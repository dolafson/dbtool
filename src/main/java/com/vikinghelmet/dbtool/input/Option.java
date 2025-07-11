package com.vikinghelmet.dbtool.input;

/**
 * Created by IntelliJ IDEA.
 * User: dolafson
 * Date: Sep 18, 2009
 * Time: 5:23:22 PM
 * To change this template use File | Settings | File Templates.
 */
public enum Option {
  // booleans
  create, infer, describe, commit, dobreak, headers, footer, html, quoteAll, quoteText, formatted, echo, human, verbose, dump,

  errorOnEmptyResultSet,

  // strings
  rowcolor1("#FFFFE0"),
  rowcolor2("#AAFFCC"),
  delim(","),

  fetchKeywords("select,call"),
  jdbc_class,
  jdbc_url,
  jdbc_user,
  jdbc_password,
  
  node,
  query,
  inputCSV,
  inputText,
  schema,
  regexcol,
  regexvalue,
  regexreplace,

  // integers
  maxwidth("25"),
  maxrows("-1"),
  norepeats("0"),
  ;

  String defaultValue;

  private Option() {
  }
  
  private Option(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getKey() {
    return name().replaceAll("_", ".");  // for backward compatibility (old props files use "jdbc.*")
  }
  
  public String getDefaultValue() {
    return defaultValue;
  }
}

