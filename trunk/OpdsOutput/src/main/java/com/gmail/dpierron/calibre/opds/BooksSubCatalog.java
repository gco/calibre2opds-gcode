package com.gmail.dpierron.calibre.opds;

/**
 * Abstract class that provides the facilities for listing the books in a catalog
 * The type specific catalogs will extend this class to inherit its functionality
 * Inherits from:
 *   -> SubCatalog

 */

import com.gmail.dpierron.calibre.cache.CachedFile;
import com.gmail.dpierron.calibre.cache.CachedFileManager;
import com.gmail.dpierron.calibre.configuration.ConfigurationManager;
import com.gmail.dpierron.calibre.datamodel.*;
import com.gmail.dpierron.calibre.opds.i18n.Localization;
import com.gmail.dpierron.calibre.opds.i18n.LocalizationHelper;
import com.gmail.dpierron.calibre.opds.indexer.IndexManager;
import com.gmail.dpierron.calibre.opds.secure.SecureFileManager;
import com.gmail.dpierron.calibre.trook.TrookSpecificSearchDatabaseManager;
import com.gmail.dpierron.tools.Composite;
import com.gmail.dpierron.tools.Helper;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class BooksSubCatalog extends SubCatalog {
  private final static DateFormat PUBLICATIONDATE_FORMAT =
      ConfigurationManager.INSTANCE.getCurrentProfile().getPublishedDateAsYear() ? new SimpleDateFormat("yyyy") : new SimpleDateFormat("dd/MM/yyyy");
  private final static Logger logger = Logger.getLogger(BooksSubCatalog.class);

  /**
   * @return
   */
  public boolean isBookTheStepUnit() {
    return false;
  }

  /**
   * Create a filtered books sub-catalog item
   *
   * @param stuffToFilterOut
   * @param books
   */
  public BooksSubCatalog(List<Object> stuffToFilterOut, List<Book> books) {
    super(stuffToFilterOut, books);
  }

  /**
   * Create an un-filtered books sub-catalog item
   *
   * @param books
   */
  public BooksSubCatalog(List<Book> books) {
    super(books);
  }

  /**
   * Sort the list of books alphabetically
   *
   * @param books
   */
  void sortBooksByTitle(List<Book> books) {
    Collections.sort(books, new Comparator<Book>() {

      public int compare(Book o1, Book o2) {
        String title1 = (o1 == null ? "" : o1.getTitleForSort());
        String title2 = (o2 == null ? "" : o2.getTitleForSort());
        return title1.compareTo(title2);
      }
    });
  }

  /**
   * Sort the list of books sorted with the following key sequence:
   * - Series (alphabetically)
   * - Series-Index (numerically ascending)
   * - Book Title (Alphabetically for case where series-Index is the same)
   *
   * @param books
   */
  void sortBooksBySeries(List<Book> books) {
    Collections.sort(books, new Comparator<Book>() {

      public int compare(Book o1, Book o2) {
        if (o1 == null) {
          if (o2 == null)
            return 0;
          else
            return 1;
        }

        if (o2 == null) {
          if (o1 == null)
            return 0;
          else
            return -1;
        }

        Series series1 = o1.getSeries();
        Series series2 = o2.getSeries();

        if (series1 == null && series2 == null) {
          // both series are null, we need to compare the book titles (as always...)

          String title1 = (o1 == null ? "" : o1.getTitleForSort());
          String title2 = (o2 == null ? "" : o2.getTitleForSort());
          return title1.compareTo(title2);

        }

        if (series1 == null) {
          // ITIMPI:  Surely we have already tested for both being null?
          if (series2 == null)
            return 0;
          else
            return 1;
        }

        if (series2 == null) {
          // ITIMPI:  Surely we have already tested for both being null?
          if (series1 == null)
            return 0;
          else
            return -1;
        }

        if (series1.getId().equals(series2.getId())) {
          // same series, we need to compare the index
          if (o1.getSerieIndex() == o2.getSerieIndex())
            return 0;
          else if (o1.getSerieIndex() > o2.getSerieIndex())
            return 1;
          else
            return -1;
        } else {
          // different series, we need to compare the series title
          return series1.getName().compareTo(series2.getName());
        }
      }
    });
  }

  void sortBooksByTimestamp(List<Book> books) {
    // sort the books by timestamp
    Collections.sort(books, new Comparator<Book>() {

      public int compare(Book o1, Book o2) {
        Date ts1 = (o1 == null ? new Date() : o1.getTimestamp());
        Date ts2 = (o2 == null ? new Date() : o2.getTimestamp());
        return ts2.compareTo(ts1);
      }

    });
  }

  /**
   * @param pBreadcrumbs
   * @param books
   * @param from
   * @param pTitle
   * @param summary
   * @param urn
   * @param pFilename
   * @param splitOption
   * @param icon
   * @param options
   * @return
   * @throws IOException
   */
  Composite<Element, String> getListOfBooks(Breadcrumbs pBreadcrumbs,
      List<Book> books,
      int from,
      String pTitle,
      String summary,
      String urn,
      String pFilename,
      SplitOption splitOption,
      String icon,
      Option... options) throws IOException {

    return getListOfBooks(pBreadcrumbs, books, from, pTitle, summary, urn, pFilename, splitOption, icon, null,         // No first elements
        options);
  }

  /**
   * Get a list of books starting from a specific point
   *
   * @param pBreadcrumbs
   * @param books
   * @param from
   * @param title
   * @param summary
   * @param urn
   * @param pFilename
   * @param splitOption
   * @param icon
   * @param firstElements
   * @param options
   * @return
   * @throws IOException
   */
  Composite<Element, String> getListOfBooks(Breadcrumbs pBreadcrumbs,
      List<Book> books,
      int from,
      String title,
      String summary,
      String urn,
      String pFilename,
      SplitOption splitOption,
      String icon,
      List<Element> firstElements,
      Option... options) throws IOException {
    logger.debug("getListOfBooks: START");
    int catalogSize = books.size();
    logger.debug("getListOfBooks:catalogSize=" + catalogSize);
    Map<String, List<Book>> mapOfBooksByLetter = null;
    Map<DateRange, List<Book>> mapOfBooksByDate = null;

    // Work out any split options
    // Fixes #716917 when applied to author books list
    boolean willSplitByLetter;
    boolean willSplitByDate;
    int maxBeforePaginate = ConfigurationManager.INSTANCE.getCurrentProfile().getMaxBeforePaginate();
    if (splitOption == null) {
      // ITIMPI: Null seems to be equivalent to SplitByLetter !
      //         Might be better to replace calls by explicit value?
      splitOption = SplitOption.SplitByLetter;
      logger.debug("getListOfBooks:splitOption=null.  Changed to SplitByLetter");
    }
    switch (splitOption) {
      case Paginate:
        logger.debug("getListOfBooks:splitOption=Paginate");
        willSplitByLetter = false;
        willSplitByDate = false;
        break;
      case DontSplitNorPaginate:
        logger.debug("getListOfBooks:splitOption=DontSplitNorPaginate");
        maxBeforePaginate = Integer.MAX_VALUE; // don't paginate !
        willSplitByLetter = false;
        willSplitByDate = false;
        break;
      case DontSplit:
        // Bug #716917 Do not split on letter (used in Author and Series book lists)
        logger.debug("getListOfBooks:splitOption=DontSplit");
        willSplitByLetter = false;
        willSplitByDate = false;
        break;
      case SplitByDate:
        logger.debug("getListOfBooks:splitOption=SplitByDate");
        willSplitByLetter = true;
        willSplitByDate = true;
        break;
      case SplitByLetter:
        logger.debug("getListOfBooks:splitOption=SplitByLetter");
        willSplitByLetter = true;
        willSplitByDate = false;
        break;
      default:
        // ITIMPI:  Not sure that this case can ever arise
        //          Just added as a safety check
        logger.debug("getListOfBooks:splitOption=" + splitOption);
        willSplitByLetter = false;
        willSplitByDate = false;
        break;
    }
    // See if SplitByLetter conditions actually apply?
    if (willSplitByLetter) {
      if (catalogSize <= ConfigurationManager.INSTANCE.getCurrentProfile().getMaxBeforeSplit()) {
        willSplitByLetter = false;
      } else if ((ConfigurationManager.INSTANCE.getCurrentProfile().getBrowseByCover()) &&
          (ConfigurationManager.INSTANCE.getCurrentProfile().getBrowseByCoverWithoutSplit())) {
        willSplitByLetter = false;
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("getListOfBooks:willSplitByLetter=" + willSplitByLetter);
      logger.debug("getListOfBooks:willSplitByDate=" + willSplitByDate);
      logger.debug("listing books from=" + from + ", title=" + title);
    }
    if (willSplitByDate) {
      if (logger.isDebugEnabled())
        logger.debug("splitting list of books by date");
      mapOfBooksByDate = DataModel.splitBooksByDate(books);
      catalogSize = 0;
    } else if (willSplitByLetter) {
      if (logger.isDebugEnabled())
        logger.debug("splitting list of books by letter");
      mapOfBooksByLetter = DataModel.splitBooksByLetter(books);
      catalogSize = 0;
    }

    int pageNumber = Summarizer.INSTANCE.getPageNumber(from + 1);
    int maxPages = Summarizer.INSTANCE.getPageNumber(catalogSize);

    // generate the book list file
    String filename = SecureFileManager.INSTANCE.decode(pFilename);
    if (from > 0) {
      int pos = filename.lastIndexOf(".xml");
      if (pos >= 0)
        filename = filename.substring(0, pos);
      filename = filename + "_" + pageNumber;
    }
    if (!filename.endsWith(".xml"))
      filename = filename + ".xml";
    filename = SecureFileManager.INSTANCE.encode(filename);
    File outputFile = getCatalogManager().storeCatalogFileInSubfolder(filename);
    FileOutputStream fos = null;
    Document document = new Document();
    String urlExt = getCatalogManager().getCatalogFileUrlInItsSubfolder(filename);
    try {
      if (logger.isTraceEnabled())
        logger.trace("getListOfBooks: fos=" + outputFile);
      fos = new FileOutputStream(outputFile);

      Element feed = FeedHelper.INSTANCE.getFeedRootElement(pBreadcrumbs, title, urn, urlExt);

      // list the books (or split them)
      List<Element> result;
      if (willSplitByDate) {
        Breadcrumbs breadcrumbs = Breadcrumbs.addBreadcrumb(pBreadcrumbs, title, urlExt);
        result = getListOfBooksSplitByDate(breadcrumbs, mapOfBooksByDate, title, urn, pFilename, icon, options);
      } else if (willSplitByLetter) {
        Breadcrumbs breadcrumbs = Breadcrumbs.addBreadcrumb(pBreadcrumbs, title, urlExt);
        result = getListOfBooksSplitByLetter(breadcrumbs, mapOfBooksByLetter, title, urn, pFilename, SplitOption.SplitByLetter, icon, options);
      } else {
        result = new LinkedList<Element>();
        for (int i = from; i < books.size(); i++) {
          if ((i - from) >= maxBeforePaginate) {
            if (logger.isDebugEnabled())
              logger.debug("making a nextpage link");
            Element nextLink = getListOfBooks(pBreadcrumbs, books, i, title, summary, urn, pFilename, splitOption, icon, options).getFirstElement();
            result.add(0, nextLink);
            break;
          } else {
            Book book = books.get(i);
            if (logger.isTraceEnabled())
              logger.trace("getListOfBooks: adding book to the list : " + book);
            Breadcrumbs breadcrumbs = Breadcrumbs.addBreadcrumb(pBreadcrumbs, title, urlExt);
            try {
              logger.trace("getListOfBooks: breadcrumbs=" + breadcrumbs + ", book=" + book + ", options=" + options);
              Element entry = getBookEntry(breadcrumbs, book, options);
              if (entry != null) {
                logger.trace("getListOfBooks: entry=" + entry);
                result.add(entry);
                TrookSpecificSearchDatabaseManager.INSTANCE.addBook(book, entry);
              }
            } catch (RuntimeException e) {
              logger.error("getListOfBooks: Exception on book: " + book.getTitle() + "[" + book.getId() + "]", e);
              throw e;
            }
          }
        }
      }

      // if needed, add the first elements to the feed
      if (Helper.isNotNullOrEmpty(firstElements))
        feed.addContent(firstElements);

      // add the book entries to the feed
      feed.addContent(result);

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
    String urlInItsSubfolder = getCatalogManager().getCatalogFileUrlInItsSubfolder(filename, pBreadcrumbs.size() > 1);
    if (from > 0) {
      String titleNext;
      if (pageNumber != maxPages) {titleNext = Localization.Main.getText("title.nextpage", pageNumber, maxPages);} else {
        titleNext = Localization.Main.getText("title.lastpage");
      }

      entry = FeedHelper.INSTANCE.getNextLink(urlExt, titleNext);
    } else {
      entry = FeedHelper.INSTANCE.getCatalogEntry(title, urn, urlInItsSubfolder, summary, icon);
    }

    return new Composite<Element, String>(entry, urlInItsSubfolder);
  }

  /**
   * @param pBreadcrumbs
   * @param mapOfBooksByLetter
   * @param baseTitle
   * @param baseUrn
   * @param baseFilename
   * @param splitOption
   * @param icon
   * @param options
   * @return
   * @throws IOException
   */
  private List<Element> getListOfBooksSplitByLetter(Breadcrumbs pBreadcrumbs,
      Map<String, List<Book>> mapOfBooksByLetter,
      String baseTitle,
      String baseUrn,
      String baseFilename,
      SplitOption splitOption,
      String icon,
      Option... options) throws IOException {
    if (Helper.isNullOrEmpty(mapOfBooksByLetter))
      return null;

    String sTitle = baseTitle;
    if (Helper.isNotNullOrEmpty(sTitle))
      sTitle = sTitle + ", ";

    List<Element> result = new LinkedList<Element>();
    SortedSet<String> letters = new TreeSet<String>(mapOfBooksByLetter.keySet());
    for (String letter : letters) {
      // generate the letter file
      String baseFilenameCleanedUp = SecureFileManager.INSTANCE.decode(baseFilename);
      int pos = baseFilenameCleanedUp.indexOf(".xml");
      if (pos > -1)
        baseFilenameCleanedUp = baseFilenameCleanedUp.substring(0, pos);
      String letterFilename = baseFilenameCleanedUp + "_" + Helper.convertToHex(letter) + ".xml";
      letterFilename = SecureFileManager.INSTANCE.encode(letterFilename);

      String letterUrn = baseUrn + ":" + letter;

      List<Book> booksInThisLetter = mapOfBooksByLetter.get(letter);
      String letterTitle;
      if (letter.equals("_"))
        letterTitle = Localization.Main.getText("splitByLetter.book.other");
      else
        letterTitle = Localization.Main.getText("splitByLetter.letter", Localization.Main.getText("bookword.title"), letter);

      // try and list the items to make the summary
      String summary = Summarizer.INSTANCE.summarizeBooks(booksInThisLetter);

      Element element = null;
      if (booksInThisLetter.size() > 0) {
        element = getListOfBooks(pBreadcrumbs, booksInThisLetter, 0, letterTitle, summary, letterUrn, letterFilename, SplitOption.SplitByLetter, icon, options)
            .getFirstElement();
      }

      if (element != null)
        result.add(element);
    }
    return result;
  }

  /**
   * @param pBreadcrumbs
   * @param mapOfBooksByDate
   * @param baseTitle
   * @param baseUrn
   * @param baseFilename
   * @param icon
   * @param options
   * @return
   * @throws IOException
   */
  private List<Element> getListOfBooksSplitByDate(Breadcrumbs pBreadcrumbs,
      Map<DateRange, List<Book>> mapOfBooksByDate,
      String baseTitle,
      String baseUrn,
      String baseFilename,
      String icon,
      Option... options) throws IOException {
    if (Helper.isNullOrEmpty(mapOfBooksByDate))
      return null;

    String sTitle = baseTitle;
    if (Helper.isNotNullOrEmpty(sTitle))
      sTitle = sTitle + ", ";

    List<Element> result = new LinkedList<Element>();
    SortedSet<DateRange> ranges = new TreeSet<DateRange>(mapOfBooksByDate.keySet());
    for (DateRange range : ranges) {
      // generate the range file
      String rangeFilenameCleanedUp = SecureFileManager.INSTANCE.decode(baseFilename);
      int pos = rangeFilenameCleanedUp.indexOf(".xml");
      if (pos > -1)
        rangeFilenameCleanedUp = rangeFilenameCleanedUp.substring(0, pos);
      String rangeFilename = rangeFilenameCleanedUp + "_" + range + ".xml";
      rangeFilename = SecureFileManager.INSTANCE.encode(rangeFilename);

      String rangeUrn = baseUrn + ":" + range;

      String rangeTitle = LocalizationHelper.INSTANCE.getEnumConstantHumanName(range);
      List<Book> booksInThisRange = mapOfBooksByDate.get(range);

      // try and list the items to make the summary
      String summary = Summarizer.INSTANCE.summarizeBooks(booksInThisRange);

      Element element = null;
      if (booksInThisRange.size() > 0) {
        element = getListOfBooks(pBreadcrumbs, booksInThisRange, 0, rangeTitle, summary, rangeUrn, rangeFilename, SplitOption.Paginate, icon, options)
            .getFirstElement();
      }

      if (element != null)
        result.add(element);
    }
    return result;
  }

  /**
   * @param book
   * @param entry
   */
  private void addAcquisitionLinks(Book book, Element entry) {
    if (!ConfigurationManager.INSTANCE.getCurrentProfile().getGenerateDownloads()) {
      if (logger.isTraceEnabled())
        logger.trace("addAcquisitionLinks: exit: download links suppressed");
      return;
    }

    // links to the ebook files
    if (logger.isTraceEnabled())
      logger.trace("addAcquisitionLinks: links to the ebook files");
    for (EBookFile file : book.getFiles()) {
      // prepare to copy the ebook file
      if (logger.isTraceEnabled())
        logger.trace("addAcquisitionLinks: prepare to copy the ebook file " + file.getName());
      // ITIMPI  Why is EPUB treated as a special case?
      if (file.getFormat() == EBookFormat.EPUB)
        getCatalogManager().addFileToTheMapOfFilesToCopy(file.getFile(), book);
      else
        getCatalogManager().addFileToTheMapOfFilesToCopy(file.getFile());

      entry.addContent(FeedHelper.INSTANCE.getAcquisitionLink(
          "../../" + FeedHelper.INSTANCE.urlEncode(book.getPath(), true) + "/" + FeedHelper.INSTANCE.urlEncode(file.getName() + file.getExtension(), true),
          file.getFormat().getMime(), // Mime type
          Localization.Main.getText("bookentry.download", file.getFormat())));

      // if the IncludeOnlyOneFile option is set, break to avoid publishing other files
      if (ConfigurationManager.INSTANCE.getCurrentProfile().getIncludeOnlyOneFile()) {
        if (logger.isTraceEnabled())
          logger.trace("addAcquisitionLinks: break to avoid publishing other files");
        break;
      }
    }
  }

  /**
   * @param book
   * @param entry
   */
  private void addCoverLink(Book book, Element entry) {

    if (logger.isDebugEnabled())
      logger.debug("addCoverLink for " + book.getTitle());
    CachedFile coverFile = CachedFileManager.INSTANCE.addCachedFile(book.getBookFolder(), Constants.CALIBRE_COVER_FILENAME);
    if (!coverFile.exists()) {
      if (logger.isTraceEnabled())
        logger.trace("addCoverLink: no cover in library for book: " + book.getTitle());
      return;
    }


    // add the cover

    // prepare to copy the cover file

    getCatalogManager().addFileToTheMapOfFilesToCopy(coverFile);

    // Add the Cover link

    String coverUri = getCoverManager().getImageUri(book);

    // get the generated cover filename
    CachedFile resizedCoverFile = CachedFileManager.INSTANCE.addCachedFile(book.getBookFolder(), getCoverManager().getResultFilename(book));

    // prepare to copy the thumbnail if we are using them file
    if (ConfigurationManager.INSTANCE.getCurrentProfile().getCoverResize()) {
      getCatalogManager().addFileToTheMapOfFilesToCopy(resizedCoverFile);

      if (!resizedCoverFile.exists() || getCoverManager().hasImageSizeChanged() || resizedCoverFile.lastModified() < coverFile.lastModified()) {
        if (logger.isTraceEnabled()) {
          if (!resizedCoverFile.exists())
            logger.trace("addCoverLink: resizedCover set to be generated (not already existing)");
          else if (getCoverManager().hasImageSizeChanged())
            logger.trace("addCoverLink: resizedCover set to be generated (image size changed)");
          else if (resizedCoverFile.lastModified() < coverFile.lastModified())
            logger.trace("addCoverLink: resizedCover set to be generated (new cover)");
        }
        getCoverManager().setImageToGenerate(resizedCoverFile, coverFile);
      } else {
        if (logger.isTraceEnabled())
          logger.trace("addCoverLink: resizedCover not to be generated");
      }
    } else {
      // Not using resized covers - use original cover.jpg

      if (resizedCoverFile.exists()) {
        // Safety check we never delete the Calibre cover
        if (0 == resizedCoverFile.getName().compareTo(Constants.CALIBRE_COVER_FILENAME)) {
          logger.warn("attempt to delete Calibre cover for book " + book.getTitle());
        } else {
          if (logger.isTraceEnabled())
            logger.trace("addCoverLink: coverResize=false. Delete " + resizedCoverFile.getName());
        }
        resizedCoverFile.delete();
        // Make sure it is no longer in the cache
        CachedFileManager.INSTANCE.removeCachedFile(resizedCoverFile);
      } else {
        if (logger.isTraceEnabled())
          logger.trace("addCoverLink: coverResize=false. No resizedCover file for book " + book.getTitle());
      }
      // Change URI name to user cover.jpg
      coverUri = FeedHelper.INSTANCE.urlEncode("../../" + book.getPath() + "/" + Constants.CALIBRE_COVER_FILENAME, true);
    }
    if (logger.isTraceEnabled())
      logger.trace("addCoverLink: coverUri=" + coverUri);
    entry.addContent(FeedHelper.INSTANCE.getCoverLink(coverUri));

    // add the thumbnail link

    String thumbnailUri = getThumbnailManager().getImageUri(book);
    CachedFile thumbnailFile = CachedFileManager.INSTANCE.addCachedFile(book.getBookFolder(), getThumbnailManager().getResultFilename(book));
    // Take into account whether thumbnail generation suppressed
    if (ConfigurationManager.INSTANCE.getCurrentProfile().getThumbnailGenerate()) {
      // Using generated thumbnail files

      // prepare to copy the thumbnail file
      getCatalogManager().addFileToTheMapOfFilesToCopy(thumbnailFile);

      // generate the file if does not exist or size changed
      if (!thumbnailFile.exists() || getThumbnailManager().hasImageSizeChanged() || thumbnailFile.lastModified() < coverFile.lastModified()) {
        if (logger.isTraceEnabled()) {
          if (!thumbnailFile.exists())
            logger.trace("addCoverLink: thumbnail set to be generated (not already existing)");
          else if (getThumbnailManager().hasImageSizeChanged())
            logger.trace("addCoverLink: thumbnail set to be generated (image size changed)");
          else if (thumbnailFile.lastModified() < coverFile.lastModified())
            logger.trace("addCoverLink: thumbnail set to be generated (new cover)");
        }
        getThumbnailManager().setImageToGenerate(thumbnailFile, coverFile);
      } else {
        if (logger.isTraceEnabled())
          logger.trace("addCoverLink: thumbnail not to be generated");
      }
    } else {
      // Not generating thumbnails - using existing cover.jpg
      if (thumbnailFile.exists()) {
        if (thumbnailFile.getName().compareTo("cover.jpg") == 0) {
          logger.warn("attempt to delete Calibre cover (for book " + book.getTitle());
        } else {
          if (logger.isTraceEnabled())
            logger.trace("addCoverLink: Delete existing thumbnail file " + thumbnailFile.getName());
          thumbnailFile.delete();
          // Make sure it is no longer in the cache
          CachedFileManager.INSTANCE.removeCachedFile(thumbnailFile);
        }
      }
      CachedFileManager.INSTANCE.removeCachedFile(thumbnailFile);
      // Change URI name to user cover.jpg
      thumbnailUri = FeedHelper.INSTANCE.urlEncode("../../" + book.getPath() + "/" + Constants.CALIBRE_COVER_FILENAME, true);


    }
    if (logger.isTraceEnabled())
      logger.trace("addCoverLink: thumbNailUri=" + thumbnailUri);
    entry.addContent(FeedHelper.INSTANCE.getThumbnailLink(thumbnailUri));
  }

  private void addNavigationLinks(Element entry, Book book) {
    if (ConfigurationManager.INSTANCE.getCurrentProfile().getGenerateCrossLinks()) {
      // add the series link
      if (book.getSeries() != null && DataModel.INSTANCE.getMapOfBooksBySeries().get(book.getSeries()).size() > 1) {
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the series link");
        entry.addContent(FeedHelper.INSTANCE.getRelatedLink(
            getCatalogManager().getCatalogFileUrlInItsSubfolder(SecureFileManager.INSTANCE.encode("series_" + book.getSeries().getId() + ".xml")),
            Localization.Main.getText("bookentry.series", book.getSerieIndex(), book.getSeries().getName())));
      }

      // add the author page link(s)
      if (book.hasAuthor()) {
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the author page link(s)");
        for (Author author : book.getAuthors()) {
          int nbBooks = DataModel.INSTANCE.getMapOfBooksByAuthor().get(author).size();
          entry.addContent(FeedHelper.INSTANCE
              .getRelatedLink(getCatalogManager().getCatalogFileUrlInItsSubfolder(SecureFileManager.INSTANCE.encode("author_" + author.getId() + ".xml")),
                  Localization.Main.getText("bookentry.author", Summarizer.INSTANCE.getBookWord(nbBooks), author.getName())));
        }
      }

      // add the tags links
      if (ConfigurationManager.INSTANCE.getCurrentProfile().getGenerateTags()) {
        if (Helper.isNotNullOrEmpty(book.getTags())) {
          if (logger.isTraceEnabled())
            logger.trace("getBookFullEntry: add the tags links");
          for (Tag tag : book.getTags()) {
            int nbBooks = DataModel.INSTANCE.getMapOfBooksByTag().get(tag).size();
            if (nbBooks > 1) {
              entry.addContent(FeedHelper.INSTANCE
                  .getRelatedLink(getCatalogManager().getCatalogFileUrlInItsSubfolder(SecureFileManager.INSTANCE.encode("tag_" + tag.getId() + ".xml")),
                      Localization.Main.getText("bookentry.tags", Summarizer.INSTANCE.getBookWord(nbBooks), tag.getName())));
            }
          }
        }
      }

      // add the ratings links
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: add the ratings links");
      if (ConfigurationManager.INSTANCE.getCurrentProfile().getGenerateRatings() && book.getRating() != BookRating.NOTRATED) {
        int nbBooks = DataModel.INSTANCE.getMapOfBooksByRating().get(book.getRating()).size();
        if (nbBooks > 1) {
          entry.addContent(FeedHelper.INSTANCE.getRelatedLink(
              getCatalogManager().getCatalogFileUrlInItsSubfolder(SecureFileManager.INSTANCE.encode("rated_" + book.getRating().getId() + ".xml")),
              Localization.Main.getText("bookentry.ratings", Summarizer.INSTANCE.getBookWord(nbBooks),
                  LocalizationHelper.INSTANCE.getEnumConstantHumanName(book.getRating()))));
        }
      }
    }
  }

  private void addExternalLinks(Element entry, Book book) {
    if (ConfigurationManager.INSTANCE.getCurrentProfile().getGenerateExternalLinks()) {
      String url;
      // add the GoodReads book link
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: add the GoodReads book link");
      if (Helper.isNotNullOrEmpty(book.getIsbn())) {
        url = ConfigurationManager.INSTANCE.getCurrentProfile().getGoodreadIsbnUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, book.getIsbn()), Localization.Main.getText("bookentry.goodreads")
          ));

        url = ConfigurationManager.INSTANCE.getCurrentProfile().getGoodreadReviewIsbnUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(
              FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, book.getIsbn()), Localization.Main.getText("bookentry.goodreads.review")));
      } else {
        url = ConfigurationManager.INSTANCE.getCurrentProfile().getGoodreadTitleUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(FeedHelper.INSTANCE
              .getRelatedHtmlLink(MessageFormat.format(url, FeedHelper.INSTANCE.urlEncode(book.getTitle())), Localization.Main.getText("bookentry.goodreads")
              ));
      }

      // add the Wikipedia book link
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: add the Wikipedia book link");
      url = ConfigurationManager.INSTANCE.getCurrentProfile().getWikipediaUrl();
      if (Helper.isNotNullOrEmpty(url))
        entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(
            MessageFormat.format(url, ConfigurationManager.INSTANCE.getCurrentProfile().getWikipediaLanguage(), FeedHelper.INSTANCE.urlEncode(book.getTitle()
            )),
            Localization.Main.getText("bookentry.wikipedia")));

      // Add Librarything book link
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: Add Librarything book link");
      if (Helper.isNotNullOrEmpty(book.getIsbn())) {
        url = ConfigurationManager.INSTANCE.getCurrentProfile().getLibrarythingIsbnUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(
              FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, book.getIsbn()), Localization.Main.getText("bookentry.librarything")));
      } else if (Helper.isNotNullOrEmpty(book.getTitle())) {
        url = ConfigurationManager.INSTANCE.getCurrentProfile().getLibrarythingTitleUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(
              MessageFormat.format(url, FeedHelper.INSTANCE.urlEncode(book.getTitle()), FeedHelper.INSTANCE.urlEncode(book.getMainAuthor().getName())),
              Localization.Main.getText("bookentry.librarything")));
      }

      // Add Amazon book link
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: Add Amazon book link");
      if (Helper.isNotNullOrEmpty(book.getIsbn())) {
        url = ConfigurationManager.INSTANCE.getCurrentProfile().getAmazonIsbnUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, book.getIsbn()), Localization.Main.getText("bookentry.amazon")));
      } else if (book.getMainAuthor() != null && Helper.isNotNullOrEmpty(book.getTitle())) {
        url = ConfigurationManager.INSTANCE.getCurrentProfile().getAmazonTitleUrl();
        if (Helper.isNotNullOrEmpty(url))
          entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(
              MessageFormat.format(url, FeedHelper.INSTANCE.urlEncode(book.getTitle()), FeedHelper.INSTANCE.urlEncode(book.getMainAuthor().getName())),
              Localization.Main.getText("bookentry.amazon")));
      }

      // Author Links
      if (book.hasAuthor()) {
        // add the GoodReads author link
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the GoodReads author link");
        for (Author author : book.getAuthors()) {
          url = ConfigurationManager.INSTANCE.getCurrentProfile().getGoodreadAuthorUrl();
          if (Helper.isNotNullOrEmpty(url))
            entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, FeedHelper.INSTANCE.urlEncode(author.getName())),
                Localization.Main.getText("bookentry.goodreads.author", author.getName())));
        }

        // add the Wikipedia author link
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the Wikipedia author link");
        for (Author author : book.getAuthors()) {
          url = ConfigurationManager.INSTANCE.getCurrentProfile().getWikipediaUrl();
          if (Helper.isNotNullOrEmpty(url))
            entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(ConfigurationManager.INSTANCE.getCurrentProfile().getWikipediaUrl(),
                ConfigurationManager.INSTANCE.getCurrentProfile().getWikipediaLanguage(), FeedHelper.INSTANCE.urlEncode(author.getName())),
                Localization.Main.getText("bookentry.wikipedia.author", author.getName())));
        }

        // add the LibraryThing author link
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the LibraryThing author link");
        for (Author author : book.getAuthors()) {
          url = ConfigurationManager.INSTANCE.getCurrentProfile().getLibrarythingAuthorUrl();
          if (Helper.isNotNullOrEmpty(url))
            entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(
                // LibraryThing is very peculiar on how it looks up it's authors... format is LastNameFirstName[Middle]
                MessageFormat.format(ConfigurationManager.INSTANCE.getCurrentProfile().getLibrarythingAuthorUrl(),
                    FeedHelper.INSTANCE.urlEncode(author.getSort().replace(",", "").replace(" ", ""))),
                Localization.Main.getText("bookentry.librarything.author", author.getName())));
        }

        // add the Amazon author link
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the Amazon author link");
        for (Author author : book.getAuthors()) {
          url = ConfigurationManager.INSTANCE.getCurrentProfile().getAmazonAuthorUrl();
          if (Helper.isNotNullOrEmpty(url))
            entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, FeedHelper.INSTANCE.urlEncode(author.getName())),
                Localization.Main.getText("bookentry.amazon.author", author.getName())));
        }

        // add the ISFDB author link
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: add the ISFDB author link");
        for (Author author : book.getAuthors()) {
          url = ConfigurationManager.INSTANCE.getCurrentProfile().getIsfdbAuthorUrl();
          if (Helper.isNotNullOrEmpty(url))
            entry.addContent(FeedHelper.INSTANCE.getRelatedHtmlLink(MessageFormat.format(url, FeedHelper.INSTANCE.urlEncode(author.getName())),
                Localization.Main.getText("bookentry.isfdb.author", author.getName())));
        }
      }
    }
  }

  private void decorateBookEntry(Element entry, Book book, boolean isFullEntry) {
    if (book.hasAuthor()) {
      for (Author author : book.getAuthors()) {
        Element authorElement = JDOM.INSTANCE.element("author").addContent(JDOM.INSTANCE.element("name").addContent(book.getListOfAuthors()))
            .addContent(JDOM.INSTANCE.element("uri").addContent("author_" + book.getMainAuthor().getId() + ".xml"));
        entry.addContent(authorElement);
      }
    }

    // published element
    if (logger.isTraceEnabled()) {logger.trace("getBookFullEntry: published element");}
    Element published = FeedHelper.INSTANCE.getPublishedTag(book.getPublicationDate());
    entry.addContent(published);

    // dublin core - language
    for (Language language : book.getBookLanguages()) {
      Element dcLang = FeedHelper.INSTANCE.getDublinCoreLanguageElement(language.getIso2());
      entry.addContent(dcLang);
    }

    // dublin core - publisher
    Publisher publisher = book.getPublisher();
    if (Helper.isNotNullOrEmpty(publisher)) {
      Element dcPublisher = FeedHelper.INSTANCE.getDublinCorePublisherElement(publisher.getName());
      entry.addContent(dcPublisher);
    }

    // categories
    if (Helper.isNotNullOrEmpty(book.getTags())) {
      // tags
      for (Tag tag : book.getTags()) {
        Element categoryElement = FeedHelper.INSTANCE.getCategoryElement(tag.getName());
        entry.addContent(categoryElement);
      }
      // series
      if (Helper.isNotNullOrEmpty(book.getSeries())) {
        Element categoryElement = FeedHelper.INSTANCE.getCategoryElement(book.getSeries().getName());
        entry.addContent(categoryElement);
      }
    }

    // book description
    if (isFullEntry) {
      // content element
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: content element");
      Element content = JDOM.INSTANCE.element("content").setAttribute("type", "text/html");
      boolean hasContent = false;
      if (logger.isTraceEnabled())
        logger.trace("getBookFullEntry: computing comments");
      if (Helper.isNotNullOrEmpty(book.getSeries())) {
        String data = Localization.Main.getText("content.series.data", book.getSerieIndex(), book.getSeries().getName());
        content.addContent(JDOM.INSTANCE.element("strong").addContent(Localization.Main.getText("content.series") + " ")).addContent(data)
            .addContent(JDOM.INSTANCE.element("br")).addContent(JDOM.INSTANCE.element("br"));
        hasContent = true;
      }
      List<Element> comments = JDOM.INSTANCE.convertBookCommentToXhtml(book.getComment());
      if (Helper.isNotNullOrEmpty(comments)) {
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: got comments");
        content.addContent(JDOM.INSTANCE.newParagraph().addContent(JDOM.INSTANCE.element("strong").addContent(Localization.Main.getText("content.summary"))));
        for (Element p : comments) {
          content.addContent(p.detach());
        }
        hasContent = true;
      }
      if (hasContent) {
        if (logger.isTraceEnabled())
          logger.trace("getBookFullEntry: had content");
        entry.addContent(content);
      }
    } else {
      // summary element (the shortened book comment)
      // ITIMPI:  Should this be refactored to calculate this
        //
      if (logger.isTraceEnabled())
        logger.trace("getBookEntry: short comment");
      Element summary = JDOM.INSTANCE.element("summary");
      Boolean hasContent = false;
      Integer maxLength = ConfigurationManager.INSTANCE.getCurrentProfile().getMaxBookSummaryLength();
      // Check for series info to include
      if (Helper.isNotNullOrEmpty(book.getSeries())) {
        DecimalFormat df = new DecimalFormat("####.##");

        // String seriesDetails = book.getSeries().getName() + "[" + df.format(book.getSerieIndex())+ ": ";
        String seriesDetails = Localization.Main.getText("content.series.data", book.getSerieIndex(), book.getSeries().getName()) +": ";
        seriesDetails = Helper.shorten(seriesDetails,maxLength);
        summary.addContent(JDOM.INSTANCE.element("strong").addContent(seriesDetails)).addContent(JDOM.INSTANCE.element("br"));
        maxLength -= seriesDetails.length();
        hasContent = true;
      }
      // See if still space for comment info
      if (maxLength > 0) {
        String noHtml = Helper.removeHtmlElements(book.getComment());
        // Is there actually any comment info?
        if (Helper.isNotNullOrEmpty(noHtml)) {
          summary.addContent(Helper.shorten(noHtml, maxLength));
          hasContent = true;
        }
      }
      // If we had anything for the summary then it needs to be added to the output.
      if (hasContent)
          entry.addContent(summary);
    }

    // acquisition links
    addAcquisitionLinks(book, entry);

    // cover and thumbnail links
    addCoverLink(book, entry);

    if (isFullEntry) {
      // navigation links
      addNavigationLinks(entry, book);

      // external links
      addExternalLinks(entry, book);
    }
  }

  /**
   * @param pBreadcrumbs
   * @param book
   * @param options
   * @return
   * @throws IOException
   */
  private Element getBookEntry(Breadcrumbs pBreadcrumbs, Book book, Option... options) throws IOException {
    if (logger.isDebugEnabled())
      logger.debug("getBookEntry: pBreadcrumbs=" + pBreadcrumbs + ", book=" + book);

    CatalogContext.INSTANCE.getCallback().showMessage(pBreadcrumbs.toString());
    if (!isInDeepLevel() && isBookTheStepUnit())
      CatalogContext.INSTANCE.getCallback().incStepProgressIndicatorPosition();

    String filename = "book_" + book.getId() + ".xml";
    if (logger.isDebugEnabled())
      logger.debug("getBookEntry:" + book);
    if (logger.isTraceEnabled()) {
      logger.trace("getBookEntry:" + pBreadcrumbs.toString());
      logger.trace("getBookEntry: generating " + filename);
    }

    // construct the contextual title (including the date, or the series, or the rating)
    String title;
    if (Option.contains(options, Option.INCLUDE_SERIE_NUMBER))
      if (book.getSerieIndex() != 0) {
        title = book.getTitleWithSerieNumber();
      } else {
        title = book.getTitle();
      }
    else if (Option.contains(options, Option.INCLUDE_TIMESTAMP))
      title = book.getTitleWithTimestamp();
    else if (!Option.contains(options, Option.DONOTINCLUDE_RATING) && !ConfigurationManager.INSTANCE.getCurrentProfile().getSuppressRatingsInTitles())
      title = book.getTitleWithRating(Localization.Main.getText("bookentry.rated"), LocalizationHelper.INSTANCE.getEnumConstantHumanName(book.getRating()));
    else
      title = book.getTitle();

    String urn = "calibre:book:" + book.getId();
    filename = SecureFileManager.INSTANCE.encode(filename);

    if (logger.isTraceEnabled())
      logger.trace("getBookEntry: checking book in the Catalog manager");
    File outputFile = getCatalogManager().storeCatalogFileInSubfolder(filename);
    String trueFilename = getCatalogManager().getCatalogFileUrlInItsSubfolder(filename);
    if (getCatalogManager().addBookEntryFile(outputFile)) {
      if (logger.isTraceEnabled())
        logger.trace("getBookEntry: book was not yet done");

      // generate the book full entry
      FileOutputStream fos = null;
      Document document = new Document();
      try {
        fos = new FileOutputStream(outputFile);
        Element entry = getBookFullEntry(book);

        // write the element to the file
        document.addContent(entry);
        JDOM.INSTANCE.getOutputter().output(document, fos);

      } catch (RuntimeException e) {
        // Exceptions are not expected, but if one does occur as well
        // as logging the details, also log the book that caused it
        logger.error("... " + book.getAuthors() + ": " + book.getTitle(), e);
        throw e;
        // Increment the warning count for advising the user to look at the log after the run
      } finally {
        if (fos != null)
          fos.close();
      }

      // create the same file as html
      getHtmlManager().generateHtmlFromXml(document, outputFile, HtmlManager.FeedType.BookFullEntry);

      if (ConfigurationManager.INSTANCE.getCurrentProfile().getGenerateIndex()) {
        // index the book
        logger.debug("getBookEntry: indexing book");
        IndexManager.INSTANCE.indexBook(book, getHtmlManager().getHtmlFilenameFromXmlFilename(trueFilename), getThumbnailManager().getImageUri(book));
      }
    }

    Element entry = FeedHelper.INSTANCE.getBookEntry(title, urn, book.getLatestFileModifiedDate());

    // add the required data to the book entry
    decorateBookEntry(entry, book, false);

    // add a full entry link to the partial entry
    if (logger.isTraceEnabled())
      logger.trace("add a full entry link to the partial entry");
    entry.addContent(FeedHelper.INSTANCE.getFullEntryLink(trueFilename));

    return entry;
  }

  /**
   * @param book
   * @return
   */
  private Element getBookFullEntry(Book book) {
    if (logger.isDebugEnabled())
      logger.debug("getBookFullEntry: " + book);
    Element entry = JDOM.INSTANCE.rootElement("entry", JDOM.Namespace.Atom, JDOM.Namespace.DcTerms, JDOM.Namespace.Atom, JDOM.Namespace.Xhtml);

    String sTitle = book.getTitle();
    Element title = JDOM.INSTANCE.element("title").addContent(sTitle);
    entry.addContent(title);

    Element id = JDOM.INSTANCE.element("id").addContent("urn:book:" + book.getUuid());
    entry.addContent(id);

    // updated element
    if (logger.isTraceEnabled())
      logger.trace("getBookFullEntry: updated element");
    Element updated = FeedHelper.INSTANCE.getUpdatedTag(book.getLatestFileModifiedDate());
    entry.addContent(updated);

    // add the required data to the book entry
    decorateBookEntry(entry, book, true);

    return entry;
  }

}