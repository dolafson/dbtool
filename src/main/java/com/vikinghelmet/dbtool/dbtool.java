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
import java.sql.*;
import java.util.*;

public class dbtool
{
  public static final String usageFile = "usage.txt";

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

  private static void run(Query query) throws SQLException, IOException
  {
    String queryString = query.getQuery();
    List<QueryParameter> queryParameterList = query.getParameterList();

    debug("final query = " +query);

    boolean fetch = false;
    for (String word : getStringList(Option.fetchKeywords)) {
      if (queryString.startsWith(word)) { fetch = true; break; }
    }

    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(queryString))
    {
      if (fetch) {
        executeFetch (ps, queryParameterList);
      }
      else {
        int rows = executeUpdate (ps, queryParameterList);
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

  private static void executeFetch(PreparedStatement ps, List<QueryParameter> queryParameterList) throws SQLException {
    // validate: for fetch, params should all be named, not positional
    boolean foundNoName=false;
    for (int i = 0; !foundNoName && i< queryParameterList.size(); i++) {
      foundNoName = queryParameterList.get(i).getName() == null;
    }
    if (foundNoName) {
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

  private static int executeUpdate(PreparedStatement ps, List<QueryParameter> queryParameterList)
    throws SQLException, IOException
  {
    if (queryParameterList.isEmpty()) {
      return ps.executeUpdate();
    }

    boolean blob = false;
    String inputFilename = getProperty (Option.inputCSV);

    if (StringUtils.isEmpty(inputFilename)) {
        inputFilename = getProperty (Option.inputText);
        blob = true;
    }

    if (StringUtils.isEmpty(inputFilename)) {
        throw new IOException ("use of prepared statement requires a filename");
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
      for (int i=0; i<list.size(); i++) {
        setQueryParameter (ps, i+1, queryParameterList.get(i), list.get(i));
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

  private static void setQueryParameter (PreparedStatement ps, int paramIndex, QueryParameter param, String value) throws SQLException
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

  private static String readFile(Reader r) throws IOException
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

  public static void usage()
  {
    String filename = usageFile;
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

  public static void main(String[] args)
  {
    Configuration.init(args);

    boolean metaDataRequest = false;    // was metadata requested ?
    int commandStart = 0;               // first arg after all "-d" and "param=value" args                            

    for (int i=0; i<args.length; i++) {
      if (args[i].equals("-d")) {
        metaDataRequest = true;
      }
      else if (commandStart == 0 && args[i].equals("getNodes"))
      {
        System.out.println ("nodes = "+Configuration.getNodes());
        System.exit(0);
      }
      else if (commandStart == 0 &&
              (args[i].equals(DatabaseMetaDataViewer.DESCRIBE) ||
               args[i].equals(DatabaseMetaDataViewer.GET_TABLES) ||
               args[i].equals(DatabaseMetaDataViewer.GET_SCHEMAS)))
      {
          metaDataRequest = true;
          commandStart = i;
          break;
      }
      else if (! args[i].contains("=") && commandStart == 0) {
        commandStart = i;
      }
    }

    if (metaDataRequest) {
      if (commandStart == 0 && args.length == 1 && args[0].equals("-d")) { // special case to show dmdv usage
        commandStart = args.length;
      }
      int size = args.length - commandStart;
      String args2[] = new String[size];

      System.arraycopy(args, commandStart, args2, 0, size);

      try {
        DatabaseMetaDataViewer.runCommand(args2);
      }
      catch (SQLException e) {
        error("SQLException", e);
      }
      return;
    }

    if (isEnabled(Option.create)) {
      setProperty(Option.infer, "true"); // there is no good use for create without infer
    }

    try {
      List<Query> queryList = QueryBuilder.build(args);

      if (queryList.isEmpty()) {
        usage();
      }

      for (Query query : queryList) {
        run(query);
      }
    }
    catch (SQLException e) {
      error("SQLException", e);
    }
    catch (IOException e) {
      error("unable to read query file", e);
    }
  }
}
