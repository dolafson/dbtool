package com.vikinghelmet.dbtool;

public class Parameter {
    Class clazz; // String, Integer, Double, ... default = String
    String name; // optional

    public Parameter(Class clazz, String name) {
        this.clazz = clazz;
        this.name = name;
    }

    public Parameter(Class clazz) {
        this(clazz,null);
    }

    public Class getClazz() {
        return clazz;
    }

    public String getName() {
        return name;
    }
}
