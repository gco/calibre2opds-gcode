package com.gmail.dpierron.calibre.opds;

/**
 * Class for implementing the Series type sub-catalogs
 * Inherits from:
 *  -> BooksSubcatalog - methods for listing contained books.
 *     -> SubCatalog
 */

import com.gmail.dpierron.calibre.configuration.Icons;
import com.gmail.dpierron.calibre.configuration.ConfigurationManager;
import com.gmail.dpierron.calibre.datamodel.*;
import com.gmail.dpierron.calibre.opds.i18n.Localization;
import com.gmail.dpierron.calibre.trook.TrookSpecificSearchDatabaseManager;
import com.gmail.dpierron.tools.Composite;
import com.gmail.dpierron.tools.Helper;

import org.apache.log4j.Logger;

import org.jdom.Element;

import java.io.IOException;
import java.text.Collator;
import java.util.*;

public class SeriesSubCatalog extends BooksSubCatalog {
  private final static Logger logger = Logger.getLogger(SeriesSubCatalog.class);
  private final static Collator collator = Collator.getInstance(ConfigurationManager.INSTANCE.getLocale());
  private List<Series> series;
  private Map<Series, List<Book>> mapOfBooksBySerie;


  public SeriesSubCatalog(List<Object> stuffToFilterOut, List<Book> books) {
    super(stuffToFilterOut, books);
    setCatalogType(Constants.SERIES_TYPE);
  }

  /**
   * Construct a series object from a list of books
   *
   * @param books
   */
  public SeriesSubCatalog(List<Book> books) {
    super(books);
    setCatalogType(Constants.SERIES_TYPE);
  }

  /**
   * Get the series list for the books associated with this sub-catalog
   * If it is not populated, then do so in alphabetical order
   * (taking into account leading noise words).
   */
  public List<Series> getSeries() {
    if (series == null) {
      series = new LinkedList<Series>();
      for (Book book : getBooks()) {
        if (book.getSeries() != null && !series.contains(book.getSeries()))
          series.add(book.getSeries());
      }

      final Language bookLang = getBooks().get(0).getBookLanguage();

      // sort the series alphabetically
      Collections.sort(series, new Comparator<Series>() {

        public int compare(Series o1, Series o2) {
          String title1 = (o1 == null ? "" : NoiseWord.fromLanguage(bookLang).removeLeadingNoiseWords(o1.getName().toUpperCase()));
          String title2 = (o2 == null ? "" : NoiseWord.fromLanguage(bookLang).removeLeadingNoiseWords(o2.getName().toUpperCase()));
          return collator.compare(title1, title2);
        }
      });

    }
    return series;
  }

  /**
   * @return
   */
  private Map<Series, List<Book>> getMapOfBooksBySerie() {
    if (mapOfBooksBySerie == null) {
      mapOfBooksBySerie = new HashMap<Series, List<Book>>();
      for (Book book : getBooks()) {
        List<Book> books = mapOfBooksBySerie.get(book.getSeries());
        if (books == null) {
          books = new LinkedList<Book>();
          Series serie = book.getSeries();
          if (serie != null)
            mapOfBooksBySerie.put(serie, books);
        }
        books.add(book);
      }
    }
    return mapOfBooksBySerie;
  }

  /**
   * Get the list of series.
   *
   * This reoutine is called recursively to generate each file (page or split level)
   *
   * @param pBreadcrumbs
   * @param series                      // Series to process.  Set to null if not known to take from catalog properties
   * @param from                        // Start point - set to 0 if not known
   * @param title
   * @param summary
   * @param urn
   * @param pFilename
   * @param splitOption
   * @param addTheSeriesWordToTheTitle
   * @return
   * @throws IOException
   */

