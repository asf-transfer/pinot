package com.linkedin.thirdeye.detector.functionex.dataframe;

import com.udojava.evalex.Expression;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.math.NumberUtils;


public class DataFrame {
  public static Pattern SERIES_NAME_PATTERN = Pattern.compile("([A-Za-z_]\\w*)");

  public static final String COLUMN_INDEX = "index";
  public static final String COLUMN_JOIN = "join";

  public interface ResamplingStrategy {
    DataFrame apply(Series.SeriesGrouping grouping, Series s);
  }

  public static final class ResampleLast implements ResamplingStrategy {
    @Override
    public DataFrame apply(Series.SeriesGrouping grouping, Series s) {
      switch(s.type()) {
        case DOUBLE:
          return grouping.applyTo(s).aggregate(new DoubleSeries.DoubleBatchLast());
        case LONG:
          return grouping.applyTo(s).aggregate(new LongSeries.LongBatchLast());
        case STRING:
          return grouping.applyTo(s).aggregate(new StringSeries.StringBatchLast());
        case BOOLEAN:
          return grouping.applyTo(s).aggregate(new BooleanSeries.BooleanBatchLast());
        default:
          throw new IllegalArgumentException(String.format("Cannot resample series type '%s'", s.type()));
      }
    }
  }

  public static final class DataFrameGrouping {
    final Series keys;
    final List<Series.Bucket> buckets;
    final DataFrame source;

    DataFrameGrouping(Series keys, DataFrame source, List<Series.Bucket> buckets) {
      this.keys = keys;
      this.buckets = buckets;
      this.source = source;
    }

    public int size() {
      return this.keys.size();
    }

    public int sourceSize() {
      return this.source.size();
    }

    public DataFrame source() {
      return this.source;
    }

    public boolean isEmpty() {
      return this.keys.isEmpty();
    }

    public Series.SeriesGrouping get(String seriesName) {
      return new Series.SeriesGrouping(keys, this.source.get(seriesName), this.buckets);
    }

    public DataFrame aggregate(String seriesName, Series.DoubleBatchFunction function) {
      return this.get(seriesName).aggregate(function);
    }

    public DataFrame aggregate(String seriesName, Series.LongBatchFunction function) {
      return this.get(seriesName).aggregate(function);
    }

    public DataFrame aggregate(String seriesName, Series.StringBatchFunction function) {
      return this.get(seriesName).aggregate(function);
    }

    public DataFrame aggregate(String seriesName, Series.BooleanBatchFunction function) {
      return this.get(seriesName).aggregate(function);
    }
  }



  Map<String, Series> series = new HashMap<>();

  public static DoubleSeries toSeries(double... values) {
    return new DoubleSeries(values);
  }

  public static LongSeries toSeries(long... values) {
    return new LongSeries(values);
  }

  public static BooleanSeries toSeries(boolean... values) {
    return new BooleanSeries(values);
  }

  public static StringSeries toSeries(String... values) {
    return new StringSeries(values);
  }

  public static DoubleSeries toSeriesFromDouble(Collection<Double> values) {
    return DataFrame.toSeries(ArrayUtils.toPrimitive(values.toArray(new Double[values.size()])));
  }

  public static LongSeries toSeriesFromLong(Collection<Long> values) {
    return DataFrame.toSeries(ArrayUtils.toPrimitive(values.toArray(new Long[values.size()])));
  }

  public static StringSeries toSeriesFromString(Collection<String> values) {
    return DataFrame.toSeries(values.toArray(new String[values.size()]));
  }

  public static BooleanSeries toSeriesFromBoolean(Collection<Boolean> values) {
    return DataFrame.toSeries(ArrayUtils.toPrimitive(values.toArray(new Boolean[values.size()])));
  }

  public DataFrame(int defaultIndexSize) {
    long[] indexValues = new long[defaultIndexSize];
    for(int i=0; i<defaultIndexSize; i++) {
      indexValues[i] = i;
    }
    this.addSeries(COLUMN_INDEX, new LongSeries(indexValues));
  }

  public DataFrame(long[] indexValues) {
    this.addSeries(COLUMN_INDEX, new LongSeries(indexValues));
  }

  public DataFrame(LongSeries index) {
    this.addSeries(COLUMN_INDEX, index);
  }

  public DataFrame() {
    // left blank
  }

  public int size() {
    if(this.series.isEmpty())
      return 0;
    return this.series.values().iterator().next().size();
  }

