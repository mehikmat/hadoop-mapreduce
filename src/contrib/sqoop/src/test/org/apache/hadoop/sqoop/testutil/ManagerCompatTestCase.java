/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.sqoop.testutil;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;

import org.apache.hadoop.sqoop.SqoopOptions;
import org.apache.hadoop.sqoop.Sqoop;
import org.apache.hadoop.sqoop.SqoopOptions.InvalidOptionsException;
import org.apache.hadoop.sqoop.orm.CompilationManager;
import org.apache.hadoop.sqoop.util.ClassLoaderStack;

import org.junit.Test;

/**
 * Class that implements common tests that should be applied to all jdbc
 * drivers that we want to interop with.
 *
 * The purpose of these tests is to ensure that if a database supports a
 * given data type, we can import this data type into Sqoop. The test is
 * not intended to check whether all data types are supported by all
 * databases, nor that the representation of a given data type has a canonical
 * representation after being imported. Some databases may not support certain
 * data types, and the format of the imported data may vary from database to
 * database. It is not Sqoop's goal to resolve inter-database differences.
 * However, if a database provides a particular type, we should verify that
 * we can import this data in some form into HDFS.
 *
 * This test battery subjects a database to a variety of import tasks. Many
 * adapter methods are provided to allow subclasses to modify the exact type
 * names injected, expected output values, etc., to account for inter-database
 * discrepencies.
 *
 * Each subclass of this class should test a single ConnManager implementation.
 * Subclasses must implement all abstract methods of this class. They may
 * also wish to override several members of the class hierarchy above this.
 * In particular:
 *
 * String getConnectString() -- Return the connect string to use to get the db.
 * void dropTableIfExists(tableName) -- how to drop a table that may not exist.
 * void createTableWithColTypes() -- how to create a table with a set of cols.
 * Configuration getConf() -- specifies config properties specific to a test.
 * SqoopOptions getSqoopOptions(conf) -- Instantiates the SqoopOptions to use. 
 * List&lt;String&gt; getExtraArgs() -- specifies extra argv elements.
 */
public abstract class ManagerCompatTestCase extends ImportJobTestCase {

  public Log LOG;

  public ManagerCompatTestCase() {
    this.LOG = LogFactory.getLog(ManagerCompatTestCase.class.getName());
  }

  /**
   * @return the Log object to use for reporting during this test
   */
  protected abstract Log getLogger();

  /**
   * @return a "friendly" name for the database. e.g "mysql" or "oracle".
   */
  protected abstract String getDbFriendlyName();

  /** Set to true during tearDown() if a test is skipped. */
  protected boolean skipped;

  @Override
  protected String getTablePrefix() {
    return "MGR_" + getDbFriendlyName().toUpperCase() + "_";
  }

  @Override
  protected boolean useHsqldbTestServer() {
    // Compat tests, by default, do not use hsqldb.
    return false;
  }

  @Override
  public void setUp() {
    LOG = getLogger();
    skipped = false;
    super.setUp();
  }

  @Override
  public void tearDown() {
    try {
      // Clean up the database on our way out.
      dropTableIfExists(getTableName());
    } catch (SQLException e) {
      LOG.warn("Error trying to drop table '" + getTableName()
          + "' on tearDown: " + e);
    }
    super.tearDown();
  }

  //////// These methods indicate whether certain datatypes are supported
  //////// by the underlying database.

  /** @return true if the database under test has a BOOLEAN type */
  protected boolean supportsBoolean() {
    return true;
  }

  /** @return true if the database under test has a BIGINT type */
  protected boolean supportsBigInt() {
    return true;
  }

  /** @return true if the database under test has a TINYINT type */
  protected boolean supportsTinyInt() {
    return true;
  }

  /** @return true if the database under test has a LONGVARCHAR type */
  protected boolean supportsLongVarChar() {
    return true;
  }

  /** @return true if the database under test has a TIME type */
  protected boolean supportsTime() {
    return true;
  }

  //////// These methods indicate how to define various datatypes.

  /**
   * Define a NUMERIC type that can handle 30 digits total, and 5
   * digits to the right of the decimal point.
   */
  protected String getNumericType() {
    return "NUMERIC(" + getNumericScale() + ", "
        + getNumericDecPartDigits() + ")";
  }

