package com.vikinghelmet.dbtool;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dolafson
 * Date: Sep 20, 2009
 * Time: 6:42:35 AM
 * To change this template use File | Settings | File Templates.
 */
public class DatabaseMetaDataViewer {

  public final static int MAX_PARAMS = 10;
  public final static String usageFile = "dmdViewerUsage.txt";

  public final static String DESCRIBE = "describe";
  public final static String GET_TABLES = "getTables";
  public final static String GET_SCHEMAS = "getSchemas";
  public final static String GET_INDEX_INFO = "getIndexInfo";

  private static final String DEFAULT_CATALOG = null; // "";
  private static final String DEFAULT_TABLE_NAME_PATTERN = "%";
  private static final String DEFAULT_COLUMN_NAME_PATTERN = "%";

  /* as of jdk 1.6, method count for DatabaseMetaData, grouped by return type:
   *
   *    String     18
   *    ResultSet  21
   *    int        29
   *    bool       96
   *
   * all method names are unique except for two: getSchemas(), supportsConvert() ...
   * both have one signature with no args, and another sig with two args ...
   * of those two, the only one i care about at the moment is getSchemas ...
   *
   * for getSchemas, the older method (0-arg) is used, because the newer (2-arg)
   * method is not supported by many jdbc drivers
   */

  public static HashMap<String,Method> methodMap = new HashMap<String,Method>();

  static {
    for (Method m : DatabaseMetaData.class.getMethods()) {
      String name = m.getName();

      if (! name.equals(GET_SCHEMAS) && ! name.equals(GET_TABLES) && ! name.equals(DESCRIBE)) {
        methodMap.put(name, m);
      }
    }
  }

  // convert String[] to Object[], filling in each slot according to argType
  // - supports args of type String, int, and boolean
  // - also supports Array args, but only in the final element of the argList
  // - all methods currently exposed by DatabaseMetaData are supported
  private static Object[] getParams(String[] args, Class<?> argTypes[]) {
    int size1 = args.length - 1;
    int size2 = argTypes.length;
    int offset = 1;

    Object args2[] = new Object[size2];
    System.arraycopy(args, offset, args2, 0, size1);

    for (int i=0; i<args2.length; i++) {
      String s = (i+offset < args.length) ? args[i+offset] : null;

      // convert "null" to null
      if (s != null && s.equalsIgnoreCase("null")) {
        args2[i] = null;
      }

      // dbtool.println ("argType("+i+") = "+argTypes[i]);

      if (argTypes[i] == int.class) {
        args2[i] = NumberUtils.toInt(s);
      }
      else if (argTypes[i] == boolean.class) {
        args2[i] = BooleanUtils.toBoolean(s);
      }
      else if (argTypes[i].isArray()) {
        Class dataType = argTypes[i].getComponentType();
        int arrayLength = args.length - offset - i;

        // if we have already exhausted the original arg list, set the array arg to null
        if (arrayLength <= 0 || (s != null && s.equalsIgnoreCase("null"))) {
          args2[i] = null;
        }
        else {
          Object[] array = (Object[]) java.lang.reflect.Array.newInstance(dataType, arrayLength);
          args2[i] = array;

          // keep going until we exhaust the original arg list
          for (int j=0; j<arrayLength && i<args.length; j++) {
            s = (String) args[offset+i+j];
            // at the moment, only int & String arrays are required
            array[j] = (dataType == int.class) ? NumberUtils.toInt(s) : s;
          }
        }
      }
      else if (args2[i] != null) {
        args2[i] = (""+args2[i]).toUpperCase();
      }
    }

    return args2;
  }