  public DataFrame sliceRows(int from, int to) {
    DataFrame newDataFrame = new DataFrame();
    for(Map.Entry<String, Series> e : this.series.entrySet()) {
      newDataFrame.addSeries(e.getKey(), e.getValue().slice(from, to));
    }
    return newDataFrame;
  }

  public boolean isEmpty() {
    return this.size() <= 0;
  }

  public DataFrame copy() {
    DataFrame newDataFrame = new DataFrame();
    for(Map.Entry<String, Series> e : this.series.entrySet()) {
      newDataFrame.addSeries(e.getKey(), e.getValue().copy());
    }
    return newDataFrame;
  }

  public void addSeries(String seriesName, Series s) {
    if(seriesName == null || !SERIES_NAME_PATTERN.matcher(seriesName).matches())
      throw new IllegalArgumentException(String.format("Series name must match pattern '%s'", SERIES_NAME_PATTERN));
    if(!this.series.isEmpty() && s.size() != this.size())
      throw new IllegalArgumentException("DataFrame index and series must be of same length");
    series.put(seriesName, s);
  }

  public void addSeries(String seriesName, double... values) {
    addSeries(seriesName, DataFrame.toSeries(values));
  }

  public void addSeries(String seriesName, long... values) {
    addSeries(seriesName, DataFrame.toSeries(values));
  }

  public void addSeries(String seriesName, String... values) {
    addSeries(seriesName, DataFrame.toSeries(values));
  }

  public void addSeries(String seriesName, boolean... values) {
    addSeries(seriesName, DataFrame.toSeries(values));
  }

  public void dropSeries(String seriesName) {
    this.series.remove(seriesName);
  }

  public void renameSeries(String oldName, String newName) {
    Series s = assertSeriesExists(oldName);
    this.dropSeries(oldName);
    this.addSeries(newName, s);
  }

  public Set<String> getSeriesNames() {
    return Collections.unmodifiableSet(this.series.keySet());
  }

  public Map<String, Series> getSeries() {
    return Collections.unmodifiableMap(this.series);
  }

  public Series get(String seriesName) {
    return assertSeriesExists(seriesName);
  }

  public boolean contains(String seriesName) {
    return this.series.containsKey(seriesName);
  }

  public DoubleSeries toDoubles(String seriesName) {
    return assertSeriesExists(seriesName).toDoubles();
  }

  public LongSeries toLongs(String seriesName) {
    return assertSeriesExists(seriesName).toLongs();
  }

  public StringSeries toStrings(String seriesName) {
    return assertSeriesExists(seriesName).toStrings();
  }

  public BooleanSeries toBooleans(String seriesName) {
   return assertSeriesExists(seriesName).toBooleans();
  }

  public DoubleSeries map(DoubleSeries.DoubleBatchFunction function, String... seriesNames) {
    assertNotNull(seriesNames);
    return this.mapWithNull(function, seriesNames);
  }

  public DoubleSeries mapWithNull(DoubleSeries.DoubleBatchFunction function, String... seriesNames) {
    DoubleSeries[] inputSeries = new DoubleSeries[seriesNames.length];
    for(int i=0; i<seriesNames.length; i++) {
      inputSeries[i] = assertSeriesExists(seriesNames[i]).toDoubles();
    }

    double[] output = new double[this.size()];
    for(int i=0; i<this.size(); i++) {
      double[] input = new double[seriesNames.length];
      for(int j=0; j<inputSeries.length; j++) {
        input[j] = inputSeries[j].values[i];
      }
      output[i] = function.apply(input);
    }

    return new DoubleSeries(output);
  }

  public LongSeries map(LongSeries.LongBatchFunction function, String... seriesNames) {
    assertNotNull(seriesNames);
    return this.mapWithNull(function, seriesNames);
  }

  public LongSeries mapWithNull(LongSeries.LongBatchFunction function, String... seriesNames) {
    LongSeries[] inputSeries = new LongSeries[seriesNames.length];
    for(int i=0; i<seriesNames.length; i++) {
      inputSeries[i] = assertSeriesExists(seriesNames[i]).toLongs();
    }

    long[] output = new long[this.size()];
    for(int i=0; i<this.size(); i++) {
      long[] input = new long[seriesNames.length];
      for(int j=0; j<inputSeries.length; j++) {
        input[j] = inputSeries[j].values[i];
      }
      output[i] = function.apply(input);
    }

    return new LongSeries(output);
  }