  public List<Element> getContentOfListOfSeries(Breadcrumbs pBreadcrumbs,
      List<Series> series,        //  The list of series to be processed.
      boolean inSubDir,
      int from,                   // Start point - set to 0 if not known
      String title,
      String summary,
      String urn,
      String pFilename,
      SplitOption splitOption,
      boolean addTheSeriesWordToTheTitle) throws IOException {

    Map<String, List<Series>> mapOfSeriesByLetter = null;
    List<Element> result;

    if (series == null) series = getSeries();

    if (logger.isTraceEnabled())
      logger.trace("getContentOfListOfSeries: title=" + title);
    boolean willSplitByLetter;

    if (null == splitOption)
      splitOption = SplitOption.SplitByLetter;
    switch (splitOption) {
      // case DontSplit:
      case Paginate:
      case DontSplit:
        if (logger.isTraceEnabled())
          logger.trace("splitOption=" + splitOption);
        willSplitByLetter = false;
        break;

      default:
        if (logger.isTraceEnabled())
          logger.trace("getContentOfListOfSeries: splitOption=" + splitOption + ", series.size()=" + series.size() + ", MaxBeforeSplit==" +
              maxBeforeSplit);
        willSplitByLetter = (maxSplitLevels != 0) &&  (series.size() > maxBeforeSplit);
        break;
    }
    if (logger.isTraceEnabled())
      logger.trace("getContentOfListOfSeries:  willSplitByLetter=" + willSplitByLetter);

    if (willSplitByLetter) {
      mapOfSeriesByLetter = DataModel.splitSeriesByLetter(series);
    }

    if (from > 0) inSubDir = true;
    int pageNumber = Summarizer.INSTANCE.getPageNumber(from + 1);
    String filename = pFilename + Constants.PAGE_DELIM + Integer.toString(pageNumber);

    // list the entries (or split them)

    if (willSplitByLetter) {
      // split the series list by letter
      Breadcrumbs breadcrumbs = Breadcrumbs.addBreadcrumb(pBreadcrumbs, title, catalogManager.getCatalogFileUrl(filename + Constants.XML_EXTENSION, inSubDir));
      result = getListOfSeriesSplitByLetter(breadcrumbs, mapOfSeriesByLetter, inSubDir, title, urn, pFilename, addTheSeriesWordToTheTitle);
    } else {
      // list the series list
      result = new LinkedList<Element>();
      for (int i = from; i < series.size(); i++) {
        if ((splitOption != SplitOption.DontSplitNorPaginate) && ((i - from) >= maxBeforePaginate)) {
          Element nextLink =
              getListOfSeries(pBreadcrumbs, series, inSubDir, i, title, summary, urn, pFilename,
                              splitOption != SplitOption.DontSplitNorPaginate ? SplitOption.Paginate : splitOption,
                              addTheSeriesWordToTheTitle).getFirstElement();
          result.add(0, nextLink);
          break;
        } else {
          Series serie = series.get(i);
          Breadcrumbs breadcrumbs = Breadcrumbs.addBreadcrumb(pBreadcrumbs, title, catalogManager.getCatalogFileUrl(filename + Constants.XML_EXTENSION, inSubDir));
          Element entry = getSerie(breadcrumbs, serie, urn, addTheSeriesWordToTheTitle);
          if (entry != null) {
            result.add(entry);
            TrookSpecificSearchDatabaseManager.INSTANCE.addSeries(serie, entry);
          }
        }
      }
    }

    return result;
  }

  /**
   * @param pBreadcrumbs
   * @param series
   * @param inSubDir
   * @param from
   * @param title
   * @param summary
   * @param urn
   * @param pFilename
   * @param splitOption
   * @param addTheSeriesWordToTheTitle
   * @return
   * @throws IOException
   */
  public Composite<Element, String> getListOfSeries(
      Breadcrumbs pBreadcrumbs,
      List<Series> series,
      boolean inSubDir,
      int from,
      String title,
      String summary,
      String urn,
      String pFilename,
      SplitOption splitOption,
      boolean addTheSeriesWordToTheTitle) throws IOException {

    int catalogSize;
    boolean willSplitByLetter = checkSplitByLetter(splitOption, series.size());
    if (willSplitByLetter) {
      catalogSize = 0;
    } else
      catalogSize = series.size();

    if (from > 0) inSubDir = true;
    int pageNumber = Summarizer.INSTANCE.getPageNumber(from + 1);
    int maxPages = Summarizer.INSTANCE.getPageNumber(catalogSize);

    String filename = pFilename + Constants.PAGE_DELIM + Integer.toString(pageNumber);
    String urlExt = optimizeCatalogURL(catalogManager.getCatalogFileUrl(filename + Constants.XML_EXTENSION, inSubDir));
    Element feed = FeedHelper.INSTANCE.getFeedRootElement(pBreadcrumbs, title, urn, urlExt);

    // list the entries (or split them)
    List<Element> result = getContentOfListOfSeries(pBreadcrumbs, series, inSubDir, from, title, summary, urn, pFilename, splitOption, addTheSeriesWordToTheTitle);

    // add the entries to the feed
    feed.addContent(result);

    // Write to files
    createFilesFromElement(feed, filename, HtmlManager.FeedType.Catalog);

    Element entry;
    String urlInItsSubfolder = optimizeCatalogURL(catalogManager.getCatalogFileUrl(filename + Constants.XML_EXTENSION, inSubDir));
    if (from > 0) {
      String titleNext;
      if (pageNumber != maxPages)
        titleNext = Localization.Main.getText("title.nextpage", pageNumber, maxPages);
      else
        titleNext = Localization.Main.getText("title.lastpage");

      entry = FeedHelper.INSTANCE.getNextLink(urlInItsSubfolder, titleNext);
    } else {
      entry = FeedHelper.INSTANCE.getCatalogEntry(title, urn, urlInItsSubfolder, summary,
          // #751211: Use external icons option
          useExternalIcons ? getIconPrefix(inSubDir) + Icons.ICONFILE_SERIES : Icons.ICON_SERIES);
    }
    return new Composite<Element, String>(entry, urlInItsSubfolder);
  }

