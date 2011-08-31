package com.gmail.dpierron.calibre.opds.indexer;

import com.gmail.dpierron.calibre.configuration.ConfigurationManager;
import com.gmail.dpierron.calibre.datamodel.Book;
import com.gmail.dpierron.calibre.opds.CatalogContext;

import java.io.File;
import java.io.IOException;

/**
 * all the algorithms needed to index the model objects are stored here
 */
public enum IndexManager {
  INSTANCE;

  Index index = new Index();

  private IndexManager() {
  }

  public void indexBook(Book book, String url, String coverUrl) {
    index.indexBook(book, url, coverUrl);
  }

  public long size() {
    return index.size();
  }

  private void exportToJavascript(Index pIndex, String name) throws IOException {
  }

  public void exportToJavascript() throws IOException {
    Index index2 = index.filterIndex(ConfigurationManager.INSTANCE.getCurrentProfile().getMaxKeywords(), ConfigurationManager.INSTANCE.getCurrentProfile().getIndexFilterAlgorithm());
    File searchFolder = new File(CatalogContext.INSTANCE.getCatalogManager().getCatalogFolder(), "_search");
    searchFolder.mkdirs();
    index2.exportToJavascript(searchFolder);
  }

  public void exportToJSON() throws IOException {
    Index index2 = index.filterIndex(ConfigurationManager.INSTANCE.getCurrentProfile().getMaxKeywords(), ConfigurationManager.INSTANCE.getCurrentProfile().getIndexFilterAlgorithm());
    File searchFolder = new File(CatalogContext.INSTANCE.getCatalogManager().getCatalogFolder(), "_search");
    searchFolder.mkdirs();
    index2.exportToJSON(searchFolder);
  }

  public void exportToJavascriptArrays() throws IOException {
    Index index2 = index.filterIndex(ConfigurationManager.INSTANCE.getCurrentProfile().getMaxKeywords(), ConfigurationManager.INSTANCE.getCurrentProfile().getIndexFilterAlgorithm());
    File searchFolder = new File(CatalogContext.INSTANCE.getCatalogManager().getCatalogFolder(), "_search");
    searchFolder = new File(searchFolder, "database");
    searchFolder.mkdirs();
    index2.exportToJavascriptArrays(searchFolder);
  }
}