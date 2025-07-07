package com.vikinghelmet.dbtool.output;

import com.vikinghelmet.dbtool.input.Option;

import static com.vikinghelmet.dbtool.input.Configuration.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: dolafson
 * Date: Sep 19, 2009
 * Time: 8:06:00 AM
 * To change this template use File | Settings | File Templates.
 */
public class ResultSetWriter {

  // warning: may be oracle-specific ... ?
  private static Pattern TIMESTAMP_PATTERN =
          Pattern.compile("([0-9-]+)\\.([0-9]+)\\.([0-9]+)\\. ([0-9]+)\\. [0-9]+");

  private static String outputBuffer = "";

  private List<Integer> suppressColumns = new ArrayList<Integer>();
  private PrintStream os;

  public ResultSetWriter() {
    this(System.out);
  }

  public ResultSetWriter(PrintStream os) {
    this.os = os;
  }

  public void suppressColumn(int i) {
    suppressColumns.add(i);
  }

    public void suppressColumns(int ... cols) {
        for (int i : cols) {
            suppressColumns.add(i);
        }
    }

  public void write(final ResultSet rs) throws SQLException
  {
    long startTime = System.currentTimeMillis();

    ResultSetMetaData rsmd = rs.getMetaData();
    boolean empty = !rs.next();

    if (empty && isEnabled(Option.errorOnEmptyResultSet)) {
        throw new SQLException("empty result set");
    }

    if (isEnabled(Option.html)) {
      writeHtml(rs, rsmd, empty);
    }
    else {
      writePlainText(rs, rsmd, empty);
    }

    if(isEnabled(Option.footer)) {
      long finishTime = System.currentTimeMillis();

      BigDecimal answer = new BigDecimal(finishTime - startTime).divide(new BigDecimal(1000),3,BigDecimal.ROUND_HALF_EVEN);

      println(answer.toPlainString() + " seconds elapsed.");
    }
  }

  // -------------------------------------------------------------------------------------------------------

  private void writePlainText(final ResultSet rs, final ResultSetMetaData rsmd, boolean empty) throws SQLException
  {
    int maxRows = getInteger(Option.maxrows);
    int numRepeatColumnsToSurpress = getInteger(Option.norepeats);

    int rows = 0;
    int colcount = rsmd.getColumnCount();
    String[] repeats = getRepeatArray();

    if (isEnabled(Option.describe)) {
      describe(rsmd);
    }

    if (isEnabled(Option.headers)) {
      showHeader(rsmd);
    }

    boolean first = true;

    while (!empty && (first || rs.next())) {
      first = false;

      if (maxRows > 0 && rows >= maxRows) {
        break;
      }

      for (int i = 1; i <= colcount; i++)
      {
        String value = getColumnValue(rs, i, rows);

        if(i <= numRepeatColumnsToSurpress) {
          if(repeats[i-1].equals(value)) {
            //Suppress the repeat
            value="";
          }
          else {
            //Save the value to compare to the next row.
            repeats[i-1] = value;
            if((i==1) && isEnabled(Option.dobreak) && (rows > 0)) {
              println("");
            }
          }
        }

        writeColumn(rs, i, value);
      }

      println("");
      rows++;
    }

    if (isEnabled(Option.footer)) {
      println(rows + " row"+((rows==1)?"":"s")+" returned.");
    }
  }

  // -------------------------------------------------------------------------------------------------------

  private void writeColumn(ResultSet rs, int i, String output) throws SQLException
  {
    if (suppressColumns.contains(i)) {
        return;
    }
    ResultSetMetaData rsmd = rs.getMetaData();
    int colcount = rsmd.getColumnCount();

    if (i > 1) {
      print(getProperty(Option.delim));
    }

    boolean doQuotes = isEnabled(Option.quoteAll) || (!rsmd.isSigned(i) && isEnabled(Option.quoteText));

    if (doQuotes) {
      print("\"");
    }

    int totalLen = getDisplayColumnWidth(rsmd, i);
    
    if (doQuotes) {
      totalLen -= 2;
    }

    if (isEnabled(Option.formatted))
    {
      // Truncate the value if it is too long.
      if (output.length() > totalLen) {
        output = output.substring(0, totalLen);
      }
      else  {
        // if this is a number, left-pad it.
        if (rsmd.isSigned(i)) {
          for (int j = totalLen - output.length(); j > 0; j--) {
            print(" ");
          }
        }
      }
    }

    print(output);

    if (doQuotes) {
      print("\"");
    }

    // right-pad if needed
    if (isEnabled(Option.formatted) && !rsmd.isSigned(i) && i < colcount)
    {
      for (int j = totalLen - output.length(); j > 0; j--) {
        print(" ");
      }
    }
  }

