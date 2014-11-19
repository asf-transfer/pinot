package com.linkedin.pinot.core.chunk.creator;

import org.apache.avro.util.Utf8;

import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.core.indexsegment.columnar.creator.V1Constants;


/**
 * @author Dhaval Patel<dpatel@linkedin.com>
 * Nov 7, 2014
 */

public abstract class AbstractColumnPreIndexStatsCollector {

  private Object previousValue = null;
  protected final FieldSpec fieldSpec;
  private boolean isSorted = true;
  private int prevBiggerThanNextCount = 0;
  private int numberOfChanges = 0;
  protected int totalNumberOfEntries = 0;
  protected int maxNumberOfMultiValues = 0;

  public void updateTotalNumberOfEntries(Object[] entries) {
    totalNumberOfEntries += entries.length;
  }

  public int getTotalNumberOfEntries() {
    return totalNumberOfEntries;
  }

  public AbstractColumnPreIndexStatsCollector(FieldSpec spec) {
    fieldSpec = spec;
    previousValue = addressNull(previousValue, fieldSpec.getDataType());
  }

  public int getMaxNumberOfMultiValues() {
    return maxNumberOfMultiValues;
  }

  public void addressSorted(Object entry) {
    if (((Comparable) entry).compareTo(previousValue) != 0) {
      numberOfChanges++;
    }
    if (((Comparable) entry).compareTo(previousValue) < 0) {
      prevBiggerThanNextCount++;
    }

    if (!entry.equals(previousValue) && previousValue != null) {
      final Comparable prevValue = (Comparable) previousValue;
      final Comparable origin = (Comparable) entry;
      if (origin.compareTo(prevValue) < 0) {
        isSorted = false;
      }
    }
    previousValue = entry;
  }

  public boolean isSorted() {
    return isSorted;
  }

  public abstract void collect(Object entry);

  public abstract Object getMinValue() throws Exception;

  public abstract Object getMaxValue() throws Exception;

  public abstract Object[] getUniqueValuesSet() throws Exception;

  public abstract int getCardinality() throws Exception;

  public int getLengthOfLargestElement() throws Exception {
    return -1;
  }

  public abstract boolean hasNull();

  public abstract void seal();

  public Object addressNull(Object entry, DataType e) {
    if (entry == null) {
      if (e == DataType.STRING || e == DataType.BOOLEAN) {
        entry = V1Constants.Str.NULL_STRING;
      } else if (e == DataType.DOUBLE) {
        entry = V1Constants.Numbers.NULL_DOUBLE;
      } else if (e == DataType.FLOAT) {
        entry = V1Constants.Numbers.NULL_FLOAT;
      } else if (e == DataType.LONG) {
        entry = V1Constants.Numbers.NULL_LONG;
      } else if (e == DataType.INT) {
        entry = V1Constants.Numbers.NULL_INT;
      }
    }
    return entry;
  }

  public Object addressNull(Object entry) {
    System.out.println("******* calling ");
    if (entry == null) {

      System.out.println("entry is null");
      if (entry instanceof String || entry instanceof Boolean || entry instanceof Utf8) {
        entry = V1Constants.Str.NULL_STRING;
        System.out.println("^^^^^^^^^^^^^^^^^^^ : " + entry);
      } else if (entry instanceof Double) {
        entry = V1Constants.Numbers.NULL_DOUBLE;
      } else if (entry instanceof Float) {
        entry = V1Constants.Numbers.NULL_FLOAT;
      } else if (entry instanceof Long) {
        entry = V1Constants.Numbers.NULL_LONG;
      } else if (entry instanceof Integer) {
        entry = V1Constants.Numbers.NULL_INT;
      }
    }
    return entry;
  }
}
