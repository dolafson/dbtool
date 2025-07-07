package com.vikinghelmet.dbtool.output;

import com.vikinghelmet.dbtool.input.Configuration;
import com.vikinghelmet.dbtool.dbtool;
import com.vikinghelmet.dbtool.input.Option;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.vikinghelmet.dbtool.dbtool.debug;

/**
 * Created by IntelliJ IDEA.
 * User: dolafson
 * Date: Sep 20, 2009
 * Time: 6:42:35 AM
 * To change this template use File | Settings | File Templates.
 */
public class DatabaseMetaDataViewer {

    public final static String GET_INDEX_INFO = "getIndexInfo";
    private static final String DEFAULT_CATALOG = null; // "";
    private static final String DEFAULT_TABLE_NAME_PATTERN = "%";
    private static final String DEFAULT_COLUMN_NAME_PATTERN = "%";

    Connection conn = null;
    DatabaseMetaData dmd = null;

    public DatabaseMetaDataViewer() {
    }

    private Connection getConnection() {
        if (conn == null) {
            conn = dbtool.getConnection();
        }
        return conn;
    }

    private DatabaseMetaData getDatabaseMetaData() throws SQLException {
        if (dmd == null) {
            dmd = getConnection().getMetaData();
        }
        return dmd;
    }

  public static HashMap<String,Method> methodMap = new HashMap<>();

  static {
    for (Method m : DatabaseMetaData.class.getMethods()) {
        methodMap.put(m.getName(), m);
    }
  }

  // convert String[] to Object[], filling in each slot according to argType
  // - supports args of type String, int, and boolean
  // - also supports Array args, but only in the final element of the argList
  // - all methods currently exposed by DatabaseMetaData are supported
  private Object[] getParams(String[] args, Class<?> argTypes[]) {
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
            s = args[offset+i+j];
            // at the moment, only int & String arrays are required
            array[j] = (dataType == int.class) ? NumberUtils.toInt(s) : s;
          }
        }
      }
      else if (args2[i] != null) {
        args2[i] = (""+args2[i]); // .toUpperCase(); // upper case was needed long ago for db2, causes problems in more modern rdbms
      }
    }

    return args2;
  }

  private List<String> getTableList(Connection conn, String catalog, String schema, String tableNamePattern) throws SQLException {
    List<String> result = new ArrayList<String>();
    DatabaseMetaData dmd = conn.getMetaData();
    debug("getTableList, schema = "+schema);
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

    public List<String> getTables(Connection conn, String pattern) throws SQLException {
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

    private String getSchema() {
        return Configuration.getProperty(Option.schema);
    }

    public boolean isSupported(String cmd) {
        return methodMap.get(cmd) != null;
    }

    public void printTables(String tablePrefix) throws SQLException {
        List<String> tables = getTables(getConnection(), tablePrefix);
        for (String table : tables) {
            System.out.println(table);
        }
    }

    public void printSchemas() throws SQLException {
        // for getSchemas, the older method (0-arg) is used, because the newer (2-arg)
        // method is not supported by many jdbc drivers
        ResultSet rs = getDatabaseMetaData().getSchemas ();
        ResultSetWriter rsw = new ResultSetWriter();
        rsw.suppressColumns( 2 );
        rsw.write (rs);
    }

    public void describe(String table) throws SQLException {
        String catalog = DEFAULT_CATALOG;
        String schema  = getSchema();
        if (table == null) {
            table = DEFAULT_TABLE_NAME_PATTERN;
        }
        
        // shortcut: if user didn't include a wildcard, stick it on the end
        if (! table.contains(DEFAULT_TABLE_NAME_PATTERN)) {
            table = table + DEFAULT_TABLE_NAME_PATTERN;
        }

        ResultSet rs = getDatabaseMetaData().getColumns(catalog, schema, table, DEFAULT_COLUMN_NAME_PATTERN);
        ResultSetWriter rsw = new ResultSetWriter();
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
    }

    public void runCommand(String[] args) throws SQLException, IllegalAccessException, InvocationTargetException
    {
        String cmd = args[0];
        Method m = methodMap.get(cmd);

        if (m == null) {
            System.err.println ("method not found: "+cmd); // should not get here if we first check "isSupported"
            return;
        }

        Object[] p = getParams(args, m.getParameterTypes());

        if (m.getReturnType() == ResultSet.class) {
            (new ResultSetWriter()).write ((ResultSet) (m.invoke(getDatabaseMetaData(), p)));
        }
        else {
            System.out.println (""+m.invoke(getDatabaseMetaData(), p));
        }
    }
}