  public StringSeries map(StringSeries.StringBatchFunction function, String... seriesNames) {
    assertNotNull(seriesNames);
    return this.mapWithNull(function, seriesNames);
  }

  public StringSeries mapWithNull(StringSeries.StringBatchFunction function, String... seriesNames) {
    StringSeries[] inputSeries = new StringSeries[seriesNames.length];
    for(int i=0; i<seriesNames.length; i++) {
      inputSeries[i] = assertSeriesExists(seriesNames[i]).toStrings();
    }

    String[] output = new String[this.size()];
    for(int i=0; i<this.size(); i++) {
      String[] input = new String[seriesNames.length];
      for(int j=0; j<inputSeries.length; j++) {
        input[j] = inputSeries[j].values[i];
      }
      output[i] = function.apply(input);
    }

    return new StringSeries(output);
  }

  public BooleanSeries map(BooleanSeries.BooleanBatchFunction function, String... seriesNames) {
    assertNotNull(seriesNames);
    return this.mapWithNull(function, seriesNames);
  }

  public BooleanSeries mapWithNull(BooleanSeries.BooleanBatchFunction function, String... seriesNames) {
    BooleanSeries[] inputSeries = new BooleanSeries[seriesNames.length];
    for(int i=0; i<seriesNames.length; i++) {
      inputSeries[i] = assertSeriesExists(seriesNames[i]).toBooleans();
    }

    boolean[] output = new boolean[this.size()];
    for(int i=0; i<this.size(); i++) {
      boolean[] input = new boolean[seriesNames.length];
      for(int j=0; j<inputSeries.length; j++) {
        input[j] = inputSeries[j].values[i];
      }
      output[i] = function.apply(input);
    }

    return new BooleanSeries(output);
  }

  public DoubleSeries map(String doubleExpression, String... seriesNames) {
    assertNotNull(seriesNames);
    return this.mapWithNull(doubleExpression, seriesNames);
  }

  public DoubleSeries mapWithNull(String doubleExpression, String... seriesNames) {
    Expression e = new Expression(doubleExpression);

    return this.mapWithNull(new DoubleSeries.DoubleBatchFunction() {
      @Override
      public double apply(double[] values) {
        for(int i=0; i<values.length; i++) {
          if(DoubleSeries.isNull(values[i]))
            return DoubleSeries.NULL_VALUE;
          e.with(seriesNames[i], new BigDecimal(values[i]));
        }
        return e.eval().doubleValue();
      }
    }, seriesNames);
  }

  public DoubleSeries map(String doubleExpression) {
    Set<String> variables = extractSeriesNames(doubleExpression);
    return this.map(doubleExpression, variables.toArray(new String[variables.size()]));
  }

  public DoubleSeries mapWithNull(String doubleExpression) {
    Set<String> variables = extractSeriesNames(doubleExpression);
    return this.mapWithNull(doubleExpression, variables.toArray(new String[variables.size()]));
  }

  public DataFrame project(int[] fromIndex) {
    DataFrame newDataFrame = new DataFrame();
    for(Map.Entry<String, Series> e : this.series.entrySet()) {
      newDataFrame.addSeries(e.getKey(), e.getValue().project(fromIndex));
    }
    return newDataFrame;
  }

  /**
   * Sort data frame by series values.  The resulting sorted order is the equivalent of applying
   * a stable sorted to nth series first, and then sorting iteratively until the 1st series.
   *
   * @param seriesNames 1st series, 2nd series, ..., nth series
   * @return sorted data frame
   */
  public DataFrame sortBy(String... seriesNames) {
    DataFrame df = this;
    for(int i=seriesNames.length-1; i>=0; i--) {
      df = df.project(assertSeriesExists(seriesNames[i]).sortedIndex());
    }
    return df;
  }

  public DataFrame reverse() {
    DataFrame newDataFrame = new DataFrame();
    for(Map.Entry<String, Series> e : this.series.entrySet()) {
      newDataFrame.addSeries(e.getKey(), e.getValue().reverse());
    }
    return newDataFrame;
  }

  public DataFrame resampleBy(String seriesName, long interval, ResamplingStrategy strategy) {
    DataFrame baseDataFrame = this.sortBy(seriesName);

    Series.SeriesGrouping grouping = baseDataFrame.toLongs(seriesName).groupByInterval(interval);

    // resample series
    DataFrame newDataFrame = new DataFrame();

    for(Map.Entry<String, Series> e : baseDataFrame.getSeries().entrySet()) {
      if(e.getKey().equals(seriesName))
        continue;
      newDataFrame.addSeries(e.getKey(), strategy.apply(grouping, e.getValue()).get(Series.COLUMN_VALUE));
    }

    // new series
    newDataFrame.addSeries(seriesName, grouping.keys());
    return newDataFrame;
  }

