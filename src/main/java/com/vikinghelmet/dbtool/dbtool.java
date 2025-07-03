package com.vikinghelmet.dbtool;

import static com.vikinghelmet.dbtool.Configuration.*;
import org.apache.commons.lang.StringUtils;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static void run(String query) throws SQLException, IOException
  {
    query = replaceQueryTokens(query);

    if (isEnabled(Option.echo)) {
      println("");
      println(query);
    }

    Connection conn = getConnection();
    Statement stmt = null;

    try {
      stmt = execute (query, conn);

      if (isEnabled(Option.commit)) {
        conn.commit();
        println("Committed.");
      }
    }
    catch (SQLException e) {
      error("SQLException", e);
      conn.rollback();
    }    
    finally {
        try { if (stmt != null) stmt.close(); } catch (Exception e) {}
        try { if (conn != null) conn.close(); } catch (Exception e) {}
    }
  }

  // --------------------------------------------------------------------
  private static List<String> inferQueryFromInputCSV() throws SQLException, IOException
  {
    List<String> result = new ArrayList<>();
    String inputFilename = getProperty (Option.inputCSV);

    if (inputFilename == null) {
      error("infer requires use of inputCSV", null);
    }

    setProperty(Option.commit, "true"); // I can't imagine ever wanting to use infer/create without commit

    String tableName = inputFilename
            .replaceAll("\\.csv","")
            .replaceAll("\\.","_")
            .replaceAll(".*/","");

    List<String> columns = getColumns(inputFilename);

    Connection conn = getConnection();
    List<String> tableList = DatabaseMetaDataViewer.getTables(conn, tableName);

    if (tableList.isEmpty()) {
      StringBuilder builder = new StringBuilder("create table if not exists ")
              .append(tableName)
              .append(" ( ");

      for (int i=0; i<columns.size(); i++){
        builder.append(columns.get(i)).append(" varchar(255)");
        if (i < columns.size()-1) builder.append(",");
      }

      String createStmt = builder.append(" );").toString();

      debug("createStmt: " + createStmt);

      result.add(createStmt);
//      execute (createStmt, conn);
//      conn.commit();
    }

    StringBuilder builder = new StringBuilder("insert into ")
            .append(tableName)
            .append(" ( ");

    for (int i=0; i<columns.size(); i++){
      builder.append(columns.get(i));
      if (i < columns.size()-1) builder.append(",");
    }

    builder.append(" ) values ( ");

    for (int i=0; i<columns.size(); i++){
      builder.append(" ?s ");                         // note: the 's' in ?s implies varchar; it gets stripped out before preparing stmt
      if (i < columns.size()-1) builder.append(",");
    }

    String insertStmt = builder.append(" );").toString();

    debug("insertStmt: " + insertStmt);

    result.add(insertStmt);
    return result;
  }

  private static List<String> getColumns (String inputFilename) throws IOException {
    InputStreamReader is = inputFilename.equals("-") ?
            new InputStreamReader (System.in) :
            new InputStreamReader (new FileInputStream(inputFilename));

    ICsvListReader reader = new CsvListReader(is, CsvPreference.STANDARD_PREFERENCE);

    List<String> list = reader.read();
    return list;
  }

  // --------------------------------------------------------------------
  // TODO: refactor ...

  private static Statement execute(String query, Connection conn)
    throws SQLException, IOException
  {
    debug("building prepared statement, query = " +query);

    boolean update   = !startsWith(query, getStringList(Option.executeKeywords));
    boolean prepared = update && query.contains("?");
    List<Class> fieldTypes = null;

    if (prepared) {
      fieldTypes = getPreparedStatementTypes(query);
      query = query
              .replace("?i","?")
              .replace("?d","?")
              .replace("?s","?");
    }

    debug("final query = " +query);

    PreparedStatement stmt = conn.prepareStatement(query);

    if (update) {
      int rows = 0;

      if (!prepared) {
        rows = stmt.executeUpdate();
      }
      else {
        rows = execute (stmt, fieldTypes);
      }

      println("Success (no query results)");
      println(rows + " rows effected.");
    }
    else {
      boolean stmtResult = stmt.execute();

      if (!stmtResult){
          println("Success (no query results)");
      }
      else {
          ResultSet rs = stmt.getResultSet();
          (new ResultSetWriter()).write (rs);
          rs.close();
      }
    }
    return stmt;
  }


  // --------------------------------------------------------------------

  final static String QUESTION_MARK_PATTERN = "(\\?[si]?)+";

  // TODO: handle more field types (other than int/string), and support cases where '?' is part of a value (not a placeholder)
  private static List<Class> getPreparedStatementTypes(String query) {
    List<Class> fieldTypes = new ArrayList<Class>();

    Pattern pattern = Pattern.compile(QUESTION_MARK_PATTERN);
    Matcher matcher = pattern.matcher(query);

    while (matcher.find()) {
      String group = matcher.group();
      if (group.equals("?i")) {
        fieldTypes.add(Integer.class);
      }
      else if (group.equals("?d")) {
        fieldTypes.add(Double.class);
      }
      else {
        fieldTypes.add(String.class);
      }
    }

    return fieldTypes;
  }

  // --------------------------------------------------------------------

  private static int execute(PreparedStatement ps, List<Class> fieldTypes)
    throws SQLException, IOException
  {
    int rows = 0;

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
        rows += ps.executeUpdate();
    }
    else {
        List<String> list = null;
        ICsvListReader reader = new CsvListReader(is, CsvPreference.STANDARD_PREFERENCE);

        if (isEnabled(Option.infer)) {
          reader.read(); // skip header line
        }

        while ((list = reader.read()) != null)
        {
          // TODO: ensure array length matches number of ? in ps
          for (int i=0; i<list.size(); i++) {
            String s = list.get(i);

    //        if (s.matches("^[0-9]+$")) {
            if (fieldTypes.get(i) == Integer.class) {
              debug("setInt: " +(i+1)+", "+s);
              ps.setInt(i+1, Integer.parseInt(s));
            }
            else {
              debug("setString: " +(i+1)+", "+s);
              ps.setString(i+1, s);
            }
          }
          ps.addBatch();
          rows ++;
          if (rows % 100 == 0) {
            ps.executeBatch(); // executeUpdate();
            // println("executeBatch, rows="+rows);
          }
        }

        if (rows % 100 != 0) {
          ps.executeBatch(); // executeUpdate();
        }
    }

    return rows;
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

    private static List<String> readQueryFile(String fixFileName) throws IOException
    {
      // TODO: use semicolon separators instead of being line-based.
      BufferedReader fr = new BufferedReader(new FileReader(fixFileName));

      List<String> queries = new ArrayList<String>();

      String nextLine;
      String statement = "";

      while ((nextLine = fr.readLine()) != null)
      {
        nextLine = nextLine.replaceAll("--.*","");  // remove end-of-line comments while building query

        statement += " " + nextLine;

        if (nextLine.endsWith(";")) {
          queries.add(statement.trim().replaceAll(";$",""));
          statement = "";
        }
      }

      if (! statement.isEmpty()) {
        queries.add (statement.trim());
      }

      return queries;
    }

  // --------------------------------------------------------------------

  private static String replaceQueryTokens(String query) {
    Pattern p = Pattern.compile("#[a-zA-Z]+#");
    Matcher m = p.matcher(query);
    Map<String, String> map = new HashMap<String, String>();

    while (m.find()) {
      String token = m.group();
      String trimmedToken = token.substring(1, token.length() - 1); // trim off the hash marks

      String replaceValue = getProperty(trimmedToken);

      if (replaceValue == null) {
        error("error: no value for query parameter: "+trimmedToken, null);
      }
      else {
        map.put(token, replaceValue);
      }
    }

    if(map.size() == 0) {
      return query;
    }

    debug("Query pre-regex: " + query);

    for (String token : map.keySet()) {
      query = query.replaceAll (token, map.get(token));
    }

    debug("Query post-regex: " + query);
    return query;
  }

  // --------------------------------------------------------------------

  private static boolean startsWith(String query, List<String> keywords)
  {
    String test = query.replaceAll("\\(", "").trim().toLowerCase();

    for (String word : keywords) {
      if (test.startsWith(word)) return true;
    }
    return false;
  }

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

    String queryFilename = getProperty (Option.query);

    try {
      List<String> queries = null;

      if (queryFilename != null) {
        queries = readQueryFile(queryFilename);
      }
      else if (args.length > 0) {
        String lastArg = args[args.length - 1];

        if (lastArg.contains(" ") || lastArg.startsWith("-")) {
          queries = Collections.singletonList(lastArg);
        }
      }
      else if (isEnabled(Option.infer)) {
        queries = inferQueryFromInputCSV();
      }

      if (queries == null) {
        usage();
      }

      for (String query : queries) {
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
