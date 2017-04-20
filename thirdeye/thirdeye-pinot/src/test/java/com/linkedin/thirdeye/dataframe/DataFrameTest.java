package com.linkedin.thirdeye.dataframe;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


public class DataFrameTest {
  final static double COMPARE_DOUBLE_DELTA = 0.001;

  final static long[] INDEX = new long[] { -1, 1, -2, 4, 3 };
  final static double[] VALUES_DOUBLE = new double[] { -2.1, -0.1, 0.0, 0.5, 1.3 };
  final static long[] VALUES_LONG = new long[] { -2, 1, 0, 1, 2 };
  final static String[] VALUES_STRING = new String[] { "-2.3", "-1", "0.0", "0.5", "0.13e1" };
  final static byte[] VALUES_BOOLEAN = new byte[] { 1, 1, 0, 1, 1 };

  // TODO test double batch function
  // TODO test string batch function
  // TODO test boolean batch function

  // TODO string test head, tail, accessors
  // TODO boolean test head, tail, accessors

  // TODO shift double, long, boolean
  // TODO fill double, long, boolean

  DataFrame df;

  @BeforeMethod
  public void before() {
    df = new DataFrame(INDEX)
        .addSeries("double", VALUES_DOUBLE)
        .addSeries("long", VALUES_LONG)
        .addSeries("string", VALUES_STRING)
        .addSeries("boolean", VALUES_BOOLEAN);
  }