  public DataFrame filter(BooleanSeries series) {
    if(series.size() != this.size())
      throw new IllegalArgumentException("Series size must be equal to index size");

    int[] fromIndex = new int[series.size()];
    int fromIndexCount = 0;
    for(int i=0; i<series.size(); i++) {
      if(series.values[i]) {
        fromIndex[fromIndexCount] = i;
        fromIndexCount++;
      }
    }

    int[] fromIndexCompressed = Arrays.copyOf(fromIndex, fromIndexCount);

    return this.project(fromIndexCompressed);
  }

  public DataFrame filter(String seriesName) {
    return this.filter(this.toBooleans(seriesName));
  }

  public DataFrame filter(String seriesName, DoubleSeries.DoubleConditional conditional) {
    return this.filter(assertSeriesExists(seriesName).toDoubles().map(conditional));
  }

  public DataFrame filter(String seriesName, LongSeries.LongConditional conditional) {
    return this.filter(assertSeriesExists(seriesName).toLongs().map(conditional));
  }

  public DataFrame filter(String seriesName, StringSeries.StringConditional conditional) {
    return this.filter(assertSeriesExists(seriesName).toStrings().map(conditional));
  }

  public DataFrame filterEquals(String seriesName, double value) {
    return this.filter(seriesName, new DoubleSeries.DoubleConditional() {
      @Override
      public boolean apply(double v) {
        return value == v;
      }
    });
  }

  public DataFrame filterEquals(String seriesName, long value) {
    return this.filter(seriesName, new LongSeries.LongConditional() {
      @Override
      public boolean apply(long v) {
        return value == v;
      }
    });
  }

  public DataFrame filterEquals(String seriesName, String value) {
    return this.filter(seriesName, new StringSeries.StringConditional() {
      @Override
      public boolean apply(String v) {
        return value.equals(v);
      }
    });
  }

  public double getDouble(String seriesName) {
    return assertSingleValue(seriesName).toDoubles().first();
  }

  public long getLong(String seriesName) {
    return assertSingleValue(seriesName).toLongs().first();
  }

  public String getString(String seriesName) {
    return assertSingleValue(seriesName).toStrings().first();
  }

  public boolean getBoolean(String seriesName) {
    return assertSingleValue(seriesName).toBooleans().first();
  }

  public static DoubleSeries toDoubles(DoubleSeries s) {
    return s;
  }

  public static DoubleSeries toDoubles(LongSeries s) {
    double[] values = new double[s.size()];
    for(int i=0; i<values.length; i++) {
      if(LongSeries.isNull(s.values[i])) {
        values[i] = DoubleSeries.NULL_VALUE;
      } else {
        values[i] = (double) s.values[i];
      }
    }
    return new DoubleSeries(values);
  }

  public static DoubleSeries toDoubles(StringSeries s) {
    double[] values = new double[s.size()];
    for(int i=0; i<values.length; i++) {
      if(StringSeries.isNull(s.values[i])) {
        values[i] = DoubleSeries.NULL_VALUE;
      } else {
        values[i] = Double.parseDouble(s.values[i]);
      }
    }
    return new DoubleSeries(values);
  }

  public static DoubleSeries toDoubles(BooleanSeries s) {
    double[] values = new double[s.size()];
    for(int i=0; i<values.length; i++) {
      values[i] = s.values[i] ? 1.0d : 0.0d;
    }
    return new DoubleSeries(values);
  }

  public static LongSeries toLongs(DoubleSeries s) {
    long[] values = new long[s.size()];
    for(int i=0; i<values.length; i++) {
      if(DoubleSeries.isNull(s.values[i])) {
        values[i] = LongSeries.NULL_VALUE;
      } else {
        values[i] = (long) s.values[i];
      }
    }
    return new LongSeries(values);
  }

  public static LongSeries toLongs(LongSeries s) {
    return s;
  }

  public static LongSeries toLongs(StringSeries s) {
    long[] values = new long[s.size()];
    for(int i=0; i<values.length; i++) {
      if(StringSeries.isNull(s.values[i])) {
        values[i] = LongSeries.NULL_VALUE;
      } else {
        try {
          values[i] = Long.parseLong(s.values[i]);
        } catch (NumberFormatException e) {
          values[i] = (long) Double.parseDouble(s.values[i]);
        }
      }
    }
    return new LongSeries(values);
  }