  protected String getDecimalType() {
    return "DECIMAL(" + getDecimalScale() + ", "
        + getDecimalDecPartDigits() + ")";
  }

  /**
   * Return the number of digits to use in the integral part of a
   * NUMERIC type
   */
  protected int getNumericScale() {
    return 30;
  }

  /**
   * Return the number of digits to use in the decimal part of a
   * NUMERIC type
   */
  protected int getNumericDecPartDigits() {
    return 5;
  }

  /**
   * Return the number of digits to use in the integral part of a
   * DECIMAL type
   */
  protected int getDecimalScale() {
    return 30;
  }

  /**
   * Return the number of digits to use in the decimal part of a
   * DECIMAL type
   */
  protected int getDecimalDecPartDigits() {
    return 5;
  }

  /**
   * Define a DOUBLE column.
   */
  protected String getDoubleType() {
    return "DOUBLE";
  }

  /**
   * Define a LONGVARCHAR type that can handle at least 24 characters.
   */
  protected String getLongVarCharType() {
    return "LONGVARCHAR";
  }

  /**
   * Define a TIMESTAMP type that can handle null values.
   */
  protected String getTimestampType() {
    return "TIMESTAMP";
  }

  //////// These methods indicate how databases respond to various datatypes.
  //////// Since our comparisons are all string-based, these return strings.

  /** @return How a BOOLEAN column with value TRUE is communicated over JDBC */
  protected String getTrueBoolDbOutput() {
    return "true";
  }

  /** @return How a BOOLEAN column with value TRUE is represented in a seq-file
   * import. */
  protected String getTrueBoolSeqOutput() {
    return "true";
  }

  /** @return How a BOOLEAN column with value FALSE is communicated over JDBC */
  protected String getFalseBoolDbOutput() {
    return "false";
  }

  /** @return How a BOOLEAN column with value FALSE is represented in a seq-file
   * import. */
  protected String getFalseBoolSeqOutput() {
    return "false";
  }

  /**
   * helper method: return a floating-point string in the same way
   * it was entered, but integers get a trailing '.0' attached.
   */
  protected String withDecimalZero(String floatingPointStr) {
    if (floatingPointStr.indexOf(".") == -1) {
      return floatingPointStr + ".0";
    } else {
      return floatingPointStr;
    }
  }

  /**
   * A real value inserted as '40' may be returned as '40', '40.', or '40.0',
   * etc. Given a string that defines how a real value is inserted, determine
   * how it is returned.
   *
   * @param realAsInserted the string we used in the SQL INSERT statement
   * @return how the string version of this as returned by the database is
   * represented.
   */
  protected String getRealDbOutput(String realAsInserted) {
    return withDecimalZero(realAsInserted);
  }

  /**
   * @return how a given real value is represented in an imported sequence
   * file
   */
  protected String getRealSeqOutput(String realAsInserted) {
    return getRealDbOutput(realAsInserted);
  }

  /**
   * A float value inserted as '40' may be returned as '40', '40.', or '40.0',
   * etc. Given a string that defines how a float value is inserted, determine
   * how it is returned.
   *
   * @param floatAsInserted the string we used in the SQL INSERT statement
   * @return how the string version of this as returned by the database is
   * represented.
   */
  protected String getFloatDbOutput(String floatAsInserted) {
    return withDecimalZero(floatAsInserted);
  }

  protected String getFloatSeqOutput(String floatAsInserted) {
    return getFloatDbOutput(floatAsInserted);
  }

  /**
   * A double value inserted as '40' may be returned as '40', '40.', or '40.0',
   * etc. Given a string that defines how a double value is inserted, determine
   * how it is returned.
   *
   * @param doubleAsInserted the string we used in the SQL INSERT statement
   * @return how the string version of this as returned by the database is
   * represented.
   */
  protected String getDoubleDbOutput(String doubleAsInserted) {
    return withDecimalZero(doubleAsInserted);
  }

  protected String getDoubleSeqOutput(String doubleAsInserted) {
    return getDoubleDbOutput(doubleAsInserted);
  }

  /**
   * Some databases require that we insert dates using a special format.
   * This takes the canonical string used to insert a DATE into a table,
   * and specializes it to the SQL dialect used by the database under
   * test.
   */
  protected String getDateInsertStr(String insertStr) {
    return insertStr;
  }

