package com.gmail.dpierron.calibre.database;

/**
 * Abstract the basic database operations used by calibre2opds
 * Handles transfering data between database field and calibre2opds variables
 */
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.gmail.dpierron.calibre.datamodel.Author;
import com.gmail.dpierron.calibre.datamodel.Book;
import com.gmail.dpierron.calibre.datamodel.BookRating;
import com.gmail.dpierron.calibre.datamodel.DataModel;
import com.gmail.dpierron.calibre.datamodel.EBookFile;
import com.gmail.dpierron.calibre.datamodel.Publisher;
import com.gmail.dpierron.calibre.datamodel.Series;
import com.gmail.dpierron.calibre.datamodel.Tag;
import com.gmail.dpierron.tools.Helper;

public enum Database {

  INSTANCE;
  
  private static final Logger logger = Logger.getLogger(Database.class);
  private static final DateFormat SQLITE_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  
  public List<Tag> listTags() {
    List<Tag> result = new Vector<Tag>();
    PreparedStatement statement = DatabaseRequest.ALL_TAGS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        result.add(new Tag(set.getString("id"), set.getString("name")));
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public boolean test() {
    PreparedStatement statement = DatabaseRequest.TEST.getStatement();
    try {
      statement.executeQuery();
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  public List<Book> listBooks() {
    List<Book> result = new Vector<Book>();
    PreparedStatement statement = DatabaseRequest.ALL_BOOKS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      // if (logger.isTraceEnabled())
      //     logger.trace("Processing Query results");
      while (set.next()) {
        String bookId = set.getString("book_id");
        // if (logger.isTraceEnabled())
        //     logger.trace("Processing bookId " + bookId);
        String uuid = set.getString("uuid");
        Date timestamp = null;
        try {
          timestamp = SQLITE_TIMESTAMP_FORMAT.parse(set.getString("book_timestamp"));
        } catch (ParseException e) {
          // we don't care
        }
        Date publicationDate = null;
        try {
          publicationDate = SQLITE_TIMESTAMP_FORMAT.parse(set.getString("book_pubdate"));
        } catch (ParseException e) {
          // we don't care
        }
        // add a new book
        String title = set.getString("book_title");
        String path = set.getString("book_path");
        float index = set.getFloat("series_index");   // Bug 716914 Get series index correctly
        String isbn = set.getString("isbn");
        String authorSort = set.getString("author_sort");
        int iRating = set.getInt("rating");
        BookRating rating = BookRating.fromValue(iRating);
        Book book = new Book(bookId, uuid, title, path, index, timestamp, publicationDate, isbn, authorSort, rating);

        // fetch its author
        List<Author> authors = DataModel.INSTANCE.getMapOfAuthorsByBookId().get(bookId);
        if (Helper .isNotNullOrEmpty(authors))
        {
          for (Author author : authors) {
            book.addAuthor(author);
          }
        }
        else
        {
            if (logger.isTraceEnabled())
                logger.trace("Appear to be no authors for bookId" + bookId);
        }
        
        // fetch its publisher
        List<Publisher> publishers = DataModel.INSTANCE.getMapOfPublishersByBookId().get(bookId);
        if (Helper.isNotNullOrEmpty(publishers)) {
          book.setPublisher(publishers.get(0));
        }

        // fetch its series
        List<Series> series = DataModel.INSTANCE.getMapOfSeriesByBookId().get(bookId);
        if (Helper.isNotNullOrEmpty(series))
          book.setSeries(series.get(0));
        
        // fetch its comment
        List<String> comments = DataModel.INSTANCE.getMapOfCommentsByBookId().get(bookId);
        if (Helper.isNotNullOrEmpty(comments))
          book.setComment(comments.get(0));
        
        // fetch its categories
        List<Tag> tags = DataModel.INSTANCE.getMapOfTagsByBookId().get(bookId);
        if (Helper.isNotNullOrEmpty(tags))
          book.getTags().addAll(tags);
        
        // fetch its files
        List<EBookFile> files = DataModel.INSTANCE.getMapOfFilesByBookId().get(bookId);
        if (Helper.isNotNullOrEmpty(files)) {
          for (EBookFile file : files) {
            file.setBook(book);
            book.addFile(file);
          }
        }
        result.add(book);
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public List<Author> listAuthors() {
    List<Author> result = new Vector<Author>();
    List<String> ids = new Vector<String>();
    PreparedStatement statement = DatabaseRequest.ALL_AUTHORS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String id = set.getString("id");
        if (!ids.contains(id)) {
          ids.add(id);
          result.add(new Author(id, set.getString("name"), set.getString("sort")));
        }
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }
  
  public List<Publisher> listPublishers() {
    List<Publisher> result = new Vector<Publisher>();
    List<String> ids = new Vector<String>();
    PreparedStatement statement = DatabaseRequest.ALL_PUBLISHERS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String id = set.getString("id");
        if (!ids.contains(id)) {
          ids.add(id);
          result.add(new Publisher(id, set.getString("name"), set.getString("sort")));
        }
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public List<Series> listSeries() {
    List<Series> result = new Vector<Series>();
    List<String> ids = new Vector<String>();
    PreparedStatement statement = DatabaseRequest.ALL_SERIES.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String id = set.getString("id");
        if (!ids.contains(id)) {
          ids.add(id);
          result.add(new Series(id, set.getString("name"), set.getString("serie_sort")));
        }
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public Map<String, List<EBookFile>> listFilesByBookId() {
    Map<String, List<EBookFile>> result = new HashMap<String, List<EBookFile>>();
    PreparedStatement statement = DatabaseRequest.BOOKS_DATA.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String id = set.getString("book");
        String format = set.getString("format");
        String name = set.getString("name");
        List<EBookFile> files = result.get(id);
        if (files == null) {
          files = new Vector<EBookFile>();
          result.put(id, files);
        }
        files.add(new EBookFile(format, name));
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public Map<String, List<Author>> listAuthorsByBookId() {
    Map<String, List<Author>> result = new HashMap<String, List<Author>>();
    PreparedStatement statement = DatabaseRequest.BOOKS_AUTHORS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String bookId = set.getString("book");
        String authorId = set.getString("author");
        List<Author> authors = result.get(bookId);
        if (authors == null) {
          authors = new Vector<Author>();
          result.put(bookId, authors);
        }
        Author author = DataModel.INSTANCE.getMapOfAuthors().get(authorId);
        if (author != null)
          authors.add(author);
        else 
          logger.warn("cannot find author #"+authorId);
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public Map<String, List<Publisher>> listPublishersByBookId() {
    Map<String, List<Publisher>> result = new HashMap<String, List<Publisher>>();
    PreparedStatement statement = DatabaseRequest.BOOKS_PUBLISHERS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String bookId = set.getString("book");
        String publisherId = set.getString("publisher");
        List<Publisher> publishers = result.get(bookId);
        if (publishers == null) {
          publishers = new Vector<Publisher>();
          result.put(bookId, publishers);
        }
        Publisher publisher = DataModel.INSTANCE.getMapOfPublishers().get(publisherId);
        if (publisher != null)
          publishers.add(publisher);
        else 
          logger.warn("cannot find publisher #"+publisherId);
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public Map<String, List<Tag>> listTagsByBookId() {
    Map<String, List<Tag>> result = new HashMap<String, List<Tag>>();
    PreparedStatement statement = DatabaseRequest.BOOKS_TAGS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String bookId = set.getString("book");
        String tagId = set.getString("tag");
        List<Tag> tags = result.get(bookId);
        if (tags == null) {
          tags = new Vector<Tag>();
          result.put(bookId, tags);
        }
        Tag tag = DataModel.INSTANCE.getMapOfTags().get(tagId);
        if (tag != null)
          tags.add(tag);
        else 
          logger.warn("cannot find tag #"+tagId);
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public Map<String, List<Series>> listSeriesByBookId() {
    Map<String, List<Series>> result = new HashMap<String, List<Series>>();
    PreparedStatement statement = DatabaseRequest.BOOKS_SERIES.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String bookId = set.getString("book");
        String serieId = set.getString("series");
        List<Series> series = result.get(bookId);
        if (series == null) {
          series = new Vector<Series>();
          result.put(bookId, series);
        }
        Series serie = DataModel.INSTANCE.getMapOfSeries().get(serieId);
        if (serie != null)
          series.add(serie);
        else 
          logger.warn("cannot find serie #"+serieId);
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public Map<String, List<String>> listCommentsByBookId() {
    Map<String, List<String>> result = new HashMap<String, List<String>>();
    PreparedStatement statement = DatabaseRequest.BOOKS_COMMENTS.getStatement();
    try {
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        String id = set.getString("book");
        String text = set.getString("text");
        List<String> comments = result.get(id);
        if (comments == null) {
          comments = new Vector<String>();
          result.put(id, comments);
        }
        comments.add(text);
      }
    } catch (SQLException e) {
      logger.error(e);
    }
    return result;
  }

  public static String stringToHex(String base)
  {
    StringBuffer buffer = new StringBuffer();
    int intValue;
    for(int x = 0; x < base.length(); x++)
    {
      int cursor = 0;
      intValue = base.charAt(x);
      String binaryChar = new String(Integer.toBinaryString(base.charAt(x)));
      for(int i = 0; i < binaryChar.length(); i++)
      {
        if(binaryChar.charAt(i) == '1')
        {
          cursor += 1;
        }
      }
      if((cursor % 2) > 0)
      {
        intValue += 128;
      }
      buffer.append(Integer.toHexString(intValue) + " ");
    }
    return buffer.toString();
  }
}