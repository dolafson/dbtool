package com.vikinghelmet.dbtool.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QueryTest {

  @BeforeEach
  public void setUp() {
    Configuration.init(new String[]{""});
  }

  @Test
  public void testExtractParametersFromQuery() throws Exception {
    Query query = new Query("select * from users where id = ?i and percent > ?d{percent}");
    List<QueryParameter> params = query.getParameterList();

//    System.out.println("params = "+params);
//    System.out.println("query = "+query.getQuery());

    assertEquals(Integer.class, params.get(0).getClazz());
    assertNull(params.get(0).getName());

    assertEquals(Double.class, params.get(1).getClazz());
    assertEquals("percent", params.get(1).getName());

    assertEquals("select * from users where id = ? and percent > ?", query.getQuery());
  }

  @Test
  public void testGuessClazz() throws Exception {
    assertEquals (Boolean.class, new QueryParameter(null).guessClazz("true"));
    assertEquals (Boolean.class, new QueryParameter(null).guessClazz("false"));
    assertEquals (Double.class,  new QueryParameter(null).guessClazz("1.3"));
    assertEquals (Integer.class, new QueryParameter(null).guessClazz("123"));
    assertEquals (String.class,  new QueryParameter(null).guessClazz("abc"));
  }

  @Test
  public void testNamedValue() throws Exception {
    assertEquals ("/",  new QueryParameter(String.class, "file.separator").getNamedValue()); // for macos & linux
  }

}
