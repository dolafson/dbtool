package com.vikinghelmet.dbtool.input;

public class QueryParameter {
    Class clazz; // String, Integer, Double, ... default = String
    String name; // optional

    public QueryParameter(Class clazz, String name) {
        this.clazz = clazz;
        this.name = name;
    }

    public QueryParameter(Class clazz) {
        this(clazz,null);
    }

    public Class getClazz() {
        return clazz;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class guessClazz(String value) {
        if (value.matches("^(true|false)$")) {
            this.clazz = Boolean.class;
        }
        else if (value.matches("^[0-9]+$")) {
            this.clazz = Integer.class;
        }
        else if (value.matches("^[0-9.]+$")) {
            this.clazz = Double.class;
        }
        else this.clazz = String.class;
        return this.clazz;
    }

    public String getNamedValue() {
        if (name == null) return null;
        String value = System.getProperty(name);
        return value != null ? value : System.getenv(name);
    }

    @Override
    public String toString() {
        return "QueryParameter{" +
                "clazz=" + clazz +
                ", name='" + name + '\'' +
                '}';
    }
}
