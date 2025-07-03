package com.vikinghelmet.dbtool;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: dolafson
 * Date: Jul 22, 2008
 * Time: 10:12:55 AM
 * To change this template use File | Settings | File Templates.
 */
public class Configuration {
  private static Properties props;

  public static void init (String args[]) {
    props = new Properties();

    String homeDir = ""+System.getenv("HOME");

    load(props, "/etc/dbtool.properties");
    load(props, "./dbtool.properties");
    load(props, homeDir+"/.dbtool/dbtool.properties");
    load(props, homeDir+"/.dbtool/user.properties");

    Properties tmpProps = new Properties();

    for (String arg : args) {
      if (arg.contains("=")) {
        String argument[] = arg.split("=", 2);
        props.setProperty(argument[0], argument[1]);
        tmpProps.setProperty(argument[0], argument[1]);
      }
    }

    if(isEnabled(Option.formatted)) {
      if (tmpProps.contains(""+Option.delim)) setProperty(Option.delim, " ");
    }

    // dbtool.println("tmpProps: ");
    // System.out.println(tmpProps);

    if (isEnabled(Option.human)) {
      if (tmpProps.getProperty(""+Option.headers) == null)    enable(Option.headers);
      if (tmpProps.getProperty(""+Option.footer) == null)     enable(Option.footer);
      if (tmpProps.getProperty(""+Option.formatted) == null)  enable(Option.formatted);
      if (tmpProps.getProperty(""+Option.delim) == null)      setProperty(Option.delim, " ");
    }

    if (isEnabled(Option.dump)) {
      System.out.println(props);
    }
  }

  public static Boolean isEnabled(Option opt) {
    String value = getProperty(opt);
    if (StringUtils.isBlank(value)) {
      return false;
    }
    char c = value.charAt(0);
    return (c == 'Y' || c == 'y' || c == 'T' || c == 't'); // "yes" or "true"
  }

  public static void enable(Option opt) {
    if (isEnabled(Option.verbose)) {
      System.out.println("enable: "+opt);
    }
    props.setProperty(opt.getKey(), "true");
  }

  public static void disable(Option opt) {
    if (isEnabled(Option.verbose)) {
      System.out.println("enable: "+opt);
    }
    props.setProperty(opt.getKey(), "false");
  }

  public static String getProperty(String key) {
    String value = null;
    String node = props.getProperty("node");

    if (node != null) {
      value = props.getProperty(key+"."+node);
    }

    return (value != null) ? value : props.getProperty(key);
  }

  public static String getProperty(Option opt) {
    String value = getProperty(opt.getKey());
    return (value != null) ? value : opt.getDefaultValue();
  }

  public static void setProperty(Option opt, String value) {
    props.setProperty(opt.getKey(), value);
  }

  public static Integer getInteger(Option opt) {
    return Integer.valueOf(getProperty(opt));
  }

  public static List<String> getStringList(Option opt) {
    return Arrays.asList(getProperty(opt).split(","));
  }

  // -------------------------------------------------------------------------------

  private static void load(Properties p, String file) {
    try {
      p.load(new FileInputStream(file));
    }
    catch (IOException e) {
    }
  }

  public static List<String> getNodes() 
  {
    String key = Option.jdbc_url.getKey();
    List<String> result = new ArrayList<String>();
    Enumeration<?> names = props.propertyNames();

    while (names.hasMoreElements()) {
        String name = ""+names.nextElement();
        //System.out.println ("key = "+key+", name = "+name);

        if (name.startsWith (key) && name.length() > key.length()) {
            result.add(name.substring(key.length()+1));
        }
    }
    return result;
  }
}