  /**
   * Some databases require that we insert times using a special format.
   * This takes the canonical string used to insert a TIME into a table,
   * and specializes it to the SQL dialect used by the database under
   * test.
   */
  protected String getTimeInsertStr(String insertStr) {
    return insertStr;
  }

  /**
   * Some databases require that we insert timestamps using a special format.
   * This takes the canonical string used to insert a TIMESTAMP into a table,
   * and specializes it to the SQL dialect used by the database under
   * test.
   */
  protected String getTimestampInsertStr(String insertStr) {
    return insertStr;
  }

  protected String getDateDbOutput(String dateAsInserted) {
    return dateAsInserted;
  }

  protected String getDateSeqOutput(String dateAsInserted) {
    return dateAsInserted;
  }

  /**
   * Convert an input timestamp to the string representation of the timestamp
   * returned by a database select query.
   *
   * @param tsAsInserted the input timestamp
   * @return the string version of this as returned by the database is
   * represented.
   */
  protected String getTimestampDbOutput(String tsAsInserted) {
    if ("null".equals(tsAsInserted)) {
      return tsAsInserted;
    }

    int dotPos = tsAsInserted.indexOf(".");
    if (-1 == dotPos) {
      // No dot in the original string; expand to 9 places.
      return tsAsInserted + ".000000000";
    } else {
      // Default with a dot is to pad the nanoseconds column to 9 places.
      int numZerosNeeded = tsAsInserted.length() - dotPos;
      String zeros = "";
      for (int i = 0; i < numZerosNeeded; i++) {
        zeros = zeros + "0";
      }

      return tsAsInserted + zeros;
    }
  }

  /**
   * Convert an input timestamp to the string representation of the timestamp
   * returned by a sequencefile-based import.
   *
   * @param tsAsInserted the input timestamp
   * @return the string version of this as returned by the database is
   * represented.
   */
  protected String getTimestampSeqOutput(String tsAsInserted) {
    if ("null".equals(tsAsInserted)) {
      return tsAsInserted;
    }

    int dotPos = tsAsInserted.indexOf(".");
    if (-1 == dotPos) {
      // No dot in the original string; expand to add a single item after the dot.
      return tsAsInserted + ".0";
    } else {
      // all other strings return as-is.
      return tsAsInserted;
    }
  }

  protected String getNumericDbOutput(String numAsInserted) {
    return numAsInserted;
  }

  protected String getNumericSeqOutput(String numAsInserted) {
    return getNumericDbOutput(numAsInserted);
  }

  protected String getDecimalDbOutput(String numAsInserted) {
    return numAsInserted;
  }

  protected String getDecimalSeqOutput(String numAsInserted) {
    return getDecimalDbOutput(numAsInserted);
  }

  /**
   * @return how a CHAR(fieldWidth) field is returned by the database
   * for a given input.
   */
  protected String getFixedCharDbOut(int fieldWidth, String asInserted) {
    return asInserted;
  }

  protected String getFixedCharSeqOut(int fieldWidth, String asInserted) {
    return asInserted;
  }

  //////// The actual tests occur below here. ////////

  /**
   * Do a full verification test on the singleton value of a given type.
   * @param colType  The SQL type to instantiate the column.
   * @param insertVal The SQL text to insert a value into the database.
   * @param returnVal The string representation of the value as extracted
   *        from the db.
   */
  protected void verifyType(String colType, String insertVal,
      String returnVal) {
    verifyType(colType, insertVal, returnVal, returnVal);
  }

  /**
   * Do a full verification test on the singleton value of a given type.
   * @param colType  The SQL type to instantiate the column.
   * @param insertVal The SQL text to insert a value into the database.
   * @param returnVal The string representation of the value as extracted from
   *        the db.
   * @param seqFileVal The string representation of the value as extracted
   *        through the DBInputFormat, serialized, and injected into a
   *        SequenceFile and put through toString(). This may be slightly
   *        different than what ResultSet.getString() returns, which is used
   *        by returnVal.
   */
  protected void verifyType(String colType, String insertVal, String returnVal,
      String seqFileVal) {
    createTableForColType(colType, insertVal);
    verifyReadback(1, returnVal);
    verifyImport(seqFileVal, null);
  }

  static final String STRING_VAL_IN = "'this is a short string'";
  static final String STRING_VAL_OUT = "this is a short string";