  // -------------------------------------------------------------------------------------------------------

  private String getColumnValue(ResultSet rs, int i, int rows) throws SQLException
  {
    int type = rs.getMetaData().getColumnType(i);

    int blobColumn = getBlobColumnNumber(rs.getMetaData());

    String output;
    if ((blobColumn > 0) && ((type == Types.BLOB) || (type == Types.CLOB)))
    {
      String fileName = writeBlobToFile(rs, i, rows);
      output = fileName;
    }
    else if (type == Types.TIMESTAMP) {
      output = formatTimestamp(rs.getString(i));
    }
    else {
      output = rs.getString(i);
    }

    if(output == null) {
      output = "{NULL}";
    }
    else {
      //Trim any non-printable characters from the string
      output = output.replaceAll("[\\u0000-\\u001F]|[\\u0080-\\uFFFF]+","");
    }

    return output;
  }

  // -------------------------------------------------------------------------------------------------------
  
  private String writeBlobToFile(ResultSet rs, int i, int rows) throws SQLException {
    // Don't display the BLOB, write it to a file
    String fileName = "blob."+i+"."+rows+ ".bin";

    Blob b = rs.getBlob(i);
    InputStream is = b.getBinaryStream();

    int count;
    byte[] buffer = new byte[1024000];

    try {
      FileOutputStream fos = new FileOutputStream(fileName);

      while ((count = is.read(buffer)) != -1) {
        fos.write(buffer, 0, count);
      }

      fos.close();
    }
    catch (IOException e) {
      System.err.println("Couldn't write the blob.  Continuing ..");
    }
    return fileName;
  }

  // -------------------------------------------------------------------------------------------------------

  private String formatTimestamp(String input) {
    if (input == null || input.length() == 0) {
      return input;
    }

    Matcher matcher = TIMESTAMP_PATTERN.matcher(input);

    if (! matcher.matches()) {
      //throw new RuntimeException("arrrrg");
      return input;
    }

    MatchResult result = matcher.toMatchResult();

    String ymd = result.group(1);
    int hms[] = new int[3];

    for (int i=0; i<3; i++) {
      //for (int i=0; i<2; i++) {
      hms[i] = Integer.parseInt(result.group(2+i));
    }

    return String.format("%s %02d:%02d:%02d", ymd, hms[0], hms[1], hms[2]);
  }

  // -------------------------------------------------------------------------------------------------------

  private int getBlobColumnNumber(ResultSetMetaData rsmd) throws SQLException {
    int colcount = rsmd.getColumnCount();

    for (int i = 1; i <= colcount; i++) {
      if (rsmd.getColumnLabel(i).equalsIgnoreCase("blobkey")) {
        return i;
      }
    }
    return -1;
  }

  // -------------------------------------------------------------------------------------------------------

  private void showHeader(ResultSetMetaData rsmd) throws SQLException {

    int colcount = rsmd.getColumnCount();
    String delimeter  = getProperty(Option.delim);
    boolean formatted = isEnabled(Option.formatted);

    // Display the column labels
    for (int i = 1; i <= colcount; i++)
    {
        if (suppressColumns.contains(i)) {
            continue;
        }

      if (i > 1) {
        print(delimeter);
      }
      
      print(rsmd.getColumnLabel(i));

      if (formatted && i < colcount)
      {
        int colPad = getDisplayColumnWidth(rsmd, i) - rsmd.getColumnLabel(i).length();
        for (int k = 0; k < colPad; k++) {
          print(" ");
        }
      }
    }

    println("");

    if (formatted) {
      // Display the 'underlines' under the column labels
      for (int i = 1; i <= colcount; i++)
      {
          if (suppressColumns.contains(i)) {
              continue;
          }

        if (i > 1) {
          print(delimeter);
        }

        int width = getDisplayColumnWidth(rsmd, i);
        for (int j = 0; j < width; j++) {
          print("=");
        }
      }

      println("");
    }
  }

  // -------------------------------------------------------------------------------------------------------

  private void describe(ResultSetMetaData rsmd) throws SQLException {
    int colcount = rsmd.getColumnCount();

    println("Result Set Description");
    println("Column # : Length : DB Type : java.sql Type :: Column Name");

    for (int i = 1; i <= colcount; i++)
    {
      print(""+i);
      print(" : ");
      print(""+rsmd.getColumnDisplaySize(i));
      print(" : ");

      int precision = rsmd.getPrecision(i);
      int scale = rsmd.getScale(i);
      StringBuffer typeName = new StringBuffer(rsmd.getColumnTypeName(i));

      if (rsmd.isSigned(i) && scale == 0)
      {
        // Don't include size details
      }
      else
      {
        typeName.append("(").append(precision);
        if (scale != 0)
        {
          typeName.append(",").append(scale);
        }
        typeName.append(")");
      }

      print(typeName.toString());
      print(" : ");
      print(SqlType.getName(rsmd.getColumnType(i)));
      print(" :: ");
      println(rsmd.getColumnLabel(i));
    }

    println("");
  }