  @Test
  public void testEnforceSeriesLengthPass() {
    df.addSeries("series", VALUES_DOUBLE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testEnforceSeriesLengthFail() {
    df.addSeries("series", 0.1, 3.2);
  }

  @Test
  public void testSeriesName() {
    df.addSeries("ab", VALUES_DOUBLE);
    df.addSeries("_a", VALUES_DOUBLE);
    df.addSeries("a1", VALUES_DOUBLE);
  }

  @Test
  public void testChainedEqualsSeparate() {
    DataFrame dfChained = new DataFrame()
        .addSeries("test", 1, 2, 3)
        .addSeries("drop", 1, 2, 3)
        .renameSeries("test", "checkme")
        .dropSeries("drop");

    DataFrame dfSeparate = new DataFrame();
    dfSeparate.addSeries("test", 1, 2, 3);
    dfSeparate.addSeries("drop", 1, 2, 3);
    dfSeparate.renameSeries("test", "checkme");
    dfSeparate.dropSeries("drop");

    Assert.assertEquals(dfChained.getSeriesNames().size(), 1);
    Assert.assertEquals(dfSeparate.getSeriesNames().size(), 1);
    Assert.assertEquals(dfChained, dfSeparate);
  }

  @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "testSeriesNameFailProvider")
  public void testSeriesNameFail(String name) {
    df.addSeries(name, VALUES_DOUBLE);
  }

  @DataProvider(name = "testSeriesNameFailProvider")
  public Object[][] testSeriesNameFailProvider() {
    return new Object[][] { { null }, { "" }, { "1a" }, { "a,b" }, { "a-b" }, { "a+b" }, { "a*b" }, { "a/b" }, { "a=b" }, { "a>b" } };
  }

  @Test
  public void testIndexColumn() {
    DataFrame dfEmpty = new DataFrame();
    Assert.assertTrue(dfEmpty.getSeriesNames().isEmpty());

    DataFrame dfIndexRange = new DataFrame(0);
    Assert.assertEquals(dfIndexRange.getSeriesNames(), Collections.singleton("index"));
  }

  @Test
  public void testDoubleNoDataDuplication() {
    DoubleSeries first = DataFrame.toSeries(VALUES_DOUBLE);
    DoubleSeries second = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertSame(first.values(), second.values());
  }

  @Test
  public void testDoubleToDouble() {
    Series s = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertEquals(s.getDoubles().values(), VALUES_DOUBLE);
  }

  @Test
  public void testDoubleToLong() {
    Series s = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertEquals(s.getLongs().values(), new long[] { -2, 0, 0, 0, 1 });
  }

  @Test
  public void testDoubleToBoolean() {
    Series s = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, 1, 0, 1, 1 });
  }

  @Test
  public void testDoubleToString() {
    Series s = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertEquals(s.getStrings().values(), new String[] { "-2.1", "-0.1", "0.0", "0.5", "1.3" });
  }

  @Test
  public void testLongToDouble() {
    Series s = DataFrame.toSeries(VALUES_LONG);
    Assert.assertEquals(s.getDoubles().values(), new double[] { -2.0, 1.0, 0.0, 1.0, 2.0 });
  }

  @Test
  public void testLongToLong() {
    Series s = DataFrame.toSeries(VALUES_LONG);
    Assert.assertEquals(s.getLongs().values(), VALUES_LONG);
  }

  @Test
  public void testLongToBoolean() {
    Series s = DataFrame.toSeries(VALUES_LONG);
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, 1, 0, 1, 1 });
  }

  @Test
  public void testLongToString() {
    Series s = DataFrame.toSeries(VALUES_LONG);
    Assert.assertEquals(s.getStrings().values(), new String[] { "-2", "1", "0", "1", "2" });
  }

  @Test
  public void testBooleanToDouble() {
    Series s = DataFrame.toSeries(VALUES_BOOLEAN);
    Assert.assertEquals(s.getDoubles().values(), new double[] { 1.0, 1.0, 0.0, 1.0, 1.0 });
  }

  @Test
  public void testBooleanToLong() {
    Series s = DataFrame.toSeries(VALUES_BOOLEAN);
    Assert.assertEquals(s.getLongs().values(), new long[] { 1, 1, 0, 1, 1 });
  }

  @Test
  public void testBooleanToBoolean() {
    Series s = DataFrame.toSeries(VALUES_BOOLEAN);
    Assert.assertEquals(s.getBooleans().values(), VALUES_BOOLEAN);
  }

  @Test
  public void testBooleanToString() {
    Series s = DataFrame.toSeries(VALUES_BOOLEAN);
    Assert.assertEquals(s.getStrings().values(), new String[] { "true", "true", "false", "true", "true" });
  }

  @Test
  public void testStringToDouble() {
    Series s = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s.getDoubles().values(), new double[] { -2.3, -1.0, 0.0, 0.5, 1.3 });
  }

  @Test
  public void testStringToDoubleNulls() {
    Series s = DataFrame.toSeries("", null, "-2.1e1");
    Assert.assertEquals(s.getDoubles().values(), new double[] { DoubleSeries.NULL, DoubleSeries.NULL, -21.0d });
  }

  @Test
  public void testStringToLong() {
    // NOTE: transparent conversion via double
    Series s = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s.getLongs().values(), new long[] { -2, -1, 0, 0, 1 });
  }

  @Test
  public void testStringToLongNulls() {
    // NOTE: transparent conversion via double
    Series s = DataFrame.toSeries("", null, "-1.0");
    Assert.assertEquals(s.getLongs().values(), new long[] { LongSeries.NULL, LongSeries.NULL, -1 });
  }

  @Test
  public void testStringToBoolean() {
    // NOTE: transparent conversion via double
    Series s = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, 1, 0, 1, 1 });
  }

  @Test
  public void testStringToBooleanNulls() {
    // NOTE: transparent conversion via double
    Series s = DataFrame.toSeries("", null, "true");
    Assert.assertEquals(s.getBooleans().values(), new byte[] { BooleanSeries.NULL, BooleanSeries.NULL, 1 });
  }

  @Test
  public void testStringToString() {
    Series s = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s.getStrings().values(), VALUES_STRING);
  }

  @Test
  public void testDoubleBuilderNull() {
    Assert.assertEquals(DoubleSeries.builder().addValues((Double)null).build().values(), new double[] { DoubleSeries.NULL});
  }

  @Test
  public void testLongBuilderNull() {
    Assert.assertEquals(LongSeries.builder().addValues((Long)null).build().values(), new long[] { LongSeries.NULL});
  }

  @Test
  public void testStringBuilderNull() {
    Assert.assertEquals(StringSeries.builder().addValues((String)null).build().values(), new String[] { StringSeries.NULL});
  }

  @Test
  public void testBooleanBuilderNull() {
    Assert.assertEquals(BooleanSeries.builder().addValues((Byte)null).build().values(), new byte[] { BooleanSeries.NULL});
  }

  @Test
  public void testBooleanBuilderNullBoolean() {
    Assert.assertEquals(BooleanSeries.builder().addBooleanValues((Boolean)null).build().values(), new byte[] { BooleanSeries.NULL});
  }

  @Test
  public void testDoubleNull() {
    Series s = DataFrame.toSeries(1.0, DoubleSeries.NULL, 2.0);
    Assert.assertEquals(s.getDoubles().values(), new double[] { 1.0, DoubleSeries.NULL, 2.0 });
    Assert.assertEquals(s.getLongs().values(), new long[] { 1, LongSeries.NULL, 2 });
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, BooleanSeries.NULL, 1 });
    Assert.assertEquals(s.getStrings().values(), new String[] { "1.0", StringSeries.NULL, "2.0" });
  }

  @Test
  public void testLongNull() {
    Series s = DataFrame.toSeries(1, LongSeries.NULL, 2);
    Assert.assertEquals(s.getDoubles().values(), new double[] { 1.0, DoubleSeries.NULL, 2.0 });
    Assert.assertEquals(s.getLongs().values(), new long[] { 1, LongSeries.NULL, 2 });
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, BooleanSeries.NULL, 1 });
    Assert.assertEquals(s.getStrings().values(), new String[] { "1", StringSeries.NULL, "2" });
  }

  @Test
  public void testBooleanNull() {
    Series s = DataFrame.toSeries(new byte[] { 1, BooleanSeries.NULL, 0 });
    Assert.assertEquals(s.getDoubles().values(), new double[] { 1.0, DoubleSeries.NULL, 0.0 });
    Assert.assertEquals(s.getLongs().values(), new long[] { 1, LongSeries.NULL, 0 });
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, BooleanSeries.NULL, 0 });
    Assert.assertEquals(s.getStrings().values(), new String[] { "true", StringSeries.NULL, "false" });
  }

  @Test
  public void testStringNull() {
    Series s = DataFrame.toSeries("1.0", StringSeries.NULL, "2.0");
    Assert.assertEquals(s.getDoubles().values(), new double[] { 1.0, DoubleSeries.NULL, 2.0 });
    Assert.assertEquals(s.getLongs().values(), new long[] { 1, LongSeries.NULL, 2 });
    Assert.assertEquals(s.getBooleans().values(), new byte[] { 1, BooleanSeries.NULL, 1 });
    Assert.assertEquals(s.getStrings().values(), new String[] { "1.0", StringSeries.NULL, "2.0" });
  }

  @Test
  public void testDoubleInfinity() {
    Series s = DataFrame.toSeries(DoubleSeries.POSITIVE_INFINITY, DoubleSeries.NEGATIVE_INFINITY);
    Assert.assertEquals(s.getDoubles().values(), new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY });
    Assert.assertEquals(s.getLongs().values(), new long[] { LongSeries.MAX_VALUE, LongSeries.MIN_VALUE });
    Assert.assertEquals(s.getBooleans().values(), new byte[] { BooleanSeries.TRUE, BooleanSeries.TRUE });
    Assert.assertEquals(s.getStrings().values(), new String[] { "Infinity", "-Infinity" });

    Assert.assertEquals(DataFrame.toSeries("Infinity", "-Infinity").getDoubles().values(), new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY });
  }

  @Test
  public void testMapDoubleToDouble() {
    DoubleSeries in = DataFrame.toSeries(VALUES_DOUBLE);
    DoubleSeries out = in.map(new DoubleSeries.DoubleFunction() {
      public double apply(double... values) {
        return values[0] * 2;
      }
    });
    Assert.assertEquals(out.values(), new double[] { -4.2, -0.2, 0.0, 1.0, 2.6 });
  }

  @Test
  public void testMapDoubleToBoolean() {
    DoubleSeries in = DataFrame.toSeries(VALUES_DOUBLE);
    BooleanSeries out = in.map(new DoubleSeries.DoubleConditional() {
      public boolean apply(double... values) {
        return values[0] <= 0.3;
      }
    });
    Assert.assertEquals(out.values(), new byte[] { 1, 1, 1, 0, 0 });
  }

  @Test
  public void testMapDataFrameAsDouble() {
    DoubleSeries out = df.map(new Series.DoubleFunction() {
      public double apply(double[] values) {
        return values[0] + values[1] + values[2];
      }
    }, "long", "string", "boolean");
    Assert.assertEquals(out.values(), new double[] { -3.3, 1.0, 0.0, 2.5, 4.3 });
  }

  @Test
  public void testOverrideWithGeneratedSeries() {
    DoubleSeries out = df.getDoubles("double").map(new DoubleSeries.DoubleFunction() {
      public double apply(double... values) {
        return values[0] * 2;
      }
    });
    df = df.addSeries("double", out);
    Assert.assertEquals(df.getDoubles("double").values(), new double[] { -4.2, -0.2, 0.0, 1.0, 2.6 });
  }

  @Test
  public void testSortDouble() {
    DoubleSeries in = DataFrame.toSeries(3, 1.5, 1.3, 5, 1.9, DoubleSeries.NULL);
    Assert.assertEquals(in.sorted().values(), new double[] { DoubleSeries.NULL, 1.3, 1.5, 1.9, 3, 5 });
  }

  @Test
  public void testSortLong() {
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19, LongSeries.NULL);
    Assert.assertEquals(in.sorted().values(), new long[] { LongSeries.NULL, 3, 5, 13, 15, 19 });
  }

  @Test
  public void testSortString() {
    StringSeries in = DataFrame.toSeries("b", "a", "ba", "ab", "aa", StringSeries.NULL);
    Assert.assertEquals(in.sorted().values(), new String[] { StringSeries.NULL, "a", "aa", "ab", "b", "ba" });
  }

  @Test
  public void testSortBoolean() {
    BooleanSeries in = DataFrame.toSeries(new byte[] { 1, 0, 0, 1, 0, BooleanSeries.NULL});
    Assert.assertEquals(in.sorted().values(), new byte[] { BooleanSeries.NULL, 0, 0, 0, 1, 1 });
  }

  @Test
  public void testProject() {
    int[] fromIndex = new int[] { 1, -1, 4, 0 };
    DataFrame ndf = df.project(fromIndex);
    Assert.assertEquals(ndf.getLongs("index").values(), new long[] { 1, LongSeries.NULL, 3, -1 });
    Assert.assertEquals(ndf.getDoubles("double").values(), new double[] { -0.1, DoubleSeries.NULL, 1.3, -2.1 });
    Assert.assertEquals(ndf.getLongs("long").values(), new long[] { 1, LongSeries.NULL, 2, -2 });
    Assert.assertEquals(ndf.getStrings("string").values(), new String[] { "-1", StringSeries.NULL, "0.13e1", "-2.3" });
    Assert.assertEquals(ndf.getBooleans("boolean").values(), new byte[] { 1, BooleanSeries.NULL, 1, 1 });
  }

  @Test
  public void testSortByIndex() {
    df = df.sortedBy("index");
    // NOTE: internal logic uses reorder() for all sorting
    Assert.assertEquals(df.getLongs("index").values(), new long[] { -2, -1, 1, 3, 4 });
    Assert.assertEquals(df.getDoubles("double").values(), new double[] { 0.0, -2.1, -0.1, 1.3, 0.5 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { 0, -2, 1, 2, 1 });
    Assert.assertEquals(df.getStrings("string").values(), new String[] { "0.0", "-2.3", "-1", "0.13e1", "0.5" });
    Assert.assertEquals(df.getBooleans("boolean").values(), new byte[] { 0, 1, 1, 1, 1 });
  }

  @Test
  public void testSortByDouble() {
    df = df.addSeries("myseries", 0.1, -2.1, 3.3, 4.6, -7.8 );
    df = df.sortedBy("myseries");
    Assert.assertEquals(df.getLongs("index").values(), new long[] { 3, 1, -1, -2, 4 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { 2, 1, -2, 0, 1 });
  }

  @Test
  public void testSortByLong() {
    df = df.addSeries("myseries", 1, -21, 33, 46, -78 );
    df = df.sortedBy("myseries");
    Assert.assertEquals(df.getLongs("index").values(), new long[] { 3, 1, -1, -2, 4 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { 2, 1, -2, 0, 1 });
  }

  @Test
  public void testSortByString() {
    df = df.addSeries("myseries", "b", "aa", "bb", "c", "a" );
    df = df.sortedBy("myseries");
    Assert.assertEquals(df.getLongs("index").values(), new long[] { 3, 1, -1, -2, 4 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { 2, 1, -2, 0, 1 });
  }

  @Test
  public void testSortByBoolean() {
    // NOTE: boolean sorted should be stable
    df = df.addSeries("myseries", true, true, false, false, true );
    df = df.sortedBy("myseries");
    Assert.assertEquals(df.getLongs("index").values(), new long[] { -2, 4, -1, 1, 3 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { 0, 1, -2, 1, 2 });
  }

  @Test
  public void testReverse() {
    // NOTE: uses separate reverse() implementation by each series
    df = df.reverse();
    Assert.assertEquals(df.getLongs("index").values(), new long[] { 3, 4, -2, 1, -1 });
    Assert.assertEquals(df.getDoubles("double").values(), new double[] { 1.3, 0.5, 0.0, -0.1, -2.1 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { 2, 1, 0, 1, -2 });
    Assert.assertEquals(df.getStrings("string").values(), new String[] { "0.13e1", "0.5", "0.0", "-1", "-2.3" });
    Assert.assertEquals(df.getBooleans("boolean").values(), new byte[] { 1, 1, 0, 1, 1 });
  }

  @Test
  public void testAppendLongDouble() {
    Series s = df.get("long").append(df.get("double"));
    Assert.assertEquals(s.type(), Series.SeriesType.LONG);
    Assert.assertEquals(s.getLongs().values(), new long[] { -2, 1, 0, 1, 2, -2, 0, 0, 0, 1 });
  }

  @Test
  public void testAppendLongBoolean() {
    Series s = df.get("long").append(df.get("boolean"));
    Assert.assertEquals(s.type(), Series.SeriesType.LONG);
    Assert.assertEquals(s.getLongs().values(), new long[] { -2, 1, 0, 1, 2, 1, 1, 0, 1, 1 });
  }

  @Test
  public void testAppendLongString() {
    Series s = df.get("long").append(df.get("string"));
    Assert.assertEquals(s.type(), Series.SeriesType.LONG);
    Assert.assertEquals(s.getLongs().values(), new long[] { -2, 1, 0, 1, 2, -2, -1, 0, 0, 1 });
  }

  @Test
  public void testLongGroupByIntervalEmpty() {
    Assert.assertTrue(DataFrame.toSeries(new long[0]).groupByInterval(1).isEmpty());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testLongGroupByIntervalFailZero() {
    DataFrame.toSeries(-1).groupByInterval(0);
  }

  @Test
  public void testLongGroupByInterval() {
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19, 20);
    Series.SeriesGrouping grouping = in.groupByInterval(4);

    Assert.assertEquals(grouping.size(), 6);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 3 });
    Assert.assertEquals(grouping.buckets.get(2).fromIndex, new int[] {});
    Assert.assertEquals(grouping.buckets.get(3).fromIndex, new int[] { 1, 2 });
    Assert.assertEquals(grouping.buckets.get(4).fromIndex, new int[] { 4 });
    Assert.assertEquals(grouping.buckets.get(5).fromIndex, new int[] { 5 });
  }

  @Test
  public void testLongGroupByCountEmpty() {
    Assert.assertTrue(DataFrame.toSeries(new long[0]).groupByCount(1).isEmpty());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testLongGroupByCountFailZero() {
    DataFrame.toSeries(-1).groupByCount(0);
  }

  @Test
  public void testLongGroupByCountAligned() {
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19, 20);
    Series.SeriesGrouping grouping = in.groupByCount(3);

    Assert.assertEquals(grouping.size(), 2);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0, 1, 2 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 3, 4, 5 });
  }

  @Test
  public void testLongBucketsByCountUnaligned() {
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19, 11, 12, 9);
    Series.SeriesGrouping grouping = in.groupByCount(3);

    Assert.assertEquals(grouping.size(), 3);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0, 1, 2 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 3, 4, 5 });
    Assert.assertEquals(grouping.buckets.get(2).fromIndex, new int[] { 6, 7 });
  }

  @Test
  public void testLongGroupByPartitionsEmpty() {
    Assert.assertTrue(DataFrame.toSeries(new long[0]).groupByPartitions(1).isEmpty());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testLongGroupByPartitionsFailZero() {
    DataFrame.toSeries(-1).groupByPartitions(0);
  }

  @Test
  public void testLongGroupByPartitionsAligned() {
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19, 20, 5, 5, 8, 1);
    Series.SeriesGrouping grouping = in.groupByPartitions(5);

    Assert.assertEquals(grouping.size(), 5);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0, 1 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 2, 3 });
    Assert.assertEquals(grouping.buckets.get(2).fromIndex, new int[] { 4, 5 });
    Assert.assertEquals(grouping.buckets.get(3).fromIndex, new int[] { 6, 7 });
    Assert.assertEquals(grouping.buckets.get(4).fromIndex, new int[] { 8, 9 });
  }

  @Test
  public void testLongGroupByPartitionsUnaligned() {
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19, 20, 5, 5, 8, 1);
    Series.SeriesGrouping grouping = in.groupByPartitions(3);

    Assert.assertEquals(grouping.size(), 3);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0, 1, 2 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 3, 4, 5, 6 });
    Assert.assertEquals(grouping.buckets.get(2).fromIndex, new int[] { 7, 8, 9 });
  }

  @Test
  public void testLongGroupByPartitionsUnalignedSmall() {
    LongSeries in = DataFrame.toSeries(3, 15, 1);
    Series.SeriesGrouping grouping = in.groupByPartitions(7);

    Assert.assertEquals(grouping.size(), 7);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] {});
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 0 });
    Assert.assertEquals(grouping.buckets.get(2).fromIndex, new int[] {});
    Assert.assertEquals(grouping.buckets.get(3).fromIndex, new int[] { 1 });
    Assert.assertEquals(grouping.buckets.get(4).fromIndex, new int[] {});
    Assert.assertEquals(grouping.buckets.get(5).fromIndex, new int[] { 2 });
    Assert.assertEquals(grouping.buckets.get(6).fromIndex, new int[] {});
  }

  @Test
  public void testLongGroupByValueEmpty() {
    Assert.assertTrue(DataFrame.toSeries(new long[0]).groupByValue().isEmpty());
  }

  @Test
  public void testLongGroupByValue() {
    LongSeries in = DataFrame.toSeries(3, 4, 5, 5, 3, 1, 5);
    Series.SeriesGrouping grouping = in.groupByValue();

    Assert.assertEquals(grouping.size(), 4);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 5 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 0, 4 });
    Assert.assertEquals(grouping.buckets.get(2).fromIndex, new int[] { 1 });
    Assert.assertEquals(grouping.buckets.get(3).fromIndex, new int[] { 2, 3, 6 });
  }

  @Test
  public void testBooleanGroupByValueEmpty() {
    Assert.assertTrue(DataFrame.toSeries(new boolean[0]).groupByValue().isEmpty());
  }

  @Test
  public void testBooleanGroupByValue() {
    BooleanSeries in = DataFrame.toSeries(true, false, false, true, false, true, false);
    Series.SeriesGrouping grouping = in.groupByValue();

    Assert.assertEquals(grouping.size(), 2);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 1, 2, 4, 6 });
    Assert.assertEquals(grouping.buckets.get(1).fromIndex, new int[] { 0, 3, 5 });
  }

  @Test
  public void testBooleanGroupByValueTrueOnly() {
    BooleanSeries in = DataFrame.toSeries(true, true, true);
    Series.SeriesGrouping grouping = in.groupByValue();

    Assert.assertEquals(grouping.size(), 1);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0, 1, 2 });
  }

  @Test
  public void testBooleanGroupByValueFalseOnly() {
    BooleanSeries in = DataFrame.toSeries(false, false, false);
    Series.SeriesGrouping grouping = in.groupByValue();

    Assert.assertEquals(grouping.size(), 1);
    Assert.assertEquals(grouping.buckets.get(0).fromIndex, new int[] { 0, 1, 2 });
  }

  @Test
  public void testLongAggregateSum() {
    Series keys = DataFrame.toSeries(3, 5, 7);
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19 );
    List<Series.Bucket> buckets = new ArrayList<>();
    buckets.add(new Series.Bucket(new int[] { 1, 3, 4 }));
    buckets.add(new Series.Bucket(new int[] {}));
    buckets.add(new Series.Bucket(new int[] { 0, 2 }));

    Series.SeriesGrouping grouping = new Series.SeriesGrouping(keys, in, buckets);

    DataFrame out = grouping.aggregate(new LongSeries.LongSum());
    Assert.assertEquals(out.getLongs("key").values(), new long[] { 3, 5, 7 });
    Assert.assertEquals(out.getLongs("value").values(), new long[] { 39, LongSeries.NULL, 16 });
  }

  @Test
  public void testLongAggregateLast() {
    Series keys = DataFrame.toSeries(3, 5, 7);
    LongSeries in = DataFrame.toSeries(3, 15, 13, 5, 19 );
    List<Series.Bucket> buckets = new ArrayList<>();
    buckets.add(new Series.Bucket(new int[] { 1, 3, 4 }));
    buckets.add(new Series.Bucket(new int[] {}));
    buckets.add(new Series.Bucket(new int[] { 0, 2 }));

    Series.SeriesGrouping grouping = new Series.SeriesGrouping(keys, in, buckets);

    DataFrame out = grouping.aggregate(new LongSeries.LongLast());
    Assert.assertEquals(out.getLongs("key").values(), new long[] { 3, 5, 7 });
    Assert.assertEquals(out.getLongs("value").values(), new long[] { 19, LongSeries.NULL, 13 });
  }

  @Test
  public void testLongGroupByAggregateEndToEnd() {
    LongSeries in = DataFrame.toSeries(0, 3, 12, 2, 4, 8, 5, 1, 7, 9, 6, 10, 11);
    Series.SeriesGrouping grouping = in.groupByInterval(4);
    Assert.assertEquals(grouping.size(), 4);

    DataFrame out = grouping.aggregate(new LongSeries.LongSum());
    Assert.assertEquals(out.getLongs("key").values(), new long[] { 0, 4, 8, 12 });
    Assert.assertEquals(out.getLongs("value").values(), new long[] { 6, 22, 38, 12 });
  }

  @Test
  public void testAggregateWithoutData() {
    DoubleSeries s = DataFrame.toSeries(new double[0]);
    Assert.assertEquals(s.sum(), DoubleSeries.NULL);
  }

  @Test
  public void testDoubleAggregateWithNull() {
    DoubleSeries s = DataFrame.toSeries(1.0, 2.0, DoubleSeries.NULL, 4.0);
    Assert.assertEquals(s.sum(), DoubleSeries.NULL);
    Assert.assertEquals(s.fillNull().sum(), 7.0);
    Assert.assertEquals(s.dropNull().sum(), 7.0);
  }

  @Test
  public void testLongAggregateWithNull() {
    LongSeries s = DataFrame.toSeries(1, 2, LongSeries.NULL, 4);
    Assert.assertEquals(s.sum(), LongSeries.NULL);
    Assert.assertEquals(s.fillNull().sum(), 7);
    Assert.assertEquals(s.dropNull().sum(), 7);
  }

  @Test
  public void testStringAggregateWithNull() {
    StringSeries s = DataFrame.toSeries("a", "b", StringSeries.NULL, "d");
    Assert.assertEquals(s.join(), StringSeries.NULL);
    Assert.assertEquals(s.fillNull().join(), "abd");
    Assert.assertEquals(s.dropNull().join(), "abd");
  }

  @Test
  public void testBooleanAggregateWithNull() {
    BooleanSeries s = DataFrame.toSeries(new byte[] { 1, 0, BooleanSeries.NULL, 1 });
    Assert.assertEquals(s.aggregate(BooleanSeries.HAS_TRUE).value(), BooleanSeries.NULL);
    Assert.assertEquals(s.fillNull().aggregate(BooleanSeries.HAS_TRUE).value(), 1);
    Assert.assertEquals(s.dropNull().aggregate(BooleanSeries.HAS_TRUE).value(), 1);
  }

  @Test
  public void testDataFrameGroupBy() {
    DataFrame.DataFrameGrouping grouping = df.groupBy("boolean");
    DoubleSeries ds = grouping.aggregate("double", new DoubleSeries.DoubleSum()).getDoubles("double");
    assertEquals(ds, 0.0, -0.4);

    LongSeries ls = grouping.aggregate("long", new LongSeries.LongSum()).get("long").getLongs();
    Assert.assertEquals(ls.values(), new long[] { 0, 2 });

    StringSeries ss = grouping.aggregate("string", new StringSeries.StringConcat("|")).get("string").getStrings();
    Assert.assertEquals(ss.values(), new String[] { "0.0", "-2.3|-1|0.5|0.13e1" });
  }

  @Test
  public void testResampleEndToEnd() {
    df = df.resampledBy("index", 2, new DataFrame.ResampleLast());

    Assert.assertEquals(df.size(), 4);
    Assert.assertEquals(df.getSeriesNames().size(), 5);

    Assert.assertEquals(df.getLongs("index").values(), new long[] { -2, 0, 2, 4 });
    Assert.assertEquals(df.getDoubles("double").values(), new double[] { -2.1, -0.1, 1.3, 0.5 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { -2, 1, 2, 1 });
    Assert.assertEquals(df.getStrings("string").values(), new String[] { "-2.3", "-1", "0.13e1", "0.5" });
    Assert.assertEquals(df.getBooleans("boolean").values(), new byte[] { 1, 1, 1, 1 });
  }

  @Test
  public void testStableMultiSortDoubleLong() {
    DataFrame mydf = new DataFrame(new long[] { 1, 2, 3, 4, 5, 6, 7, 8 })
        .addSeries("double", 1.0, 1.0, 2.0, 2.0, 1.0, 1.0, 2.0, 2.0)
        .addSeries("long", 2, 2, 2, 2, 1, 1, 1, 1);

    DataFrame sdfa = mydf.sortedBy("double", "long");
    Assert.assertEquals(sdfa.getLongs("index").values(), new long[] { 5, 6, 1, 2, 7, 8, 3, 4 });

    DataFrame sdfb = mydf.sortedBy("long", "double");
    Assert.assertEquals(sdfb.getLongs("index").values(), new long[] { 3, 4, 7, 8, 1, 2, 5, 6 });
  }

  @Test
  public void testStableMultiSortStringBoolean() {
    DataFrame mydf = new DataFrame(new long[] { 1, 2, 3, 4, 5, 6, 7, 8 })
        .addSeries("string", "a", "a", "b", "b", "a", "a", "b", "b")
        .addSeries("boolean", true, true, true, true, false, false, false, false);

    DataFrame sdfa = mydf.sortedBy("string", "boolean");
    Assert.assertEquals(sdfa.getLongs("index").values(), new long[] { 5, 6, 1, 2, 7, 8, 3, 4 });

    DataFrame sdfb = mydf.sortedBy("boolean", "string");
    Assert.assertEquals(sdfb.getLongs("index").values(), new long[] { 3, 4, 7, 8, 1, 2, 5, 6 });
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testFilterUnequalLengthFail() {
    df.filter(DataFrame.toSeries(false, true));
  }

  @Test
  public void testFilter() {
    df = df.filter(DataFrame.toSeries(true, false, true, true, false));

    Assert.assertEquals(df.size(), 3);
    Assert.assertEquals(df.getLongs("index").values(), new long[] { -1, -2, 4 });
    Assert.assertEquals(df.getDoubles("double").values(), new double[] { -2.1, 0.0, 0.5 });
    Assert.assertEquals(df.getLongs("long").values(), new long[] { -2, 0, 1 });
    Assert.assertEquals(df.getStrings("string").values(), new String[] { "-2.3", "0.0", "0.5"  });
    Assert.assertEquals(df.getBooleans("boolean").values(), new byte[] { 1, 0, 1 });
  }

  @Test
  public void testFilterAll() {
    df = df.filter(DataFrame.toSeries(true, true, true, true, true));
    Assert.assertEquals(df.size(), 5);
  }

  @Test
  public void testFilterNone() {
    df = df.filter(DataFrame.toSeries(false, false, false, false, false));
    Assert.assertEquals(df.size(), 0);
  }

  @Test
  public void testFilterNull() {
    df = df.filter(DataFrame.toSeries(new byte[] { BooleanSeries.NULL, 0, 1, BooleanSeries.NULL, 0 }));
    Assert.assertEquals(df.size(), 1);
  }

  @Test
  public void testRenameSeries() {
    df = df.renameSeries("double", "new");

    df.getDoubles("new");

    try {
      df.getDoubles("double");
      Assert.fail();
    } catch(IllegalArgumentException e) {
      // left blank
    }
  }

  @Test
  public void testRenameSeriesOverride() {
    df = df.renameSeries("double", "long");
    Assert.assertEquals(df.getDoubles("long").values(), VALUES_DOUBLE);
  }

  @Test
  public void testContains() {
    Assert.assertTrue(df.contains("double"));
    Assert.assertFalse(df.contains("NOT_VALID"));
  }

  @Test
  public void testCopy() {
    DataFrame ndf = df.copy();

    ndf.getDoubles("double").values()[0] = 100.0;
    Assert.assertNotEquals(df.getDoubles("double").first(), ndf.getDoubles("double").first());

    ndf.getLongs("long").values()[0] = 100;
    Assert.assertNotEquals(df.getLongs("long").first(), ndf.getLongs("long").first());

    ndf.getStrings("string").values()[0] = "other string";
    Assert.assertNotEquals(df.getStrings("string").first(), ndf.getStrings("string").first());

    ndf.getBooleans("boolean").values()[0] = 0;
    Assert.assertNotEquals(df.getBooleans("boolean").first(), ndf.getBooleans("boolean").first());
  }

  @Test
  public void testDoubleHead() {
    DoubleSeries s = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertEquals(s.head(0).values(), new double[]{});
    Assert.assertEquals(s.head(3).values(), Arrays.copyOfRange(VALUES_DOUBLE, 0, 3));
    Assert.assertEquals(s.head(6).values(), Arrays.copyOfRange(VALUES_DOUBLE, 0, 5));
  }

  @Test
  public void testDoubleTail() {
    DoubleSeries s = DataFrame.toSeries(VALUES_DOUBLE);
    Assert.assertEquals(s.tail(0).values(), new double[] {});
    Assert.assertEquals(s.tail(3).values(), Arrays.copyOfRange(VALUES_DOUBLE, 2, 5));
    Assert.assertEquals(s.tail(6).values(), Arrays.copyOfRange(VALUES_DOUBLE, 0, 5));
  }

  @Test
  public void testDoubleAccessorsEmpty() {
    DoubleSeries s = DoubleSeries.empty();
    Assert.assertTrue(DoubleSeries.isNull(s.sum()));
    Assert.assertTrue(DoubleSeries.isNull(s.min()));
    Assert.assertTrue(DoubleSeries.isNull(s.max()));
    Assert.assertTrue(DoubleSeries.isNull(s.mean()));
    Assert.assertTrue(DoubleSeries.isNull(s.std()));

    try {
      s.first();
      Assert.fail();
    } catch(IllegalStateException ignore) {
      // left blank
    }

    try {
      s.last();
      Assert.fail();
    } catch(IllegalStateException ignore) {
      // left blank
    }

    try {
      s.value();
      Assert.fail();
    } catch(IllegalStateException ignore) {
      // left blank
    }
  }

  @Test
  public void testLongHead() {
    LongSeries s = DataFrame.toSeries(VALUES_LONG);
    Assert.assertEquals(s.head(0).values(), new long[] {});
    Assert.assertEquals(s.head(3).values(), Arrays.copyOfRange(VALUES_LONG, 0, 3));
    Assert.assertEquals(s.head(6).values(), Arrays.copyOfRange(VALUES_LONG, 0, 5));
  }

  @Test
  public void testLongTail() {
    LongSeries s = DataFrame.toSeries(VALUES_LONG);
    Assert.assertEquals(s.tail(0).values(), new long[] {});
    Assert.assertEquals(s.tail(3).values(), Arrays.copyOfRange(VALUES_LONG, 2, 5));
    Assert.assertEquals(s.tail(6).values(), Arrays.copyOfRange(VALUES_LONG, 0, 5));
  }

  @Test
  public void testLongAccessorsEmpty() {
    LongSeries s = LongSeries.empty();
    Assert.assertTrue(LongSeries.isNull(s.sum()));
    Assert.assertTrue(LongSeries.isNull(s.min()));
    Assert.assertTrue(LongSeries.isNull(s.max()));

    try {
      s.first();
      Assert.fail();
    } catch(IllegalStateException ignore) {
      // left blank
    }

    try {
      s.last();
      Assert.fail();
    } catch(IllegalStateException ignore) {
      // left blank
    }

    try {
      s.value();
      Assert.fail();
    } catch(IllegalStateException ignore) {
      // left blank
    }
  }

  @Test
  public void testLongUnique() {
    LongSeries s1 = DataFrame.toSeries(new long[] {});
    Assert.assertEquals(s1.unique().values(), new long[] {});

    LongSeries s2 = DataFrame.toSeries(4, 5, 2, 1);
    Assert.assertEquals(s2.unique().values(), new long[] {1, 2, 4, 5});

    LongSeries s3 = DataFrame.toSeries(9, 1, 2, 3, 6, 1, 2, 9, 2, 7);
    Assert.assertEquals(s3.unique().values(), new long[] {1, 2, 3, 6, 7, 9});
  }

  @Test
  public void testDoubleUnique() {
    DoubleSeries s1 = DataFrame.toSeries(new double[] {});
    Assert.assertEquals(s1.unique().values(), new double[] {});

    DoubleSeries s2 = DataFrame.toSeries(4.1, 5.2, 2.3, 1.4);
    Assert.assertEquals(s2.unique().values(), new double[] {1.4, 2.3, 4.1, 5.2});

    DoubleSeries s3 = DataFrame.toSeries(9.0, 1.1, 2.2, 3.0, 6.0, 1.1, 2.3, 9.0, 2.3, 7.0);
    Assert.assertEquals(s3.unique().values(), new double[] {1.1, 2.2, 2.3, 3.0, 6.0, 7.0, 9.0});
  }

  @Test
  public void testStringUnique() {
    StringSeries s1 = DataFrame.toSeries(new String[] {});
    Assert.assertEquals(s1.unique().values(), new String[] {});

    StringSeries s2 = DataFrame.toSeries("a", "A", "b", "Cc");
    Assert.assertEquals(new HashSet<>(s2.unique().toList()), new HashSet<>(Arrays.asList("a", "A", "b", "Cc")));

    StringSeries s3 = DataFrame.toSeries("a", "A", "b", "Cc", "A", "cC", "a", "cC");
    Assert.assertEquals(new HashSet<>(s3.unique().toList()), new HashSet<>(Arrays.asList("a", "A", "b", "Cc", "cC")));
  }

  @Test
  public void testStringFillNull() {
    StringSeries s = DataFrame.toSeries("a", null, null, "b", null);
    Assert.assertEquals(s.fillNull("N").values(), new String[] { "a", "N", "N", "b", "N" });
  }

  @Test
  public void testStringShift() {
    StringSeries s1 = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s1.shift(0).values(), VALUES_STRING);

    StringSeries s2 = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s2.shift(2).values(), new String[] { null, null, "-2.3", "-1", "0.0" });

    StringSeries s3 = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s3.shift(4).values(), new String[] { null, null, null, null, "-2.3" });

    StringSeries s4 = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s4.shift(-4).values(), new String[] { "0.13e1", null, null, null, null });

    StringSeries s5 = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s5.shift(100).values(), new String[] { null, null, null, null, null });

    StringSeries s6 = DataFrame.toSeries(VALUES_STRING);
    Assert.assertEquals(s6.shift(-100).values(), new String[] { null, null, null, null, null });
  }

  @Test
  public void testDoubleMapNullConditional() {
    DoubleSeries in = DataFrame.toSeries(1.0, DoubleSeries.NULL, 2.0);
    BooleanSeries out = in.map(new Series.DoubleConditional() {
      @Override
      public boolean apply(double... values) {
        return true;
      }
    });
    Assert.assertEquals(out.values(), new byte[] { 1, BooleanSeries.NULL, 1 });
  }

  @Test
  public void testLongMapNullConditional() {
    LongSeries in = DataFrame.toSeries(1, LongSeries.NULL, 2);
    BooleanSeries out = in.map(new Series.LongConditional() {
      @Override
      public boolean apply(long... values) {
        return true;
      }
    });
    Assert.assertEquals(out.values(), new byte[] { 1, BooleanSeries.NULL, 1 });
  }

  @Test
  public void testStringMapNullConditional() {
    StringSeries in = DataFrame.toSeries("1.0", StringSeries.NULL, "2.0");
    BooleanSeries out = in.map(new Series.StringConditional() {
      @Override
      public boolean apply(String... values) {
        return true;
      }
    });
    Assert.assertEquals(out.values(), new byte[] { 1, BooleanSeries.NULL, 1 });
  }

  @Test
  public void testDoubleMapNullFunction() {
    DoubleSeries in = DataFrame.toSeries(1.0, DoubleSeries.NULL, 2.0);
    DoubleSeries out = in.map(new DoubleSeries.DoubleFunction() {
      @Override
      public double apply(double... values) {
        return values[0] + 1.0;
      }
    });
    Assert.assertEquals(out.values(), new double[] { 2.0, DoubleSeries.NULL, 3.0 });
  }

  @Test
  public void testLongMapNullFunction() {
    LongSeries in = DataFrame.toSeries(1, LongSeries.NULL, 2);
    LongSeries out = in.map(new LongSeries.LongFunction() {
      @Override
      public long apply(long... values) {
        return values[0] + 1;
      }
    });
    Assert.assertEquals(out.values(), new long[] { 2, LongSeries.NULL, 3 });
  }

  @Test
  public void testStringMapNullFunction() {
    StringSeries in = DataFrame.toSeries("1.0", StringSeries.NULL, "2.0");
    StringSeries out = in.map(new StringSeries.StringFunction() {
      @Override
      public String apply(String... values) {
        return values[0] + "+";
      }
    });
    Assert.assertEquals(out.values(), new String[] { "1.0+", StringSeries.NULL, "2.0+" });
  }

  @Test
  public void testDropNullRows() {
    DataFrame mdf = new DataFrame(new long[] { 1, 2, 3, 4, 5, 6 })
        .addSeries("double", 1.0, 2.0, DoubleSeries.NULL, 4.0, 5.0, 6.0)
        .addSeries("long", LongSeries.NULL, 2, 3, 4, 5, 6)
        .addSeries("string", "1.0", "2", "bbb", "true", StringSeries.NULL, "aaa")
        .addSeries("boolean", true, true, false, false, false, false);

    DataFrame ddf = mdf.dropNull();
    Assert.assertEquals(ddf.size(), 3);
    Assert.assertEquals(ddf.getLongs("index").values(), new long[] { 2, 4, 6 });
    Assert.assertEquals(ddf.getDoubles("double").values(), new double[] { 2.0, 4.0, 6.0 });
    Assert.assertEquals(ddf.getLongs("long").values(), new long[] { 2, 4, 6 });
    Assert.assertEquals(ddf.getStrings("string").values(), new String[] { "2", "true", "aaa" });
    Assert.assertEquals(ddf.getBooleans("boolean").values(), new byte[] { 1, 0, 0 });
  }

  @Test
  public void testDropNullRowsIdentity() {
    Assert.assertEquals(df.dropNull().size(), df.size());
  }

  @Test
  public void testDropNullColumns() {
    DataFrame mdf = new DataFrame()
        .addSeries("double_null", 1.0, 2.0, DoubleSeries.NULL)
        .addSeries("double", 1.0, 2.0, 3.0)
        .addSeries("long_null", LongSeries.NULL, 2, 3)
        .addSeries("long", 1, 2, 3)
        .addSeries("string_null", "true", StringSeries.NULL, "aaa")
        .addSeries("string", "true", "this", "aaa")
        .addSeries("boolean", true, true, false);

    DataFrame ddf = mdf.dropNullColumns();
    Assert.assertEquals(ddf.size(), 3);
    Assert.assertEquals(new HashSet<>(ddf.getSeriesNames()), new HashSet<>(Arrays.asList("double", "long", "string", "boolean")));
  }

  @Test
  public void testMapExpression() {
    DoubleSeries s = df.map("(double * 2 + long + boolean) / 2");
    Assert.assertEquals(s.values(), new double[] { -2.6, 0.9, 0.0, 1.5, 2.8 });
  }

  @Test
  public void testMapExpressionNull() {
    DataFrame mdf = new DataFrame(VALUES_LONG)
        .addSeries("null", 1.0, 1.0, DoubleSeries.NULL, 1.0, 1.0);
    DoubleSeries out = mdf.map("null + 1");
    Assert.assertEquals(out.values(), new double[] { 2.0, 2.0, DoubleSeries.NULL, 2.0, 2.0 });
  }

  @Test
  public void testMapExpressionOtherNullPass() {
    DataFrame mdf = new DataFrame(VALUES_LONG)
        .addSeries("null", 1.0, 1.0, DoubleSeries.NULL, 1.0, 1.0)
        .addSeries("notnull", 1.0, 1.0, 1.0, 1.0, 1.0);
    mdf.map("notnull + 1");
  }

  @Test
  public void testMapExpressionWithNull() {
    DataFrame mdf = new DataFrame(VALUES_LONG)
        .addSeries("null", 1.0, 1.0, DoubleSeries.NULL, 1.0, 1.0);
    DoubleSeries s = mdf.map("null + 1");
    Assert.assertEquals(s.values(), new double[] { 2.0, 2.0, DoubleSeries.NULL, 2.0, 2.0 });
  }

  @Test
  public void testDoubleMovingWindow() {
    DoubleSeries s = DataFrame.toSeries(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
    DoubleSeries out = s.applyMovingWindow(2, 1, new DoubleSeries.DoubleSum());
    Assert.assertEquals(out.values(), new double[] { 1.0, 3.0, 5.0, 7.0, 9.0, 11.0 });
  }

  @Test
  public void testSeriesEquals() {
    Assert.assertTrue(DataFrame.toSeries(0.0, 3.0, 4.0).equals(DataFrame.toSeries(0.0, 3.0, 4.0)));
    Assert.assertTrue(DataFrame.toSeries(0, 3, 4).equals(DataFrame.toSeries(0, 3, 4)));
    Assert.assertTrue(DataFrame.toSeries(false, true, true).equals(DataFrame.toSeries(false, true, true)));
    Assert.assertTrue(DataFrame.toSeries("1", "3", "4").equals(DataFrame.toSeries("1", "3", "4")));

    Assert.assertFalse(DataFrame.toSeries(0.0, 3.0, 4.0).equals(DataFrame.toSeries(0, 3, 4)));
    Assert.assertFalse(DataFrame.toSeries(0, 3, 4).equals(DataFrame.toSeries(0.0, 3.0, 4.0)));
    Assert.assertFalse(DataFrame.toSeries(false, true, true).equals(DataFrame.toSeries("0", "1", "1")));
    Assert.assertFalse(DataFrame.toSeries("1", "3", "4").equals(DataFrame.toSeries(1, 3, 4)));

    Assert.assertTrue(DataFrame.toSeries(0.0, 3.0, 4.0).equals(DataFrame.toSeries(0, 3, 4).getDoubles()));
    Assert.assertTrue(DataFrame.toSeries(0, 3, 4).equals(DataFrame.toSeries(0.0, 3.0, 4.0).getLongs()));
    Assert.assertTrue(DataFrame.toSeries(false, true, true).equals(DataFrame.toSeries("0", "1", "1").getBooleans()));
    Assert.assertTrue(DataFrame.toSeries("1", "3", "4").equals(DataFrame.toSeries(1, 3, 4).getStrings()));
  }

  @Test
  public void testLongJoinInner() {
    Series sLeft = DataFrame.toSeries(4, 3, 1, 2);
    Series sRight = DataFrame.toSeries(5, 4, 3, 3, 0);

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.INNER);

    Assert.assertEquals(pairs.size(), 3);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(1, 2));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(1, 3));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(0, 1));
  }

  @Test
  public void testLongJoinLeft() {
    Series sLeft = DataFrame.toSeries(4, 3, 1, 2);
    Series sRight = DataFrame.toSeries(5, 4, 3, 3, 0);

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.LEFT);

    Assert.assertEquals(pairs.size(), 5);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(2, -1));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(3, -1));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(1, 2));
    Assert.assertEquals(pairs.get(3), new Series.JoinPair(1, 3));
    Assert.assertEquals(pairs.get(4), new Series.JoinPair(0, 1));
  }

  @Test
  public void testLongJoinRight() {
    Series sLeft = DataFrame.toSeries(4, 3, 1, 2);
    Series sRight = DataFrame.toSeries(5, 4, 3, 3, 0);

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.RIGHT);

    Assert.assertEquals(pairs.size(), 5);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(-1, 4));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(1, 2));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(1, 3));
    Assert.assertEquals(pairs.get(3), new Series.JoinPair(0, 1));
    Assert.assertEquals(pairs.get(4), new Series.JoinPair(-1, 0));
  }

  @Test
  public void testLongJoinOuter() {
    Series sLeft = DataFrame.toSeries(4, 3, 1, 2);
    Series sRight = DataFrame.toSeries(5, 4, 3, 3, 0);

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.OUTER);

    Assert.assertEquals(pairs.size(), 7);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(-1, 4));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(2, -1));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(3, -1));
    Assert.assertEquals(pairs.get(3), new Series.JoinPair(1, 2));
    Assert.assertEquals(pairs.get(4), new Series.JoinPair(1, 3));
    Assert.assertEquals(pairs.get(5), new Series.JoinPair(0, 1));
    Assert.assertEquals(pairs.get(6), new Series.JoinPair(-1, 0));
  }

  @Test
  public void testLongDoubleJoinInner() {
    Series sLeft = DataFrame.toSeries(4, 3, 1, 2);
    Series sRight = DataFrame.toSeries(5.0, 4.0, 3.0, 3.0, 0.0);

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.INNER);

    Assert.assertEquals(pairs.size(), 3);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(1, 2));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(1, 3));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(0, 1));
  }

  @Test
  public void testStringJoinInner() {
    Series sLeft = DataFrame.toSeries("4", "3", "1", "2");
    Series sRight = DataFrame.toSeries("5", "4", "3", "3", "0");

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.INNER);

    Assert.assertEquals(pairs.size(), 3);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(1, 2));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(1, 3));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(0, 1));
  }

  @Test
  public void testBooleanJoinInner() {
    Series sLeft = DataFrame.toSeries(true, false, false);
    Series sRight = DataFrame.toSeries(false, true, true);

    List<Series.JoinPair> pairs = sLeft.join(sRight, Series.JoinType.INNER);

    Assert.assertEquals(pairs.size(), 4);
    Assert.assertEquals(pairs.get(0), new Series.JoinPair(1, 0));
    Assert.assertEquals(pairs.get(1), new Series.JoinPair(2, 0));
    Assert.assertEquals(pairs.get(2), new Series.JoinPair(0, 1));
    Assert.assertEquals(pairs.get(3), new Series.JoinPair(0, 2));
  }

  @Test
  public void testJoinInner() {
    DataFrame left = new DataFrame()
        .addSeries("leftKey", 4, 2, 1, 3)
        .addSeries("leftValue", "a", "d", "c", "b");

    DataFrame right = new DataFrame()
        .addSeries("rightKey", 5.0, 2.0, 1.0, 3.0, 1.0, 0.0)
        .addSeries("rightValue", "v", "z", "w", "x", "y", "u");

    DataFrame joined = left.joinInner(right, "leftKey", "rightKey");

    Assert.assertEquals(joined.size(), 4);
    Assert.assertEquals(joined.get("leftKey").type(), Series.SeriesType.LONG);
    Assert.assertEquals(joined.get("leftValue").type(), Series.SeriesType.STRING);
    Assert.assertEquals(joined.get("rightKey").type(), Series.SeriesType.DOUBLE);
    Assert.assertEquals(joined.get("rightValue").type(), Series.SeriesType.STRING);
    Assert.assertEquals(joined.getLongs("leftKey").values(), new long[] { 1, 1, 2, 3 });
    Assert.assertEquals(joined.getDoubles("rightKey").values(), new double[] { 1.0, 1.0, 2.0, 3.0 });
    Assert.assertEquals(joined.getStrings("leftValue").values(), new String[] { "c", "c", "d", "b" });
    Assert.assertEquals(joined.getStrings("rightValue").values(), new String[] { "w", "y", "z", "x" });
  }

  @Test
  public void testJoinOuter() {
    DataFrame left = new DataFrame()
        .addSeries("leftKey", 4, 2, 1, 3)
        .addSeries("leftValue", "a", "d", "c", "b");

    DataFrame right = new DataFrame()
        .addSeries("rightKey", 5.0, 2.0, 1.0, 3.0, 1.0, 0.0)
        .addSeries("rightValue", "v", "z", "w", "x", "y", "u");

    DataFrame joined = left.joinOuter(right, "leftKey", "rightKey");

    Assert.assertEquals(joined.size(), 7);
    Assert.assertEquals(joined.get("leftKey").type(), Series.SeriesType.LONG);
    Assert.assertEquals(joined.get("leftValue").type(), Series.SeriesType.STRING);
    Assert.assertEquals(joined.get("rightKey").type(), Series.SeriesType.DOUBLE);
    Assert.assertEquals(joined.get("rightValue").type(), Series.SeriesType.STRING);
    Assert.assertEquals(joined.getLongs("leftKey").values(), new long[] { LongSeries.NULL, 1, 1, 2, 3, 4, LongSeries.NULL});
    Assert.assertEquals(joined.getDoubles("rightKey").values(), new double[] { 0.0, 1.0, 1.0, 2.0, 3.0, DoubleSeries.NULL, 5.0 });
    Assert.assertEquals(joined.getStrings("leftValue").values(), new String[] { StringSeries.NULL, "c", "c", "d", "b", "a", StringSeries.NULL});
    Assert.assertEquals(joined.getStrings("rightValue").values(), new String[] { "u", "w", "y", "z", "x", StringSeries.NULL, "v" });
  }

  @Test
  public void testJoinSameNameSameContent() {
    DataFrame left = new DataFrame()
        .addSeries("name", 1, 2, 3, 4);

    DataFrame right = new DataFrame()
        .addSeries("name", 3, 4, 5, 6);

    DataFrame df = left.joinInner(right, "name", "name");

    Assert.assertEquals(df.getSeriesNames().size(), 1);
    Assert.assertTrue(df.contains("name"));
    Assert.assertFalse(df.contains("name_right"));
  }

  @Test
  public void testJoinSameNameDifferentContent() {
    DataFrame left = new DataFrame()
        .addSeries("name", 1, 2, 3, 4);

    DataFrame right = new DataFrame()
        .addSeries("name", 3, 4, 5, 6);

    DataFrame df = left.joinOuter(right, "name", "name");

    Assert.assertEquals(df.getSeriesNames().size(), 2);
    Assert.assertTrue(df.contains("name"));
    Assert.assertTrue(df.contains("name_right"));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testJoinIndexFailNoIndex() {
    DataFrame dfIndex = new DataFrame(5);
    DataFrame dfNoIndex = new DataFrame().addSeries(DataFrame.COLUMN_INDEX_DEFAULT, DataFrame.toSeries(VALUES_DOUBLE));
    dfIndex.joinOuter(dfNoIndex);
  }

  @Test
  public void testJoinIndex() {
    DataFrame dfLeft = new DataFrame(5).addSeries("one", 5, 4, 3, 2, 1);
    DataFrame dfRight = new DataFrame(3).addSeries("two", "A", "B", "C");
    DataFrame joined = dfLeft.joinLeft(dfRight);

    Assert.assertEquals(joined.getStrings("one").values(), new String[] {"5", "4", "3", "2", "1"});
    Assert.assertEquals(joined.getStrings("two").values(), new String[] {"A", "B", "C", null, null});
  }

  @Test
  public void testBooleanHasTrueFalseNull() {
    BooleanSeries s1 = DataFrame.toSeries(new boolean[0]);
    Assert.assertFalse(s1.hasFalse());
    Assert.assertFalse(s1.hasTrue());
    Assert.assertFalse(s1.hasNull());

    BooleanSeries s2 = DataFrame.toSeries(true, true);
    Assert.assertFalse(s2.hasFalse());
    Assert.assertTrue(s2.hasTrue());
    Assert.assertFalse(s2.hasNull());

    BooleanSeries s3 = DataFrame.toSeries(false, false);
    Assert.assertTrue(s3.hasFalse());
    Assert.assertFalse(s3.hasTrue());
    Assert.assertFalse(s3.hasNull());

    BooleanSeries s4 = DataFrame.toSeries(true, false);
    Assert.assertTrue(s4.hasFalse());
    Assert.assertTrue(s4.hasTrue());
    Assert.assertFalse(s4.hasNull());

    BooleanSeries s5 = DataFrame.toSeries(new byte[] { 1, 0, BooleanSeries.NULL});
    Assert.assertFalse(s5.hasFalse());
    Assert.assertFalse(s5.hasTrue());
    Assert.assertTrue(s5.hasNull());
  }

  @Test
  public void testBooleanAllTrueFalse() {
    BooleanSeries s1 = BooleanSeries.empty();
    Assert.assertFalse(s1.allTrue());
    Assert.assertFalse(s1.allFalse());

    BooleanSeries s2 = DataFrame.toSeries(true, true);
    Assert.assertFalse(s2.allFalse());
    Assert.assertTrue(s2.allTrue());

    BooleanSeries s3 = DataFrame.toSeries(false, false);
    Assert.assertTrue(s3.allFalse());
    Assert.assertFalse(s3.allTrue());

    BooleanSeries s4 = DataFrame.toSeries(true, false);
    Assert.assertFalse(s4.allFalse());
    Assert.assertFalse(s4.allTrue());

    BooleanSeries s5 = DataFrame.toSeries(new byte[] { 1, 1, BooleanSeries.NULL});
    Assert.assertFalse(s5.allFalse());
    Assert.assertFalse(s5.allTrue());

    BooleanSeries s6 = DataFrame.toSeries(new byte[] { 0, 0, BooleanSeries.NULL});
    Assert.assertFalse(s6.allFalse());
    Assert.assertFalse(s6.allTrue());

    BooleanSeries s7 = DataFrame.toSeries(new byte[] { 1, 0, BooleanSeries.NULL});
    Assert.assertFalse(s7.allFalse());
    Assert.assertFalse(s7.allTrue());
  }

  @Test
  public void testStringInferSeriesTypeDoubleDot() {
    Series.SeriesType t = StringSeries.buildFrom("1", "2", "3.", "", null).inferType();
    Assert.assertEquals(t, Series.SeriesType.DOUBLE);
  }

  @Test
  public void testStringInferSeriesTypeDoubleExp() {
    Series.SeriesType t = StringSeries.buildFrom("1", "2e1", "3", "", null).inferType();
    Assert.assertEquals(t, Series.SeriesType.DOUBLE);
  }

  @Test
  public void testStringInferSeriesTypeLong() {
    Series.SeriesType t = StringSeries.buildFrom("2", "-4", "-0", "", null).inferType();
    Assert.assertEquals(t, Series.SeriesType.LONG);
  }

  @Test
  public void testStringInferSeriesTypeBoolean() {
    Series.SeriesType t = StringSeries.buildFrom("true", "False", "false", "", null).inferType();
    Assert.assertEquals(t, Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testStringInferSeriesTypeString() {
    Series.SeriesType t = StringSeries.buildFrom("true", "", "-0.2e1", null).inferType();
    Assert.assertEquals(t, Series.SeriesType.STRING);
  }

  @Test
  public void testCompareInversion() {
    StringSeries string = StringSeries.buildFrom("0", "", "true");
    BooleanSeries bool = BooleanSeries.buildFrom(new byte[] { 0, BooleanSeries.NULL, 1 });

    Assert.assertTrue(string.compare(bool, 0, 0) < 0); // "0" < "false"
    Assert.assertTrue(bool.compare(string, 0, 0) == 0);

    Assert.assertTrue(string.compare(bool, 1, 1) > 0); // "" > null
    Assert.assertTrue(bool.compare(string, 1, 1) == 0);

    Assert.assertTrue(string.compare(bool, 2, 2) == 0);
    Assert.assertTrue(bool.compare(string, 2, 2) == 0);
  }

  @Test
  public void testDataFrameFromCsv() throws IOException {
    Reader in = new InputStreamReader(this.getClass().getResourceAsStream("test.csv"));
    DataFrame df = DataFrame.fromCsv(in);

    Assert.assertEquals(df.getSeriesNames().size(), 3);
    Assert.assertEquals(df.size(), 6);

    Series a = df.get("header_A");
    Assert.assertEquals(a.type(), Series.SeriesType.STRING);
    Assert.assertEquals(a.getStrings().values(), new String[] { "a1", "A2", "two words", "", "with comma, semicolon; and more", "" });

    Series b = df.get("_1headerb");
    Assert.assertEquals(b.type(), Series.SeriesType.LONG);
    Assert.assertEquals(b.getLongs().values(), new long[] { 1, 2, 3, 4, 5, 6 });

    Series c = df.get("Header_C");
    Assert.assertEquals(c.type(), Series.SeriesType.BOOLEAN);
    Assert.assertEquals(c.getBooleans().values(), new byte[] { BooleanSeries.NULL, 1, 0, 0, BooleanSeries.NULL, 1 });
  }

  @Test
  public void testDoubleFunctionConversion() {
    Series out = df.map(new Series.DoubleFunction() {
      @Override
      public double apply(double... values) {
        return values[0] + 1;
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.DOUBLE);
  }

  @Test
  public void testLongFunctionConversion() {
    Series out = df.map(new Series.LongFunction() {
      @Override
      public long apply(long... values) {
        return values[0] + 1;
      }
    }, "double");
    Assert.assertEquals(out.type(), Series.SeriesType.LONG);
  }

  @Test
  public void testStringFunctionConversion() {
    Series out = df.map(new Series.StringFunction() {
      @Override
      public String apply(String... values) {
        return values[0] + "-";
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.STRING);
  }

  @Test
  public void testBooleanFunctionConversion() {
    Series out = df.map(new Series.BooleanFunction() {
      @Override
      public boolean apply(boolean... values) {
        return !values[0];
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testBooleanFunctionExConversion() {
    Series out = df.map(new Series.BooleanFunctionEx() {
      @Override
      public byte apply(boolean... values) {
        return TRUE;
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testDoubleConditionalConversion() {
    Series out = df.map(new Series.DoubleConditional() {
      @Override
      public boolean apply(double... values) {
        return true;
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testLongConditionalConversion() {
    Series out = df.map(new Series.LongConditional() {
      @Override
      public boolean apply(long... values) {
        return true;
      }
    }, "double");
    Assert.assertEquals(out.type(), Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testStringConditionalConversion() {
    Series out = df.map(new Series.StringConditional() {
      @Override
      public boolean apply(String... values) {
        return true;
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testBooleanConditionalConversion() {
    Series out = df.map(new Series.BooleanConditional() {
      @Override
      public boolean apply(boolean... values) {
        return true;
      }
    }, "long");
    Assert.assertEquals(out.type(), Series.SeriesType.BOOLEAN);
  }

  @Test
  public void testFillForward() {
    // must pass
    LongSeries.empty().fillNullForward();

    // must pass
    LongSeries.buildFrom(LongSeries.NULL).fillNullForward();

    LongSeries in = LongSeries.buildFrom(LongSeries.NULL, 1, LongSeries.NULL, 2, 3, LongSeries.NULL);
    LongSeries out = in.fillNullForward();
    Assert.assertEquals(out.values, new long[] { LongSeries.NULL, 1, 1, 2, 3, 3 });
  }

  @Test
  public void testFillBackward() {
    // must pass
    LongSeries.empty().fillNullBackward();

    // must pass
    LongSeries.buildFrom(LongSeries.NULL).fillNullBackward();

    LongSeries in = LongSeries.buildFrom(LongSeries.NULL, 1, LongSeries.NULL, 2, 3, LongSeries.NULL);
    LongSeries out = in.fillNullBackward();
    Assert.assertEquals(out.values, new long[] { 1, 1, 2, 2, 3, LongSeries.NULL});
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testIndexNone() {
    DataFrame df = new DataFrame();
    Assert.assertFalse(df.hasIndex());
    df.getIndex();
  }

  @Test
  public void testIndexDefault() {
    Assert.assertTrue(new DataFrame(0).hasIndex());
    Assert.assertTrue(new DataFrame(1, 2, 3).hasIndex());
    Assert.assertTrue(new DataFrame(DataFrame.toSeries(VALUES_STRING)).hasIndex());
  }

  @Test
  public void testIndexCopy() {
    DataFrame df = new DataFrame(5)
        .addSeries("test", DataFrame.toSeries(VALUES_BOOLEAN))
        .setIndex("test");
    Assert.assertEquals(df.copy().getIndexName(), "test");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testIndexSetInvalid() {
    DataFrame df = new DataFrame(0);
    df.setIndex("test");
  }

  @Test
  public void testIndexRename() {
    DataFrame df = new DataFrame(0);
    Series index = df.getIndex();
    df.renameSeries(df.getIndexName(), "test");
    df.addSeries(DataFrame.COLUMN_INDEX_DEFAULT, DataFrame.toSeries(new double[0]));
    Assert.assertEquals(df.getIndexName(), "test");
    Assert.assertEquals(df.getIndex(), index);
  }

  @Test
  public void testDoubleNormalize() {
    DoubleSeries s = DataFrame.toSeries(1.5, 2.0, 3.5).normalize();
    Assert.assertEquals(s.values(), new double[] { 0, 0.25, 1.0 });
  }

  @Test
  public void testDoubleNormalizeFailInvalid() {
    DoubleSeries s = DataFrame.toSeries(1.5, 1.5, 1.5).normalize();
    Assert.assertEquals(s, DoubleSeries.nulls(3));
  }

  @Test
  public void testDoubleZScore() {
    DoubleSeries s = DataFrame.toSeries(0.0, 1.0, 2.0).zscore();
    assertEquals(s.values(), -0.707, 0.0, 0.707);
  }

  @Test
  public void testDoubleZScoreFailInvalid() {
    DoubleSeries s = DataFrame.toSeries(1.5, 1.5, 1.5).zscore();
    Assert.assertEquals(s, DoubleSeries.nulls(3));
  }

  @Test
  public void testDoubleOperationsSeries() {
    DoubleSeries base = DataFrame.toSeries(DoubleSeries.NULL, 0, 1, 1.5, 0.003);
    DoubleSeries mod = DataFrame.toSeries(1, 1, 1, 0, DoubleSeries.NULL);

    assertEquals(base.add(mod), DoubleSeries.NULL, 1, 2, 1.5, DoubleSeries.NULL);
    assertEquals(base.subtract(mod), DoubleSeries.NULL, -1, 0, 1.5, DoubleSeries.NULL);
    assertEquals(base.multiply(mod), DoubleSeries.NULL, 0, 1, 0, DoubleSeries.NULL);
    assertEquals(base.divide(mod.replace(0, 1)), DoubleSeries.NULL, 0, 1, 1.5, DoubleSeries.NULL);

    try {
      base.divide(mod);
      Assert.fail();
    } catch(ArithmeticException ignore) {
      // left blank
    }
  }

  @Test
  public void testDoubleOperationsConstant() {
    DoubleSeries base = DataFrame.toSeries(DoubleSeries.NULL, 0, 1, 1.5, 0.003);

    assertEquals(base.add(1), DoubleSeries.NULL, 1, 2, 2.5, 1.003);
    assertEquals(base.add(0), DoubleSeries.NULL, 0, 1, 1.5, 0.003);
    assertEquals(base.add(-1), DoubleSeries.NULL, -1, 0, 0.5, -0.997);
    assertEquals(base.add(DoubleSeries.NULL), DoubleSeries.nulls(5));

    assertEquals(base.subtract(1), DoubleSeries.NULL, -1, 0, 0.5, -0.997);
    assertEquals(base.subtract(0), DoubleSeries.NULL, 0, 1, 1.5, 0.003);
    assertEquals(base.subtract(-1), DoubleSeries.NULL, 1, 2, 2.5, 1.003);
    assertEquals(base.subtract(DoubleSeries.NULL), DoubleSeries.nulls(5));

    assertEquals(base.multiply(1), DoubleSeries.NULL, 0, 1, 1.5, 0.003);
    assertEquals(base.multiply(0), DoubleSeries.NULL, 0, 0, 0, 0);
    assertEquals(base.multiply(-1), DoubleSeries.NULL, 0, -1, -1.5, -0.003);
    assertEquals(base.multiply(DoubleSeries.NULL), DoubleSeries.nulls(5));

    assertEquals(base.divide(1), DoubleSeries.NULL, 0, 1, 1.5, 0.003);
    assertEquals(base.divide(-1), DoubleSeries.NULL, 0, -1, -1.5, -0.003);
    assertEquals(base.divide(DoubleSeries.NULL), DoubleSeries.nulls(5));

    try {
      base.divide(0);
      Assert.fail();
    } catch(ArithmeticException ignore) {
      // left blank
    }
  }

  @Test
  public void testLongOperationsSeries() {
    LongSeries base = DataFrame.toSeries(LongSeries.NULL, 0, 1, 5, 10);
    LongSeries mod = DataFrame.toSeries(1, 1, 1, 0, LongSeries.NULL);

    assertEquals(base.add(mod), LongSeries.NULL, 1, 2, 5, LongSeries.NULL);
    assertEquals(base.subtract(mod), LongSeries.NULL, -1, 0, 5, LongSeries.NULL);
    assertEquals(base.multiply(mod), LongSeries.NULL, 0, 1, 0, LongSeries.NULL);
    assertEquals(base.divide(mod.replace(0, 1)), LongSeries.NULL, 0, 1, 5, LongSeries.NULL);

    try {
      base.divide(mod);
      Assert.fail();
    } catch(ArithmeticException ignore) {
      // left blank
    }
  }

  @Test
  public void testLongOperationsConstant() {
    LongSeries base = DataFrame.toSeries(LongSeries.NULL, 0, 1, 5, 10);

    assertEquals(base.add(1), LongSeries.NULL, 1, 2, 6, 11);
    assertEquals(base.add(0), LongSeries.NULL, 0, 1, 5, 10);
    assertEquals(base.add(-1), LongSeries.NULL, -1, 0, 4, 9);
    assertEquals(base.add(LongSeries.NULL), LongSeries.nulls(5));

    assertEquals(base.subtract(1), LongSeries.NULL, -1, 0, 4, 9);
    assertEquals(base.subtract(0), LongSeries.NULL, 0, 1, 5, 10);
    assertEquals(base.subtract(-1), LongSeries.NULL, 1, 2, 6, 11);
    assertEquals(base.subtract(LongSeries.NULL), LongSeries.nulls(5));

    assertEquals(base.multiply(1), LongSeries.NULL, 0, 1, 5, 10);
    assertEquals(base.multiply(0), LongSeries.NULL, 0, 0, 0, 0);
    assertEquals(base.multiply(-1), LongSeries.NULL, 0, -1, -5, -10);
    assertEquals(base.multiply(LongSeries.NULL), LongSeries.nulls(5));

    assertEquals(base.divide(1), LongSeries.NULL, 0, 1, 5, 10);
    assertEquals(base.divide(-1), LongSeries.NULL, 0, -1, -5, -10);
    assertEquals(base.divide(LongSeries.NULL), LongSeries.nulls(5));

    try {
      base.divide(0);
      Assert.fail();
    } catch(ArithmeticException ignore) {
      // left blank
    }
  }

  @Test
  public void testStringOperationsSeries() {
    StringSeries base = DataFrame.toSeries(StringSeries.NULL, "a", "b", "c", "d");
    StringSeries mod = DataFrame.toSeries("A", "A", "A", "B", StringSeries.NULL);

    assertEquals(base.concat(mod), StringSeries.NULL, "aA", "bA", "cB", StringSeries.NULL);
  }

  @Test
  public void testStringOperationsConstant() {
    StringSeries base = DataFrame.toSeries(StringSeries.NULL, "a", "b", "c", "d");

    assertEquals(base.concat("X"), StringSeries.NULL, "aX", "bX", "cX", "dX");
    assertEquals(base.concat(""), StringSeries.NULL, "a", "b", "c", "d");
    assertEquals(base.concat(StringSeries.NULL), StringSeries.nulls(5));
  }

  @Test
  public void testBooleanOperationsSeries() {
    BooleanSeries base = DataFrame.toSeries(new byte[] { BooleanSeries.NULL, 1, 0, 1, 0 });
    BooleanSeries mod = DataFrame.toSeries(new byte[] { 1, 1, 1, 0, BooleanSeries.NULL });

    assertEquals(base.and(mod), new byte[] { BooleanSeries.NULL, 1, 0, 0, BooleanSeries.NULL });
    assertEquals(base.or(mod), new byte[] { BooleanSeries.NULL, 1, 1, 1, BooleanSeries.NULL });
    assertEquals(base.xor(mod), new byte[] { BooleanSeries.NULL, 0, 1, 1, BooleanSeries.NULL });
    assertEquals(base.implies(mod), new byte[] { BooleanSeries.NULL, 1, 1, 0, BooleanSeries.NULL });
  }

  @Test
  public void testBooleanOperationsConstant() {
    BooleanSeries base = DataFrame.toSeries(new byte[] { BooleanSeries.NULL, 1, 0, 1, 0 });

    assertEquals(base.and(true), new byte[] { BooleanSeries.NULL, 1, 0, 1, 0 });
    assertEquals(base.and(false), new byte[] { BooleanSeries.NULL, 0, 0, 0, 0 });
    assertEquals(base.and(BooleanSeries.NULL), BooleanSeries.nulls(5));

    assertEquals(base.or(true), new byte[] { BooleanSeries.NULL, 1, 1, 1, 1 });
    assertEquals(base.or(false), new byte[] { BooleanSeries.NULL, 1, 0, 1, 0 });
    assertEquals(base.or(BooleanSeries.NULL), BooleanSeries.nulls(5));

    assertEquals(base.xor(true), new byte[] { BooleanSeries.NULL, 0, 1, 0, 1 });
    assertEquals(base.xor(false), new byte[] { BooleanSeries.NULL, 1, 0, 1, 0 });
    assertEquals(base.xor(BooleanSeries.NULL), BooleanSeries.nulls(5));

    assertEquals(base.implies(true), new byte[] { BooleanSeries.NULL, 1, 1, 1, 1 });
    assertEquals(base.implies(false), new byte[] { BooleanSeries.NULL, 0, 1, 0, 1 });
    assertEquals(base.implies(BooleanSeries.NULL), BooleanSeries.nulls(5));
  }

  @Test
  public void testAppend() {
    DataFrame base = new DataFrame();
    base.addSeries("A", 1, 2, 3, 4);
    base.addSeries("B", "a", "b", "c", "d");
    base.setIndex("B");

    DataFrame other = new DataFrame();
    other.addSeries("A", 5.0d, 6.3d, 7.1d);
    other.addSeries("C", true, true, false);

    DataFrame another = new DataFrame();
    another.addSeries("C", false, false);

    DataFrame res = base.append(other, another);

    Assert.assertEquals(res.getSeriesNames(), new HashSet<>(Arrays.asList("A", "B")));
    Assert.assertEquals(res.get("A").type(), Series.SeriesType.LONG);
    Assert.assertEquals(res.get("B").type(), Series.SeriesType.STRING);

    assertEquals(res.getLongs("A"), 1, 2, 3, 4, 5, 6, 7, LongSeries.NULL, LongSeries.NULL);
    assertEquals(res.getStrings("B"), "a", "b", "c", "d", null, null, null, null, null);
  }

  /* **************************************************************************
   * Helpers
   ***************************************************************************/

  static void assertEquals(Series actual, Series expected) {
    Assert.assertEquals(actual, expected);
  }

  static void assertEquals(Series actual, double... expected) {
    assertEquals(actual.getDoubles().values(), expected);
  }

  static void assertEquals(double[] actual, double... expected) {
    if(actual.length != expected.length)
      Assert.fail(String.format("expected array length [%d] but found [%d]", actual.length, expected.length));
    for(int i=0; i<actual.length; i++) {
      if(Double.isNaN(actual[i]) && Double.isNaN(expected[i]))
        continue;
      Assert.assertEquals(actual[i], expected[i], COMPARE_DOUBLE_DELTA, "index=" + i);
    }
  }

  static void assertEquals(Series actual, long... expected) {
    assertEquals(actual.getLongs().values(), expected);
  }

  static void assertEquals(long[] actual, long... expected) {
    if(actual.length != expected.length)
      Assert.fail(String.format("expected array length [%d] but found [%d]", actual.length, expected.length));
    for(int i=0; i<actual.length; i++) {
      Assert.assertEquals(actual[i], expected[i], "index=" + i);
    }
  }

  static void assertEquals(Series actual, String... expected) {
    assertEquals(actual.getStrings().values(), expected);
  }

  static void assertEquals(String[] actual, String... expected) {
    if(actual.length != expected.length)
      Assert.fail(String.format("expected array length [%d] but found [%d]", actual.length, expected.length));
    for(int i=0; i<actual.length; i++) {
      Assert.assertEquals(actual[i], expected[i], "index=" + i);
    }
  }

  static void assertEquals(Series actual, byte... expected) {
    assertEquals(actual.getBooleans().values(), expected);
  }

  static void assertEquals(Series actual, boolean... expected) {
    BooleanSeries s = actual.getBooleans();
    if(s.hasNull())
      Assert.fail("Encountered NULL when comparing against booleans");
    assertEquals(s.valuesBoolean(), expected);
  }

  static void assertEquals(byte[] actual, byte... expected) {
    if(actual.length != expected.length)
      Assert.fail(String.format("expected array length [%d] but found [%d]", actual.length, expected.length));
    for(int i=0; i<actual.length; i++) {
      Assert.assertEquals(actual[i], expected[i], "index=" + i);
    }
  }

  static void assertEquals(boolean[] actual, boolean... expected) {
    if(actual.length != expected.length)
      Assert.fail(String.format("expected array length [%d] but found [%d]", actual.length, expected.length));
    for(int i=0; i<actual.length; i++) {
      Assert.assertEquals(actual[i], expected[i], "index=" + i);
    }
  }
}