  @Test
  public void testStringCol1() {
    verifyType("VARCHAR(32)", STRING_VAL_IN, STRING_VAL_OUT);
  }

  @Test
  public void testStringCol2() {
    verifyType("CHAR(32)", STRING_VAL_IN,
        getFixedCharDbOut(32, STRING_VAL_OUT),
        getFixedCharSeqOut(32, STRING_VAL_OUT));
  }

  @Test
  public void testEmptyStringCol() {
    verifyType("VARCHAR(32)", "''", "");
  }

  @Test
  public void testNullStringCol() {
    verifyType("VARCHAR(32)", "NULL", null);
  }

  @Test
  public void testInt() {
    verifyType("INTEGER", "42", "42");
  }

  @Test
  public void testNullInt() {
    verifyType("INTEGER", "NULL", null);
  }

  @Test
  public void testBoolean() {
    if (!supportsBoolean()) {
      LOG.info("Skipping boolean test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("BOOLEAN", "1", getTrueBoolDbOutput(), getTrueBoolSeqOutput());
  }

  @Test
  public void testBoolean2() {
    if (!supportsBoolean()) {
      LOG.info("Skipping boolean test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("BOOLEAN", "0", getFalseBoolDbOutput(), getFalseBoolSeqOutput());
  }

  @Test
  public void testBoolean3() {
    if (!supportsBoolean()) {
      LOG.info("Skipping boolean test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("BOOLEAN", "false", getFalseBoolDbOutput(), getFalseBoolSeqOutput());
  }

  @Test
  public void testTinyInt1() {
    if (!supportsTinyInt()) {
      LOG.info("Skipping tinyint test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("TINYINT", "0", "0");
  }

  @Test
  public void testTinyInt2() {
    if (!supportsTinyInt()) {
      LOG.info("Skipping tinyint test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("TINYINT", "42", "42");
  }

  @Test
  public void testSmallInt1() {
    verifyType("SMALLINT", "-1024", "-1024");
  }

  @Test
  public void testSmallInt2() {
    verifyType("SMALLINT", "2048", "2048");
  }

  @Test
  public void testBigInt1() {
    if (!supportsBigInt()) {
      LOG.info("Skipping bigint test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("BIGINT", "10000000000", "10000000000");
  }


  @Test
  public void testReal1() {
    verifyType("REAL", "256", getRealDbOutput("256"), getRealSeqOutput("256"));
  }

  @Test
  public void testReal2() {
    verifyType("REAL", "256.45", getRealDbOutput("256.45"),
        getRealSeqOutput("256.45"));
  }

  @Test
  public void testFloat1() {
    verifyType("FLOAT", "256", getFloatDbOutput("256"),
        getFloatSeqOutput("256"));
  }

  @Test
  public void testFloat2() {
    verifyType("FLOAT", "256.5", getFloatDbOutput("256.5"),
        getFloatSeqOutput("256.5"));
  }

  @Test
  public void testDouble1() {
    verifyType(getDoubleType(), "-256", getDoubleDbOutput("-256"),
        getDoubleSeqOutput("-256"));
  }

  @Test
  public void testDouble2() {
    verifyType(getDoubleType(), "256.45", getDoubleDbOutput("256.45"),
        getDoubleSeqOutput("256.45"));
  }

  @Test
  public void testDate1() {
    verifyType("DATE", getDateInsertStr("'2009-1-12'"),
        getDateDbOutput("2009-01-12"),
        getDateSeqOutput("2009-01-12"));
  }

  @Test
  public void testDate2() {
    verifyType("DATE", getDateInsertStr("'2009-01-12'"),
        getDateDbOutput("2009-01-12"),
        getDateSeqOutput("2009-01-12"));
  }

  @Test
  public void testDate3() {
    verifyType("DATE", getDateInsertStr("'2009-04-24'"),
        getDateDbOutput("2009-04-24"),
        getDateSeqOutput("2009-04-24"));
  }

  @Test
  public void testTime1() {
    if (!supportsTime()) {
      LOG.info("Skipping time test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("TIME", getTimeInsertStr("'12:24:00'"), "12:24:00");
  }

  @Test
  public void testTime2() {
    if (!supportsTime()) {
      LOG.info("Skipping time test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("TIME", getTimeInsertStr("'06:24:00'"), "06:24:00");
  }

  @Test
  public void testTime3() {
    if (!supportsTime()) {
      LOG.info("Skipping time test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("TIME", getTimeInsertStr("'6:24:00'"), "06:24:00");
  }

  @Test
  public void testTime4() {
    if (!supportsTime()) {
      LOG.info("Skipping time test (unsupported)");
      skipped = true;
      return;
    }
    verifyType("TIME", getTimeInsertStr("'18:24:00'"), "18:24:00");
  }

  @Test
  public void testTimestamp1() {
    verifyType(getTimestampType(),
        getTimestampInsertStr("'2009-04-24 18:24:00'"),
        getTimestampDbOutput("2009-04-24 18:24:00"),
        getTimestampSeqOutput("2009-04-24 18:24:00"));
  }

  @Test
  public void testTimestamp2() {
    try {
      LOG.debug("Beginning testTimestamp2");
      verifyType(getTimestampType(),
          getTimestampInsertStr("'2009-04-24 18:24:00.0002'"),
          getTimestampDbOutput("2009-04-24 18:24:00.0002"),
          getTimestampSeqOutput("2009-04-24 18:24:00.0002"));
    } finally {
      LOG.debug("End testTimestamp2");
    }
  }

  @Test
  public void testTimestamp3() {
    try {
      LOG.debug("Beginning testTimestamp3");
      verifyType(getTimestampType(), "null", null);
    } finally {
      LOG.debug("End testTimestamp3");
    }
  }

  @Test
  public void testNumeric1() {
    verifyType(getNumericType(), "1",
        getNumericDbOutput("1"),
        getNumericSeqOutput("1"));
  }

  @Test
  public void testNumeric2() {
    verifyType(getNumericType(), "-10",
        getNumericDbOutput("-10"),
        getNumericSeqOutput("-10"));
  }

  @Test
  public void testNumeric3() {
    verifyType(getNumericType(), "3.14159",
        getNumericDbOutput("3.14159"),
        getNumericSeqOutput("3.14159"));
  }

  @Test
  public void testNumeric4() {
    verifyType(getNumericType(),
        "3000000000000000000.14159",
        getNumericDbOutput("3000000000000000000.14159"),
        getNumericSeqOutput("3000000000000000000.14159"));
  }

  @Test
  public void testNumeric5() {
    verifyType(getNumericType(),
        "99999999999999999999.14159",
        getNumericDbOutput("99999999999999999999.14159"),
        getNumericSeqOutput("99999999999999999999.14159"));

  }

  @Test
  public void testNumeric6() {
    verifyType(getNumericType(),
        "-99999999999999999999.14159",
        getNumericDbOutput("-99999999999999999999.14159"),
        getNumericSeqOutput("-99999999999999999999.14159"));
  }

  @Test
  public void testDecimal1() {
    verifyType(getDecimalType(), "1",
        getDecimalDbOutput("1"),
        getDecimalSeqOutput("1"));
  }

  @Test
  public void testDecimal2() {
    verifyType(getDecimalType(), "-10",
        getDecimalDbOutput("-10"),
        getDecimalSeqOutput("-10"));
  }

  @Test
  public void testDecimal3() {
    verifyType(getDecimalType(), "3.14159",
        getDecimalDbOutput("3.14159"),
        getDecimalSeqOutput("3.14159"));
  }

  @Test
  public void testDecimal4() {
    verifyType(getDecimalType(),
        "3000000000000000000.14159",
        getDecimalDbOutput("3000000000000000000.14159"),
        getDecimalSeqOutput("3000000000000000000.14159"));
  }

  @Test
  public void testDecimal5() {
    verifyType(getDecimalType(),
        "99999999999999999999.14159",
        getDecimalDbOutput("99999999999999999999.14159"),
        getDecimalSeqOutput("99999999999999999999.14159"));
  }

  @Test
  public void testDecimal6() {
    verifyType(getDecimalType(),
        "-99999999999999999999.14159",
        getDecimalDbOutput("-99999999999999999999.14159"),
        getDecimalSeqOutput("-99999999999999999999.14159"));
  }

  @Test
  public void testLongVarChar() {
    if (!supportsLongVarChar()) {
      LOG.info("Skipping long varchar test (unsupported)");
      skipped = true;
      return;
    }
    verifyType(getLongVarCharType(),
        "'this is a long varchar'",
        "this is a long varchar");
  }

}