  public static LongSeries toLongs(BooleanSeries s) {
    long[] values = new long[s.size()];
    for(int i=0; i<values.length; i++) {
      values[i] = s.values[i] ? 1L : 0L;
    }
    return new LongSeries(values);
  }

  public static BooleanSeries toBooleans(DoubleSeries s) {
    boolean[] values = new boolean[s.size()];
    for(int i=0; i<values.length; i++) {
      if(DoubleSeries.isNull(s.values[i])) {
        values[i] = BooleanSeries.NULL_VALUE;
      } else {
        values[i] = s.values[i] != 0.0d;
      }
    }
    return new BooleanSeries(values);
  }

  public static BooleanSeries toBooleans(LongSeries s) {
    boolean[] values = new boolean[s.size()];
    for(int i=0; i<values.length; i++) {
      if(LongSeries.isNull(s.values[i])) {
        values[i] = BooleanSeries.NULL_VALUE;
      } else {
        values[i] = s.values[i] != 0L;
      }
    }
    return new BooleanSeries(values);
  }

  public static BooleanSeries toBooleans(BooleanSeries s) {
    return s;
  }

  public static BooleanSeries toBooleans(StringSeries s) {
    boolean[] values = new boolean[s.size()];
    for(int i=0; i<values.length; i++) {
      if(StringSeries.isNull(s.values[i])) {
        values[i] = BooleanSeries.NULL_VALUE;
      } else {
        if(NumberUtils.isNumber(s.values[i])) {
          values[i] = Double.parseDouble(s.values[i]) != 0.0d;
        } else {
          values[i] = Boolean.parseBoolean(s.values[i]);
        }
      }
    }
    return new BooleanSeries(values);
  }

  public static StringSeries toStrings(DoubleSeries s) {
    String[] values = new String[s.size()];
    for(int i=0; i<values.length; i++) {
      if(DoubleSeries.isNull(s.values[i])) {
        values[i] = StringSeries.NULL_VALUE;
      } else {
        values[i] = String.valueOf(s.values[i]);
      }
    }
    return new StringSeries(values);
  }

  public static StringSeries toStrings(LongSeries s) {
    String[] values = new String[s.size()];
    for(int i=0; i<values.length; i++) {
      if(LongSeries.isNull(s.values[i])) {
        values[i] = StringSeries.NULL_VALUE;
      } else {
        values[i] = String.valueOf(s.values[i]);
      }
    }
    return new StringSeries(values);
  }

  public static StringSeries toStrings(BooleanSeries s) {
    String[] values = new String[s.size()];
    for(int i=0; i<values.length; i++) {
      values[i] = String.valueOf(s.values[i]);
    }
    return new StringSeries(values);
  }

  public static StringSeries toStrings(StringSeries s) {
    return s;
  }

  public static Series toType(Series s, Series.SeriesType type) {
    switch(type) {
      case DOUBLE:
        return s.toDoubles();
      case LONG:
        return s.toLongs();
      case BOOLEAN:
        return s.toBooleans();
      case STRING:
        return s.toStrings();
      default:
        throw new IllegalArgumentException(String.format("Unknown series type '%s'", type));
    }
  }

  public DataFrameGrouping groupBy(Series labels) {
    Series.SeriesGrouping grouping = labels.groupByValue();
    return new DataFrameGrouping(grouping.keys(), this, grouping.buckets);
  }

  public DataFrameGrouping groupBy(String seriesName) {
    return this.groupBy(this.get(seriesName));
  }

  public DataFrame dropNullRows() {
    int[] fromIndex = new int[this.size()];
    for(int i=0; i<fromIndex.length; i++) {
      fromIndex[i] = i;
    }

    for(Series s : this.series.values()) {
      int[] nulls = s.nullIndex();
      for(int n : nulls) {
        fromIndex[n] = -1;
      }
    }

    int countNotNull = 0;
    for(int i=0; i<fromIndex.length; i++) {
      if(fromIndex[i] >= 0) {
        fromIndex[countNotNull] = fromIndex[i];
        countNotNull++;
      }
    }

    int[] fromIndexCompressed = Arrays.copyOf(fromIndex, countNotNull);

    return this.project(fromIndexCompressed);
  }