    private static ResultSet execute(Connection conn, String query) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(query);
        boolean stmtResult = stmt.execute();
        // TODO: error handling
        return stmt.getResultSet();
    }

  private static List<String> getTableList(Connection conn, String catalog, String schema, String tableNamePattern) throws SQLException {
    List<String> result = new ArrayList<String>();
    DatabaseMetaData dmd = conn.getMetaData();
    ResultSet rs = dmd.getTables(catalog, schema, tableNamePattern, null);
    while (rs.next()) {
      String tableName = rs.getString(3);
      String tableType = rs.getString(4);

      if (tableType.equalsIgnoreCase("TABLE")) {
        result.add(tableName);
      }
    }
    return result;
  }

    public static List<String> getTables(Connection conn, String pattern) throws SQLException {
        List<String> tables;
        // System.out.println ("class: "+conn.getClass().toString());

        if (pattern == null || pattern.isEmpty()) {
            pattern = DEFAULT_TABLE_NAME_PATTERN;
        }

        // shortcut: if user didn't include a wildcard, stick it on the end
        if (! pattern.contains(DEFAULT_TABLE_NAME_PATTERN)) {
            pattern = pattern + DEFAULT_TABLE_NAME_PATTERN;
        }            

        String schema  = getSchema();
        tables = getTableList(conn, null, schema, pattern);
        return tables;
    }

  private static boolean isWildcard(Object obj) {
    if (obj == null) return true;
    if (!(obj instanceof String)) return false;
    String s = (String) obj;
    return s.length() == 0 || s.contains("%");
  }

  // workaround for DMD.getIndexInfo(), which requires an explicit tablename instead of a tablename pattern
  private static void getIndexInfo(Connection conn, Object[] p)
          throws SQLException, IllegalAccessException, InvocationTargetException
  {
    DatabaseMetaData dmd = conn.getMetaData();
    Method m = methodMap.get(GET_INDEX_INFO);

    if (! isWildcard(p[2])) {
      (new ResultSetWriter()).write ((ResultSet) (m.invoke(dmd, (Object[]) p)));
      return;
    }

    // note: first 3 args of getTables and getIndexInfo match, although getTables takes
    // a pattern, and getIndexInfo requires an exact match ...
    List<String> tables = getTableList(conn, (String) p[0], (String) p[1], (String) p[2]);

    // println(""+tables);
    Configuration.disable(Option.footer);

    for (String table : tables) {
      if (table.contains("$")) { // TODO: ???
        continue;
      }
      p[2] = table;

      ResultSet rs = (ResultSet) (m.invoke(dmd, (Object[]) p));
      (new ResultSetWriter()).write(rs);
      rs.close();

      Configuration.disable(Option.headers);
    }
  }

  private static String getSchema() {
      return Configuration.getProperty(Option.schema);
  }

  public static void runCommand(String[] args) throws SQLException {
    Connection conn = dbtool.getConnection();
    DatabaseMetaData dmd = conn.getMetaData();

    if (args.length == 0) {
      usage();
    }

    String cmd = args[0];

    try {
      if (cmd.equals(GET_SCHEMAS)) {
        // for getSchemas, the older method (0-arg) is used, because the newer (2-arg)
        // method is not supported by many jdbc drivers

          ResultSet rs = null;
          ResultSetWriter rsw = new ResultSetWriter();

          rs = dmd.getSchemas ();
          rsw.suppressColumns( 2 );
          
          rsw.write (rs);
        return;
      }

        if (cmd.equals(GET_TABLES)) {
            List<String> tables = getTables(conn, (args.length > 1) ? args[1] : null);

            for (String table : tables) {
                System.out.println(""+table);
            }

            return;
        }

        if (cmd.equals(DESCRIBE)) {
            String catalog = DEFAULT_CATALOG;
            String schema  = getSchema();
            String table   = (args.length > 1) ? args[1] : DEFAULT_TABLE_NAME_PATTERN; // args[1].toUpperCase() :

            ResultSet rs = null;
            ResultSetWriter rsw = new ResultSetWriter();

            rs = dmd.getColumns(catalog, schema, table, DEFAULT_COLUMN_NAME_PATTERN);
            rsw.suppressColumns( 1,2,5,7,8,9,10,11,12,13,14,15,17,19,20,21,22,23 );

            if (rs == null) {
                System.err.println ("table not found: "+table+", schema = "+schema);
                return;
            }

            try {
                Configuration.enable(Option.errorOnEmptyResultSet);            
                rsw.write (rs);
            }
            catch (Exception e) {
                System.err.println ("table not found: "+table+", schema = "+schema);
            }
            return;
        }

      Method m = methodMap.get(cmd);

      if (m == null) {
        System.err.println ("method not found: "+cmd);
        return;
      }

      Object p[] = getParams(args, m.getParameterTypes());

      // dbtool.println ("found method: "+m);
      // dbtool.println ("params: "+Arrays.asList(p));

      if (cmd.equals(GET_INDEX_INFO)) {
        // workaround for DMD.getIndexInfo(), which requires an explicit tablename instead of a tablename pattern
        getIndexInfo(conn, p);
      }
      else if (m.getReturnType() == ResultSet.class) {
        (new ResultSetWriter()).write ((ResultSet) (m.invoke(dmd, (Object[]) p)));
      }
      else {
        System.out.println (""+m.invoke(dmd, (Object[]) p));
      }
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private static void usage() {
    dbtool.usage();
  }

  public static void main(String[] args) {
    Configuration.init(args);
    
    try {
      runCommand(args);
    }
    catch (SQLException e) {
      dbtool.error ("SQLException", e);
    }
  }
}
