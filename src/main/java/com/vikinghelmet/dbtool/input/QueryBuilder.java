package com.vikinghelmet.dbtool.input;

import com.vikinghelmet.dbtool.output.DatabaseMetaDataViewer;
import com.vikinghelmet.dbtool.dbtool;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.vikinghelmet.dbtool.input.Configuration.*;
import static com.vikinghelmet.dbtool.dbtool.debug;
import static com.vikinghelmet.dbtool.dbtool.error;

public class QueryBuilder {

    public static List<Query> build(String args[]) throws IOException, SQLException {
        List<Query> result = new ArrayList<>();

        String queryFilename = getProperty (Option.query);

        if (queryFilename != null) {
            result = readQueryFile(queryFilename);
        }
        else if (args.length > 0) {
            String lastArg = args[args.length - 1];

            if (lastArg.contains(" ") || lastArg.startsWith("-")) {
                result = Collections.singletonList(new Query(lastArg));
            }
        }
        else if (isEnabled(Option.infer)) {
            result = inferQueryFromInputCSV();
        }

        return result;
    }

    private static List<Query> readQueryFile(String fixFileName) throws IOException
    {
        BufferedReader fr = new BufferedReader(new FileReader(fixFileName));
        List<Query> queries = new ArrayList<>();

        String nextLine;
        String statement = "";

        while ((nextLine = fr.readLine()) != null)
        {
            nextLine = nextLine.replaceAll("--.*","");  // remove end-of-line comments while building query
            statement += " " + nextLine;

            if (nextLine.endsWith(";")) {
                queries.add(new Query(statement.trim().replaceAll(";$","")));
                statement = "";
            }
        }

        if (! statement.isEmpty()) {
            queries.add (new Query(statement.trim()));
        }

        return queries;
    }

    private static List<Query> inferQueryFromInputCSV() throws SQLException, IOException
    {
        List<Query> result = new ArrayList<>();
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

        Connection conn = dbtool.getConnection();
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

            result.add(new Query(createStmt));
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

        result.add(new Query(insertStmt));
        return result;
    }

    private static List<String> getColumns (String inputFilename) throws IOException {
        InputStreamReader is = inputFilename.equals("-") ?
                new InputStreamReader (System.in) :
                new InputStreamReader (new FileInputStream(inputFilename));

        ICsvListReader reader = new CsvListReader(is, CsvPreference.STANDARD_PREFERENCE);
        return reader.read();
    }
}
