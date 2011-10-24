package com.gmail.dpierron.calibre.datamodel;

import com.gmail.dpierron.calibre.configuration.Configuration;
import com.gmail.dpierron.tools.Helper;
import org.apache.log4j.Logger;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Book implements SplitableByLetter {
  private final static DateFormat TIMESTAMP_INTITLE_FORMAT = new SimpleDateFormat("dd/MM");
  private final static Logger logger = Logger.getLogger(Book.class);

  private File bookFolder;
  private final String id;
  private final String uuid;
  private String title;
  private String titleForSort;
  private String titleWithSerieNumber;
  private String titleWithTimestamp;
  private String titleWithRating;
  private final String path;
  private String comment;
  private String shortComment;
  private final Float serieIndex;
  private final Date timestamp;
  private final Date publicationDate;
  private final String isbn;
  private List<Author> authors;
  private String listOfAuthors;
  private String authorSort;
  private Publisher publisher;
  private Series series;
  private List<Tag> tags;
  private List<EBookFile> files;
  private boolean filesSorted = false;
  private EBookFile preferredFile;
  private EBookFile epubFile;
  private boolean epubFileComputed = false;
  private boolean preferredFileComputed = false;
  private String epubFileName;
  private long latestFileModifiedDate = -1;
  private final BookRating rating;
  private List<Language> bookLanguages = new LinkedList<Language>();
  private boolean flag;

  private static Date ZERO;
  static {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(0);
    ZERO = c.getTime();
  }

  public Book(String id,
      String uuid,
      String title,
      String path,
      Float serieIndex,
      Date timestamp,
      Date publicationDate,
      String isbn,
      String authorSort,
      BookRating rating) {
    super();
    this.id = id;
    this.uuid = uuid;
    setTitle(title);
    this.path = path;
    this.serieIndex = serieIndex;
    this.timestamp = timestamp;
    this.publicationDate = publicationDate;
    this.tags = new LinkedList<Tag>();
    this.files = new LinkedList<EBookFile>();
    this.authors = new LinkedList<Author>();
    this.isbn = isbn;
    this.authorSort = authorSort;
    this.rating = rating;
  }

  public String getId() {
    return id;
  }

  public String getUuid() {
    return uuid;
  }

  private void setTitle(String value) {
    titleForSort = null;
    titleWithRating = null;
    titleWithSerieNumber = null;
    titleWithTimestamp = null;
    title = value;
    // clean up
    if (Helper.isNotNullOrEmpty(title)) {
      title = title.trim();
      title = title.substring(0, 1).toUpperCase() + title.substring(1);
    }
  }

  public String getTitle() {
    return title;
  }

  public String getTitleForSort() {
    if (titleForSort == null)
      titleForSort = NoiseWord.fromLanguage(getBookLanguage()).removeLeadingNoiseWords(getTitle());
    return titleForSort;
  }

  public Language getBookLanguage() {
    List<Language> languages = getBookLanguages();
    if ((languages == null) || (languages.size() == 0))
      return null;
    else
      return languages.get(0);
  }

  public List<Language> getBookLanguages() {
    return bookLanguages;
  }

  public void addBookLanguage(Language bookLanguage) {
    if (bookLanguage == null)
      return;
    if (bookLanguages == null)
      bookLanguages = new LinkedList<Language>();
    if (!bookLanguages.contains(bookLanguage))
      bookLanguages.add(bookLanguage);
  }

  public String getIsbn() {
    return (isbn == null ? "" : isbn);
  }

  public String getTitleWithSerieNumber() {
    if (titleWithSerieNumber == null) {
      DecimalFormat df = new DecimalFormat("####.##");
      titleWithSerieNumber = df.format(getSerieIndex()) + " - " + title;
    }
    return titleWithSerieNumber;
  }

  public String getTitleWithTimestamp() {
    if (titleWithTimestamp == null) {
      if (getTimestamp() != null)
        titleWithTimestamp = TIMESTAMP_INTITLE_FORMAT.format(getTimestamp()) + " - " + title;
      else
        titleWithTimestamp = title;
    }
    return titleWithTimestamp;
  }

  public String getTitleWithRating(String message, String ratingText) {
    if (titleWithRating == null) {
      if (getRating() != BookRating.NOTRATED)
        titleWithRating = MessageFormat.format(message, getTitle(), ratingText);
      else
        titleWithRating = title;
    }
    return titleWithRating;
  }


  public String getPath() {
    return path;
  }

  public String getComment() {
    return comment;
  }

  /**
   * Sets the comment value
   * If it starts with 'SUMMARY' then this is removed as superfluous
   * TODO  Enhance this by looking beyond starting HTML tag?
   * @param value the new comment
   */
  public void setComment(String value) {
    shortComment = null;
    if (value != null) {
      // Special Processing - remove SUMMARY from start of comment field (if present)
      if (value.toUpperCase(Locale.ENGLISH).startsWith("SUMMARY:"))
        comment = value.substring(8);
      else if (value.toUpperCase(Locale.ENGLISH).startsWith("SUMMARY"))
        comment = value.substring(6);
      else
        comment = value;
      // logger.info("Book " + id + ", Comment: " + Database.INSTANCE.stringToHex(comment));
    } else
      comment = null;
  }

  public String getShortComment(int maxLength) {
    if (shortComment == null) {
      String noHtml = Helper.removeHtmlElements(getComment());
      shortComment = Helper.shorten(noHtml, maxLength);
    }
    return shortComment;
  }

  public Float getSerieIndex() {
    return serieIndex;
  }

  public Date getTimestamp() {
    // ITIMPI:  Return 'now' if timestamp not set - would 0 be better?
    if (timestamp == null) {
      logger.warn("Date Added not set for book '" + title + "'");
      return new Date();
    }
    return timestamp;
  }

  public Date getPublicationDate() {
    if (publicationDate == null) {
      logger.warn("Publication Date not set for book '" + title + "'");
      return ZERO;
    }
    return (publicationDate);
  }

  public boolean hasAuthor() {
    return Helper.isNotNullOrEmpty(getAuthors());
  }

  public boolean hasSingleAuthor() {
    return (Helper.isNotNullOrEmpty(getAuthors()) && getAuthors().size() == 1);
  }

  public List<Author> getAuthors() {
    return authors;
  }

  public String getListOfAuthors() {
    if (listOfAuthors == null)
      listOfAuthors = Helper.concatenateList(" & ", getAuthors(), "getName");
    return listOfAuthors;
  }

  public Author getMainAuthor() {
    if (getAuthors() == null || getAuthors().size() == 0)
      return null;
    return getAuthors().get(0);
  }

  public String getAuthorSort() {
    return authorSort;
  }

  public Publisher getPublisher() {
    return publisher;
  }

  public void setPublisher(Publisher publisher) {
    this.publisher = publisher;
  }

  public Series getSeries() {
    return series;
  }

  public void setSeries(Series value) {
    this.series = value;
  }

  public List<Tag> getTags() {
    return tags;
  }

  public List<EBookFile> getFiles() {
    if (!filesSorted) {
      if (files != null && files.size() > 1) {
        Collections.sort(files, new Comparator<EBookFile>() {
          public int compare(EBookFile o1, EBookFile o2) {
            if (o1 == null)
              if (o2 == null)
                return 0;
              else
                return 1;
            if (o2 == null)
              if (o1 == null)
                return 0;
              else
                return -1;
            return Helper.checkedCompare(o1.getFormat(), o2.getFormat());
          }
        });
      }
      filesSorted = true;
    }
    return files;
  }

  public void removeFile(EBookFile file) {
    files.remove(file);
    epubFileComputed = false;
    preferredFileComputed = false;
    epubFile = null;
    preferredFile = null;
    latestFileModifiedDate = -1;
    filesSorted = false;
  }

  public void addFile(EBookFile file) {
    files.add(file);
    epubFileComputed = false;
    preferredFileComputed = false;
    epubFile = null;
    preferredFile = null;
    latestFileModifiedDate = -1;
    filesSorted = false;
  }

  public EBookFile getPreferredFile() {
    if (!preferredFileComputed) {
      for (EBookFile file : getFiles()) {
        if (preferredFile == null || file.getFormat().getPriority() > preferredFile.getFormat().getPriority())
          preferredFile = file;
      }
      preferredFileComputed = true;
    }
    return preferredFile;
  }

  public void addAuthor(Author author) {
    listOfAuthors = null;
    if (authors == null)
      authors = new LinkedList<Author>();
    if (!authors.contains(author))
      authors.add(author);
  }

  public String toString() {
    return getId() + " - " + getTitle();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null)
      return false;
    if (obj instanceof Book) {
      return (Helper.checkedCompare(((Book) obj).getId(), getId()) == 0);
    } else
      return super.equals(obj);
  }

  public String toDetailedString() {
    return getId() + " - " + getMainAuthor().getName() + " - " + getTitle() + " - " + Helper.concatenateList(getTags()) + " - " + getPath();
  }

  public File getBookFolder() {
    if (bookFolder == null) {
      File calibreLibraryFolder = Configuration.instance().getDatabaseFolder();
      bookFolder = new File(calibreLibraryFolder, getPath());
    }
    return bookFolder;
  }

  public String getEpubFilename() {
    if (!epubFileComputed) {
      getEpubFile();
    }
    return epubFileName;
  }

  public EBookFile getEpubFile() {
    if (!epubFileComputed) {
      epubFile = null;
      epubFileName = null;
      for (EBookFile file : getFiles()) {
        if (file.getFormat() == EBookFormat.EPUB) {
          epubFile = file;
          epubFileName = epubFile.getName() + epubFile.getExtension();
        }
      }
      epubFileComputed = true;
    }
    return epubFile;
  }

  public boolean doesEpubFileExist() {
    EBookFile file = getEpubFile();
    if (file == null)
      return false;
    File f = file.getFile();
    return (f != null && f.exists());
  }

  public long getLatestFileModifiedDate() {
    if (latestFileModifiedDate == -1) {
      latestFileModifiedDate = 0;
      for (EBookFile file : getFiles()) {
        File f = file.getFile();
        if (f.exists()) {
          long m = f.lastModified();
          if (m > latestFileModifiedDate)
            latestFileModifiedDate = m;
        }
      }
    }
    return latestFileModifiedDate;
  }

  public BookRating getRating() {
    return rating;
  }

  public String getTitleToSplitByLetter() {
    return getTitleForSort();
  }

  public Book copy() {
    Book result = new Book(id, uuid, title, path, serieIndex, timestamp, publicationDate, isbn, authorSort, rating);
    result.setComment(this.getComment());
    result.setSeries(this.getSeries());
    result.setPublisher(this.getPublisher());

    result.files = new LinkedList<EBookFile>(this.getFiles());
    result.tags = new LinkedList<Tag>(this.getTags());
    result.authors = new LinkedList<Author>(this.getAuthors());

    return result;
  }

  public boolean isFlagged() {
    return flag;
  }

  public void setFlag() {
    flag = true;
  }

  public void clearFlag() {
    flag = false;
  }

}