  // -------------------------------------------------------------------------------------------------------

  private void writeHtml(final ResultSet rs, final ResultSetMetaData rsmd, boolean empty) throws SQLException
  {
    final boolean doBreak = isEnabled(Option.dobreak);

    final int numRepeatColumnsToSurpress = getInteger(Option.norepeats);

    final String rowcolor1 = getProperty(Option.rowcolor1);
    final String rowcolor2 = getProperty(Option.rowcolor2);
    final String regexCols = getProperty(Option.regexcol);
    final String regexValue = getProperty(Option.regexvalue);
    final String regexReplace = getProperty(Option.regexreplace);

    int colcount = rsmd.getColumnCount();
    int rows = 0;
    boolean toggle = true;

    String[] repeats = getRepeatArray();

    println("<HTML>\n<HEAD>\n<TITLE>Query output</TITLE>\n</HEAD>");

    println("<BODY>\n<TABLE border=\"1\">\n");

    print("<TR>");

    for(int i=1; i <= colcount; i++)
    {
      print("<TH BGCOLOR=\"#000000\"><FONT COLOR=\"#FFFFFF\">");
      print(rsmd.getColumnLabel(i));
      print("</FONT></TH>");
    }

    println("</TR>");

    boolean first = true;

    while (!empty && (first || rs.next())) {
      first = false;

      if(!doBreak)
      {
        toggle = !toggle;
      }

      print("<TR>");
      for (int i = 1; i <= colcount; i++)
      {
        String output = rs.getString(i);
        if(output == null)
        {
          output = "{NULL}";
        }
        else if (output.trim().equals(""))
        {
          output="&nbsp;";
        }
        else
        {
          //Trim any non-printable characters from the string
          output = output.replaceAll("[\\u0000-\\u001F]|[\\u0080-\\uFFFF]+", "");
        }

        if (i <= numRepeatColumnsToSurpress)
        {
          assert repeats != null;
          if (repeats[i - 1].equals(output))
          {
            //Suppress the repeat if we're not changing the background color
            if(!doBreak)
            {
              output = "&nbsp;";
            }
          }
          else
          {
            //Save the value to compare to the next row.
            repeats[i - 1] = output;
            if ((i == 1) && doBreak && (rows >= 0))
            {
              toggle = !toggle;
            }
          }
        }

        String rowDataTag;

        // Alternate background colors
        if (toggle)
        {
          rowDataTag = "<TD BGCOLOR=\""+rowcolor1+"\">";
        }
        else
        {
          rowDataTag = "<TD BGCOLOR=\""+rowcolor2+"\">";
        }
        println(rowDataTag);
        if(rsmd.getColumnLabel(i).equals(regexCols))
        {

          output = output.replaceAll(regexValue,regexReplace);
        }
        print(output);
        println("</TD>");
      }
      println("</TR>");
      rows++;
    }

    println("</TABLE>\n</BODY>\n</HTML>");

  }

  // -------------------------------------------------------------------------------------------------------

  private String[] getRepeatArray() {
    int size = getInteger(Option.norepeats);

    if (size <= 0) {
      return null;
    }

    String[] repeats = new String[size];

    for (int i = 0; i < size; i++) {
      repeats[i] = "";
    }

    return repeats;
  }


  private int getDisplayColumnWidth(final ResultSetMetaData rsmd, final int i)
          throws SQLException
  {
    final int maxWidth = getInteger(Option.maxwidth);

    int totalLen = rsmd.getColumnDisplaySize(i);
    int labelLen = rsmd.getColumnLabel(i).length();


    if ( rsmd.getColumnType(i) == Types.TIMESTAMP) { totalLen = 20; } // DMO ???
    if ( rsmd.getColumnType(i) == Types.DATE) { totalLen = 19; } // DMO ???

    if (totalLen > maxWidth)
    {
      totalLen = maxWidth;
    }

    // If the label itself is bigger than what we've been told (or computed), use the label size
    if (labelLen > totalLen)
    {
      totalLen = labelLen;
    }

    return totalLen;
  }

  // -------------------------------------------------------------------------------------------------------

  private void print(final String s)
  {
    outputBuffer += s;
  }

  private void println(final String s)
  {
    os.println(outputBuffer + s);
    outputBuffer = "";
  }
}