  /**
   * @param pBreadcrumbs
   * @param mapOfSeriesByLetter
   * @param baseTitle
   * @param baseUrn
   * @param baseFilename
   * @param addTheSeriesWordToTheTitle
   * @return
   * @throws IOException
   */
  private List<Element> getListOfSeriesSplitByLetter(
      Breadcrumbs pBreadcrumbs,
      Map<String, List<Series>> mapOfSeriesByLetter,
      boolean inSubDir,
      String baseTitle,
      String baseUrn,
      String baseFilename,
      boolean addTheSeriesWordToTheTitle) throws IOException {
    if (Helper.isNullOrEmpty(mapOfSeriesByLetter))
      return null;

    String sTitle = baseTitle;
    if (Helper.isNotNullOrEmpty(sTitle))
      sTitle = sTitle + ", ";

    List<Element> result = new LinkedList<Element>();
    SortedSet<String> letters = new TreeSet<String>(mapOfSeriesByLetter.keySet());
    for (String letter : letters) {
      // generate the letter file
      String letterFilename = Helper.getSplitString(baseFilename,letter, Constants.TYPE_SEPARATOR);

      String letterUrn = Helper.getSplitString(baseUrn, letter, Constants.URN_SEPARATOR);
      List<Series> seriesInThisLetter = mapOfSeriesByLetter.get(letter);
      String letterTitle;
      int itemsCount = seriesInThisLetter.size();
      if (letter.equals("_"))
        letterTitle = Localization.Main.getText("splitByLetter.series.other");
      else
        letterTitle = Localization.Main.getText("splitByLetter.letter", Localization.Main.getText("seriesword.title"),
                                                letter.length() > 1 ? letter.substring(0,1) + letter.substring(1).toLowerCase() : letter);
      Element element = null;
      if (itemsCount > 0) {
        int maxBeforeSplit=0;
        // try and list the items to make the summary
        String summary = Summarizer.INSTANCE.summarizeSeries(seriesInThisLetter);

        element = getListOfSeries(pBreadcrumbs, seriesInThisLetter, inSubDir, 0, letterTitle, summary, letterUrn, letterFilename,
            letter.length() < maxSplitLevels ? SplitOption.SplitByLetter : SplitOption.Paginate,
            addTheSeriesWordToTheTitle).getFirstElement();
      }

      if (element != null)
        result.add(element);
    }
    return result;
  }

  /**
   * List the books that belong to the given series
   *
   * @param pBreadcrumbs
   * @param serie
   * @param baseurn
   * @param addTheSeriesWordToTheTitle
   * @return
   * @throws IOException
   */
  private Element getSerie(Breadcrumbs pBreadcrumbs, Series serie, String baseurn, boolean addTheSeriesWordToTheTitle) throws IOException {
    if (logger.isDebugEnabled())
      logger.debug(pBreadcrumbs + "/" + serie);

    CatalogContext.INSTANCE.callback.showMessage(pBreadcrumbs.toString());
    if (!isInDeepLevel())
      CatalogContext.INSTANCE.callback.incStepProgressIndicatorPosition();

    List<Book> books = getMapOfBooksBySerie().get(serie);
    if (Helper.isNullOrEmpty(books))
      return null;

    // sort the books by series index
    Collections.sort(books, new Comparator<Book>() {

      public int compare(Book o1, Book o2) {
        Float index1 = (o1 == null ? Float.MIN_VALUE : o1.getSerieIndex());
        Float index2 = (o2 == null ? Float.MIN_VALUE : o2.getSerieIndex());
        return index1.compareTo(index2);
      }
    });

    String title = serie.getName();
    if (addTheSeriesWordToTheTitle)
      title = Localization.Main.getText("content.series") + " " + title;
    String urn = baseurn + Constants.URN_SEPARATOR + serie.getId();
    String filename = getCatalogBaseFolderFileName(Constants.SERIE_TYPE)
                      + Constants.TYPE_SEPARATOR + serie.getId();
    // try and list the items to make the summary
    String summary = Summarizer.INSTANCE.summarizeBooks(books);

    Element result = getCatalog(pBreadcrumbs, books, true, 0,   // Starting at 0
        title, summary, urn, filename, SplitOption.Paginate,   // Do not split on letter in Series - it does not really make sense
        useExternalIcons      // #751211: Use external icons option
            ? getIconPrefix(true) + Icons.ICONFILE_SERIES : Icons.ICON_SERIES, null, Option.INCLUDE_SERIE_NUMBER).getFirstElement();
    return result;
  }

  /**
   * @param pBreadcrumbs
   * @return
   * @throws IOException
   */
  public Composite<Element, String> getSeriesCatalog(Breadcrumbs pBreadcrumbs, boolean inSubDir) throws IOException {
    if (Helper.isNullOrEmpty(getSeries()))
      return null;

    String filename = getCatalogBaseFolderFileName();
    String title = Localization.Main.getText("series.title");
    String urn = "calibre:series";

    String summary = "";
    if (getSeries().size() > 1)
      summary = Localization.Main.getText("series.alphabetical", series.size());
    else if (getSeries().size() == 1)
      summary = Localization.Main.getText("series.alphabetical.single");

    return getListOfSeries(pBreadcrumbs, getSeries(), inSubDir, 0,           // Start at 0
                            title, summary, urn, filename, null,        // SplitOption
                            false);
  }

}
