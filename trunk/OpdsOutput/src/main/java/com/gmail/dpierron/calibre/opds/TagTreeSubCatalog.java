package com.gmail.dpierron.calibre.opds;

import com.gmail.dpierron.calibre.configuration.ConfigurationManager;
import com.gmail.dpierron.calibre.configuration.StanzaConstants;
import com.gmail.dpierron.calibre.datamodel.Book;
import com.gmail.dpierron.calibre.datamodel.Tag;
import com.gmail.dpierron.calibre.opds.i18n.Localization;
import com.gmail.dpierron.calibre.opds.secure.SecureFileManager;
import com.gmail.dpierron.calibre.trook.TrookSpecificSearchDatabaseManager;
import com.gmail.dpierron.tools.Helper;
import com.gmail.dpierron.tools.RootTreeNode;
import com.gmail.dpierron.tools.TreeNode;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class TagTreeSubCatalog extends TagSubCatalog {
  private final static Logger logger = Logger.getLogger(TagTreeSubCatalog.class);

  public TagTreeSubCatalog(List<Object> stuffToFilterOut, List<Book> books) {
    super(stuffToFilterOut, books);
  }

  public TagTreeSubCatalog(List<Book> books) {
    super(books);
  }

  private Element getLevelOfTreeNode(Breadcrumbs pBreadcrumbs, TreeNode level, int from) throws IOException {
    int pageNumber = Summarizer.INSTANCE.getPageNumber(from+1);
    int itemsCount = level.getChildren().size();

    String filename = pBreadcrumbs.getFilename()+"_tags" +level.getGuid();
    if (from > 0) {
      filename = filename + "_" + pageNumber;
    }
    filename = filename + ".xml";
    logger.debug("getLevelOfTreeNode,int: generating "+filename);
    filename = SecureFileManager.INSTANCE.encode(filename);
    
    boolean onRoot = (level.isRoot());
    
    String title = (onRoot ? Localization.Main.getText("tags.title") : level.getId());
    String urn = "calibre:tags:"+level.getGuid();
    
    String summary = "";
    if (onRoot) {
      List<Tag> tags = getTags();
      if (tags.size() > 1)
        summary = Localization.Main.getText("tags.categorized", tags.size());
      else if (tags.size() == 1)
        summary = Localization.Main.getText("tags.categorized.single");
    } else {
      // try and list the items to make the summary
      summary = Summarizer.INSTANCE.summarizeTagLevels(level.getChildren());
    }
    int maxPages = Summarizer.INSTANCE.getPageNumber(itemsCount);
    
    File outputFile = getCatalogManager().storeCatalogFileInSubfolder(filename);
    FileOutputStream fos = null;
    Document document = new Document();
    try {
      fos = new FileOutputStream(outputFile);
  
      Element feed = FeedHelper.INSTANCE.getFeed(pBreadcrumbs, title, urn, summary);
      
      for (int i=from; i<itemsCount; i++) {
        if ((i-from) >= ConfigurationManager.INSTANCE.getCurrentProfile().getMaxBeforePaginate()) {
          Element nextLink = getLevelOfTreeNode(pBreadcrumbs, level, i);
          feed.addContent(nextLink);
          break;
        } else {
          TreeNode childLevel = level.getChildren().get(i);
          Breadcrumbs breadcrumbs = Breadcrumbs.addBreadcrumb(pBreadcrumbs, title, getCatalogManager().getCatalogFileUrlInItsSubfolder(filename));
          Element entry = getLevelOfTreeNode(breadcrumbs, childLevel);
          if (entry != null) 
            feed.addContent(entry);
        }
      }
  
      // write the element to the file
      document.addContent(feed);
      JDOM.INSTANCE.getOutputter().output(document, fos);
    } finally {
      if (fos != null)
        fos.close();
    }
    
    // create the same file as html
    getHtmlManager().generateHtmlFromXml(document, outputFile);
    
    Element entry;
    if (from > 0) {
      String titleNext;
      if (pageNumber != maxPages)
        titleNext = Localization.Main.getText("title.nextpage", pageNumber, maxPages);
      else
        titleNext = Localization.Main.getText("title.lastpage");
      
      entry = FeedHelper.INSTANCE.getNext(getCatalogManager().getCatalogFileUrlInItsSubfolder(filename), titleNext);
    }
    else
    {
      if (logger.isTraceEnabled())
        logger.trace("getLevelOfTreeNode:  Breadcrumbs=" + pBreadcrumbs.toString());
      boolean weAreAlsoInSubFolder = pBreadcrumbs.size()>1;

      entry = FeedHelper.INSTANCE.getEntry(title,
                                           urn,
                                           getCatalogManager().getCatalogFileUrlInItsSubfolder(filename,
                                           pBreadcrumbs.size()>1),
                                           summary,
                                               ConfigurationManager.INSTANCE.getCurrentProfile().getExternalIcons()
                                                    ? getCatalogManager().getPathToCatalogRoot(filename,weAreAlsoInSubFolder) + StanzaConstants.ICONFILE_TAGS
                                                    : StanzaConstants.ICON_TAGS);
    }
    return entry;
  }
  
  private Element getLevelOfTreeNode(Breadcrumbs pBreadcrumbs, TreeNode level) throws IOException {
    logger.debug("getLevelOfTreeNode:"+level);
    if (Helper.isNullOrEmpty(level.getChildren())) {
      // it's a leaf, consisting of a single tag : make a list of books
      logger.debug("it's a leaf, consisting of a single tag : make a list of books");
      Tag tag = (Tag)level.getData();
      String urn = "calibre:tags";
      Element entry = getTag(pBreadcrumbs, tag, urn, level.getId());
      TrookSpecificSearchDatabaseManager.INSTANCE.addTag(tag, entry);
      return entry; 
    } else {
      logger.debug("calling getLevelOfTreeNode,int");
      return getLevelOfTreeNode(pBreadcrumbs, level, 0);
    }
  }

  @Override
  Element _getEntry(Breadcrumbs pBreadcrumbs) throws IOException {
    TreeNode root = getTreeOfTags(getTags());
    logger.debug("_getEntry:"+pBreadcrumbs.toString());
    return getLevelOfTreeNode(pBreadcrumbs, root);
  }

  private TreeNode getTreeOfTags(List<Tag> tags) {
    // compute the tree
    logger.debug("compute the tree of tags");
    TreeNode root = new RootTreeNode();
    for (Tag tag : tags) {
      String[] partsOfTag = tag.getPartsOfTag(ConfigurationManager.INSTANCE.getCurrentProfile().getSplitTagsOn());
      TreeNode currentPositionInTree = root;
      for (int i = 0; i < partsOfTag.length; i++) {
        String partOfTag = partsOfTag[i];
        TreeNode nextPositionInTree = currentPositionInTree.getChildWithId(partOfTag);
        if (nextPositionInTree == null) {
          nextPositionInTree = new TreeNode(partOfTag);
          currentPositionInTree.addChild(nextPositionInTree);
          currentPositionInTree = nextPositionInTree;
        } else 
          currentPositionInTree = nextPositionInTree;
      }
      currentPositionInTree.setData(tag);
    }
    // browse the tree, removing unneeded levels (single childs up to the leafs)
    logger.debug("remove unneeded levels");
    removeUnNeededLevelsInTree(root, null);
    
    return root;
  }

  private TreeNode removeUnNeededLevelsInTree(TreeNode node, TreeNode removedParent) {
    if (removedParent != null)
      node.setId(removedParent.getId() + ConfigurationManager.INSTANCE.getCurrentProfile().getSplitTagsOn() + node.getId());
    
    if (node.getData() != null) {
      // this is a leaf
      return node;
    }

    List<TreeNode> newChildren = new Vector<TreeNode>(node.getChildren().size());
    for (TreeNode childNode : node.getChildren()) {
      if (childNode.getData() == null && childNode.getChildren().size() <= 1) {
        if (childNode.getChildren().size() == 0) {
          // useless node
        } else {
          // useless level
          TreeNode newChild = removeUnNeededLevelsInTree(childNode.getChildren().get(0), childNode);
          if (newChild != null) {
            newChild.setParent(node);
            newChildren.add(newChild);
          }
        }
      } else {
        newChildren.add(removeUnNeededLevelsInTree(childNode, null));
      }
    }
    node.setChildren(newChildren);
    return node;
  }

}