  public DataFrame dropNullColumns() {
    DataFrame df = new DataFrame();
    for(Map.Entry<String, Series> e : this.getSeries().entrySet()) {
      if(!e.getValue().hasNull())
        df.addSeries(e.getKey(), e.getValue());
    }
    return df;
  }

  public DataFrame joinInner(DataFrame other, String onSeriesLeft, String onSeriesRight) {
    List<Series.JoinPair> pairs = DataFrame.makeJoinPairs(this, other, onSeriesLeft, onSeriesRight, Series.JoinType.INNER);
    return DataFrame.join(this, other, pairs);
  }

  public DataFrame joinLeft(DataFrame other, String onSeriesLeft, String onSeriesRight) {
    List<Series.JoinPair> pairs = DataFrame.makeJoinPairs(this, other, onSeriesLeft, onSeriesRight, Series.JoinType.LEFT);
    return DataFrame.join(this, other, pairs);
  }

  public DataFrame joinRight(DataFrame other, String onSeriesLeft, String onSeriesRight) {
    List<Series.JoinPair> pairs = DataFrame.makeJoinPairs(this, other, onSeriesLeft, onSeriesRight, Series.JoinType.RIGHT);
    return DataFrame.join(this, other, pairs);
  }

  public DataFrame joinOuter(DataFrame other, String onSeriesLeft, String onSeriesRight) {
    List<Series.JoinPair> pairs = DataFrame.makeJoinPairs(this, other, onSeriesLeft, onSeriesRight, Series.JoinType.OUTER);
    return DataFrame.join(this, other, pairs);
  }

  static List<Series.JoinPair> makeJoinPairs(DataFrame left, DataFrame right, String onSeriesLeft, String onSeriesRight, Series.JoinType type) {
    Series sLeft = left.get(onSeriesLeft);
    Series sRight = right.get(onSeriesRight);

    // TODO: automatic renaming
    Set<String> seriesLeft = left.getSeriesNames();
    Set<String> seriesRight = right.getSeriesNames();
    for (String s : seriesLeft) {
      if (seriesRight.contains(s))
        throw new IllegalArgumentException("Series '%s' exists in both DataFrames");
    }

    return sLeft.join(sRight, type);
  }

  static DataFrame join(DataFrame left, DataFrame right, List<Series.JoinPair> pairs) {
    int[] fromIndexLeft = new int[pairs.size()];
    int i=0;
    for(Series.JoinPair p : pairs) {
      fromIndexLeft[i++] = p.left;
    }

    int[] fromIndexRight = new int[pairs.size()];
    int j=0;
    for(Series.JoinPair p : pairs) {
      fromIndexRight[j++] = p.right;
    }

    DataFrame leftData = left.project(fromIndexLeft);
    DataFrame rightData = right.project(fromIndexRight);

    for(Map.Entry<String, Series> e : rightData.getSeries().entrySet()) {
      leftData.addSeries(e.getKey(), e.getValue());
    }

    return leftData;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("DataFrame{\n");
    for(Map.Entry<String, Series> e : this.series.entrySet()) {
      builder.append(e.getKey());
      builder.append(": ");
      builder.append(e.getValue());
      builder.append("\n");
    }
    builder.append("}");
    return builder.toString();
  }

  private Series assertSeriesExists(String name) {
    if(!series.containsKey(name))
      throw new IllegalArgumentException(String.format("Unknown series '%s'", name));
    return series.get(name);
  }

  private Series assertSingleValue(String name) {
    if(assertSeriesExists(name).size() != 1)
      throw new IllegalArgumentException(String.format("Series '%s' must have exactly one element", name));
    return series.get(name);
  }

  private Series assertNotNull(String name) {
    if(assertSeriesExists(name).hasNull())
      throw new IllegalStateException(String.format("Series '%s' Must not contain null values", name));
    return series.get(name);
  }

  private void assertNotNull(String... names) {
    for(String s : names)
      assertNotNull(s);
  }

  private void assertSameLength(Series s) {
    if(this.size() != s.size())
      throw new IllegalArgumentException("Series size must be equals to DataFrame size");
  }

  private Set<String> extractSeriesNames(String doubleExpression) {
    Matcher m = SERIES_NAME_PATTERN.matcher(doubleExpression);

    Set<String> variables = new HashSet<>();
    while(m.find()) {
      if(this.series.keySet().contains(m.group()))
        variables.add(m.group());
    }

    return variables;
  }

}
