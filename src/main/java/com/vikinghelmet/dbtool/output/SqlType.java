package com.vikinghelmet.dbtool.output;

import java.sql.Types;

/**
 * Created by IntelliJ IDEA.
 * User: dolafson
 * Date: Sep 19, 2009
 * Time: 7:47:40 AM
 * To change this template use File | Settings | File Templates.
 */
public enum SqlType {
  ARRAY   (Types.ARRAY),
  BIGINT  (Types.BIGINT),
  BINARY  (Types.BINARY),
  BIT     (Types.BIT),
  BLOB    (Types.BLOB),
  BOOLEAN (Types.BOOLEAN),
  CHAR    (Types.CHAR),
  CLOB    (Types.CLOB),
  DATALINK(Types.DATALINK),
  DATE    (Types.DATE),
  DECIMAL (Types.DECIMAL),
  DISTINCT(Types.DISTINCT),
  DOUBLE  (Types.DOUBLE),
  FLOAT   (Types.FLOAT),
  INTEGER (Types.INTEGER),

  JAVA_OBJECT   (Types.JAVA_OBJECT),
  LONGVARBINARY (Types.LONGVARBINARY),
  LONGVARCHAR   (Types.LONGVARCHAR),

  NULL      (Types.NULL),
  NUMERIC   (Types.NUMERIC),
  OTHER     (Types.OTHER),
  REAL      (Types.REAL),
  REF       (Types.REF),
  SMALLINT  (Types.SMALLINT),
  STRUCT    (Types.STRUCT),
  TIME      (Types.TIME),
  TIMESTAMP (Types.TIMESTAMP),
  TINYINT   (Types.TINYINT),
  VARBINARY (Types.VARBINARY),
  VARCHAR   (Types.VARCHAR),
  ;

  int sqlType;
  
  SqlType(int sqlType) {
    this.sqlType = sqlType;
  }

  public static SqlType get(int value) {
    for (SqlType e : values()) {
      if (e.sqlType == value) return e;
    }
    return null;
  }

  public static String getName(final int type)
  {
    SqlType e = SqlType.get(type);
    return (e == null) ? "{invalid type}" : e.name();
  }  
}
