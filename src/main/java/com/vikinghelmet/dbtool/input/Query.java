package com.vikinghelmet.dbtool.input;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vikinghelmet.dbtool.dbtool.debug;

public class Query {
    final static String PARAMETER_PATTERN = "(\\?[bdis]?)(\\{[A-Za-z]*})?";

    String query;

    public Query(String query) {
        this.query = query;
    }

    public String getQuery() {
        String queryString = query;

        debug("before replace, query = " +queryString);

        queryString = queryString.replaceAll(PARAMETER_PATTERN,"?");

        debug("after replace, query = " +queryString);
        return queryString;
    }

    // TODO: support cases where '?' is part of a value (not a placeholder)
    public List<QueryParameter> getParameterList() {
        List<QueryParameter> fieldTypes = new ArrayList<>();

        Pattern pattern = Pattern.compile(PARAMETER_PATTERN);
        Matcher matcher = pattern.matcher(query);

        while (matcher.find()) {
            QueryParameter param = switch (matcher.group(1)) {
                case "?b" -> new QueryParameter(Boolean.class);
                case "?d" -> new QueryParameter(Double.class);
                case "?i" -> new QueryParameter(Integer.class);
                case "?s" -> new QueryParameter(String.class);
                default -> new QueryParameter(null);
            };

            fieldTypes.add(param);

            for (int i=0; i<=matcher.groupCount(); i++) {
                debug("group["+i+"] = "+matcher.group(i));
            }

            if (matcher.groupCount() > 1) {
                param.setName (matcher.group(2) == null ? null : matcher.group(2).replaceAll("[{}]",""));
            }

            debug("param = "+param);
        }

        return fieldTypes;
    }
}
