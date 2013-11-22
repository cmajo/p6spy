/*
 * #%L
 * P6Spy
 * %%
 * Copyright (C) 2013 P6Spy
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.p6spy.engine.spy;

import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.test.P6TestFramework;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class P6TestStatement extends P6TestFramework {

  public P6TestStatement(String db) throws SQLException, IOException {
    super(db);
  }

  @Test
  public void testQueryUpdate() throws SQLException {
    Statement statement = null;

    try {
      ResultSet rs;

      // test a basic insert
      String update = "insert into customers(name,id) values ('bob', 100)";
      statement = connection.createStatement();

      // as executeUpdate Javadocs say:
      // Returns either
      // (1) the row count for SQL Data Manipulation Language (DML) statements or
      // (2) 0 for SQL statements that return nothing
      //
      // most of the drivers return != 0, except SQLite, that returns == 0
      // for the SQLite calling: rs = statement.getResultSet() fails with:
      // SQLException statement is not executing getResultset on insert
      //
      // => let's check for the result and handle correctly
      boolean noResult = 0 != statement.executeUpdate(update);
      assertTrue(super.getLastLogEntry().contains(update));

      // most of drivers
      assertTrue("neither no result indicated, nor statement is not null", noResult || statement.getResultSet() == null);

      // test a basic select
      String query = "select count(*) from customers where id=100";
      rs = statement.executeQuery(query);
      assertTrue(super.getLastLogEntry().contains(query));
      rs.next();
      assertEquals(1, rs.getInt(1));
      rs.close();

      try {
        // test batch inserts
        update = "insert into customers(name,id) values ('jim', 101)";
        statement.addBatch(update);
        update = "insert into customers(name,id) values ('billy', 102)";
        statement.addBatch(update);
        update = "insert into customers(name,id) values ('bambi', 103)";
        statement.addBatch(update);
        statement.executeBatch();
        assertTrue(super.getLastLogEntry().contains(update));

        query = "select count(*) from customers where id >= 100";
        rs = statement.executeQuery(query);
        rs.next();
        assertEquals(4, rs.getInt(1));
        rs.close();
      } catch (Exception e) {
        // you may not be able to execute this Prepared & Callable, so
        // this is an okay error, but only this!
        assertTrue(e.getMessage().indexOf("Unsupported feature") != -1);
      }

    } finally {
      if (statement != null) {
        statement.close();
      }
    }
  }

  @Test
  public void testExecutionThreshold() throws SQLException {
    Statement statement = connection.createStatement();

    try {
      // set the execution threshold very low
      P6LogOptions.getActiveInstance().setExecutionThreshold("0");

      // test a basic select
      String query = "select count(*) from customers";
      ResultSet rs = statement.executeQuery(query);
      assertTrue(super.getLastLogEntry().contains(query));
      // finally just make sure the query executed!
      rs.next();
      assertTrue(rs.getInt(1) > 0);
      rs.close();

      // now increase the execution threshold and make sure the query is not captured
      P6LogOptions.getActiveInstance().setExecutionThreshold("10000");

      // test a basic select
      String nextQuery = "select count(*) from customers where 1 = 2";
      rs = statement.executeQuery(nextQuery);
      // make sure the previous query is still the last query
      assertTrue(super.getLastLogEntry().contains(query));
      // and of course that the new query isn't
      assertFalse(super.getLastLogEntry().contains(nextQuery));
      // finally just make sure the query executed!
      rs.next();
      assertEquals(0, rs.getInt(1));
      rs.close();

      P6LogOptions.getActiveInstance().setExecutionThreshold("0");

      // finally, just make sure it now works as expected
      rs = statement.executeQuery(nextQuery);
      assertTrue(super.getLastLogEntry().contains(nextQuery));
      rs.next();
      assertEquals(0, rs.getInt(1));
      rs.close();
    } finally {
      if (statement != null) {
        statement.close();
      }
    }
  }

}
