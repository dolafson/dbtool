package com.vikinghelmet.dbtool;

import static com.vikinghelmet.dbtool.input.Configuration.*;

import com.vikinghelmet.dbtool.input.*;
import com.vikinghelmet.dbtool.output.DatabaseMetaDataViewer;
import com.vikinghelmet.dbtool.output.ResultSetWriter;
import org.apache.commons.lang.StringUtils;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;

public class dbtool
{
  public static final String usageFile = "usage.txt";
  public final static String metaUsageFile = "dmdViewerUsage.txt";

  public void run(Query query) throws SQLException, IOException
  {
    String queryString = query.getQuery();

    debug("final query = " +query);

    boolean fetch = false;
    for (String word : getStringList(Option.fetchKeywords)) {
      if (queryString.startsWith(word)) { fetch = true; break; }
    }

    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(queryString))
    {
      if (fetch) {
        executeFetch (query, ps);
      }
      else {
        int rows = executeUpdate (query, ps);
        println("Success (no query results)");
        println(rows + " rows effected.");
      }

      if (isEnabled(Option.commit)) {
        conn.commit();
        println("Committed.");
      }
    }
  }

  // --------------------------------------------------------------------

  private void executeFetch(Query query, PreparedStatement ps) throws SQLException
  {
    List<QueryParameter> queryParameterList = query.getParameterList();

    if (! query.allNamedParameters()) {
      throw new IllegalArgumentException("on fetch, only named parameters are supported");
    }

    for (int i = 0; i< queryParameterList.size(); i++) {
      setQueryParameter (ps, i+1, queryParameterList.get(i), null);
    }

    ResultSet rs = ps.executeQuery();
    (new ResultSetWriter()).write (rs);
    rs.close();
  }


  // --------------------------------------------------------------------

  private int executeUpdate(Query query, PreparedStatement ps)
    throws SQLException, IOException
  {
    List<QueryParameter> queryParameterList = query.getParameterList();

    if (query.allNamedParameters()) {
      for (int i = 0; i< queryParameterList.size(); i++) {
        setQueryParameter (ps, i+1, queryParameterList.get(i), null);
      }
      return ps.executeUpdate();
    }

    boolean blob = false;
    String inputFilename = getProperty (Option.inputCSV);

    if (StringUtils.isEmpty(inputFilename)) {
        inputFilename = getProperty (Option.inputText);
        blob = true;
    }

    if (StringUtils.isEmpty(inputFilename)) {
        throw new IOException ("use of unnamed positional parameters requires a filename");
    }

    InputStreamReader is = inputFilename.equals("-") ?
      new InputStreamReader (System.in) :
      new InputStreamReader (new FileInputStream(inputFilename));

    if (blob) {
        String blobString = readFile(is);
        ps.setString(1, blobString);
        return ps.executeUpdate();
    }

    int rows = 0;
    List<String> list = null;
    ICsvListReader reader = new CsvListReader(is, CsvPreference.STANDARD_PREFERENCE);

    if (isEnabled(Option.infer)) {
      reader.read(); // skip header line
    }

    while ((list = reader.read()) != null)
    {
      debug("executeUpdate, list = "+list);
      int inputIndex=0;
      int outputIndex=1;
      for (QueryParameter param : queryParameterList) {
        String value = param.isNamed() ? param.getNamedValue() : list.get(inputIndex++);
        setQueryParameter (ps, outputIndex++, param, value);
      }

      ps.addBatch();
      rows ++;
      if (rows % 100 == 0) {
        ps.executeBatch();
      }
    }

    if (rows % 100 != 0) {
      ps.executeBatch(); // executeUpdate();
    }

    return rows;
  }

  private void setQueryParameter (PreparedStatement ps, int paramIndex, QueryParameter param, String value) throws SQLException
  {
    if (value == null) {
      value = param.getNamedValue();
      if (value == null) throw new IllegalArgumentException("no value provided for named parameter: "+param.getName());
    }

    if (param.getClazz() == null) {
      param.guessClazz(value);
    }

    if (param.getClazz() == Boolean.class) {
      debug("setBoolean: " +paramIndex+", "+ value);
      ps.setBoolean (paramIndex, Boolean.parseBoolean(value));
    }
    else if (param.getClazz() == Double.class) {
      debug("setDouble: " +paramIndex+", "+ value);
      ps.setDouble (paramIndex, Double.parseDouble(value));
    }
    else if (param.getClazz() == Integer.class) {
      debug("setInt: " +paramIndex+", "+ value);
      ps.setInt (paramIndex, Integer.parseInt(value));
    }
    else {
      debug("setString: " +paramIndex+", "+ value);
      ps.setString (paramIndex, value);
    }
  }

  private String readFile(Reader r) throws IOException
    {
      BufferedReader fr = new BufferedReader(r);
      String result = "";
      String nextLine;

      while ((nextLine = fr.readLine()) != null) {
        result += nextLine +" \n";                   // TODO: better newline treatment (DB eats newlines ?)
      }

      return result;
    }

  // --------------------------------------------------------------------

  public static Connection getConnection ()
  {
    final String className = getProperty(Option.jdbc_class);

    try {
      Class.forName(className);
    }
    catch (Throwable e) {
      error("Could not load JDBC driver class (" + className + ")", e);
    }

    try {
      Connection conn = DriverManager.getConnection(
              getProperty(Option.jdbc_url),
              getProperty(Option.jdbc_user),
              getProperty(Option.jdbc_password));

      conn.setAutoCommit(false);
      return conn;
    }
    catch (SQLException e) {
      e.printStackTrace();
      System.exit(1);
    }

    return null;
  }

  // --------------------------------------------------------------------

  public static void println(final String s) {
    System.out.println(s);
  }

  public static void debug(final String s) {
    if (isEnabled(Option.verbose)) {
      println(s);
    }
  }
  public static void error(final String message, final Throwable e)
  {
    System.err.println(message);
    if (e != null && isEnabled(Option.verbose)) {
      e.printStackTrace();
    }
    System.exit(1);
  }

  public static void usage(String filename)
  {
    try (InputStream is = dbtool.class.getClassLoader().getResourceAsStream(filename))
    {
      if (is == null) {
        System.err.println ("resource not found: "+filename);
      }
      else {
        System.out.println (new String (is.readAllBytes()));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    System.exit(1);
  }

  // --------------------------------------------------------------------

  public static String[] copyArgs(String[] args, int offset) {
    int size = args.length - offset;
    String args2[] = new String[size];
    System.arraycopy(args, offset, args2, 0, size);
    return args2;
  }

  public static String optArg(String[] args, int optIndex) {
    return (args.length > optIndex) ? args[optIndex] : null;
  }

  public static void main(String[] args)
  {
    int i = Configuration.init(args);

    try {
      DatabaseMetaDataViewer viewer = new DatabaseMetaDataViewer();

      switch (args[i]) {
        case "getNodes":   System.out.println (Configuration.getNodes()); return;
        case "getSchemas": viewer.printSchemas(); return;
        case "describe":   viewer.describe    (optArg (args,i+1)); return;
        case "getTables":  viewer.printTables (optArg (args,i+1)); return;
        case "metaUsage":  usage(metaUsageFile); return;
      }

      if (viewer.isSupported (args[i])) {
        viewer.runCommand (copyArgs (args,i));
        return;
      }

      List<Query> queryList = QueryBuilder.build(args);

      if (queryList.isEmpty()) {
        usage(usageFile);
      }

      dbtool tool = new dbtool();

      for (Query query : queryList) {
        tool.run(query);
      }
    }
    catch (SQLException | IllegalAccessException | InvocationTargetException e) {
      error("SQLException", e);
    }
    catch (IOException e) {
      error("unable to read query file", e);
    }
  }
}
