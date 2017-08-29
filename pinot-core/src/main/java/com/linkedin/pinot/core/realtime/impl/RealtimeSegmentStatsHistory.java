/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.pinot.core.realtime.impl;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * Keeps history of statistics for segments consumed from kafka for a single table.
 */
public class RealtimeSegmentStatsHistory implements Serializable {
  public static Logger LOGGER = LoggerFactory.getLogger(RealtimeSegmentStatsHistory.class);
  private static final long serialVersionUID = 1L;  // Change this if a new field is added to this class
  // XXX MAX_NUM_ENTRIES should be a final variable, but we need to modify it for testing.
  private static int MAX_NUM_ENTRIES = 16;  // Max number of past segments for which stats are kept
  // Fields to be serialzied.
  private int _cursor = 0;
  private SegmentStats[] _entries;
  private boolean _isFull = false;
  private String _historyFilePath;

  // We return these values when we don't have any prior statistics.
  private final static int DEFAULT_EST_AVG_COL_SIZE = 32;
  private final static int DEFAULT_EST_CARDINALITY = 5000;

  // Not to be serialized
  transient int _arraySize;
  transient File _historyFile;

  @VisibleForTesting
  public static int getMaxNumEntries() {
    return MAX_NUM_ENTRIES;
  }

  @VisibleForTesting
  public static int getDefaultEstAvgColSize() {
    return DEFAULT_EST_AVG_COL_SIZE;
  }

  @VisibleForTesting
  public static int getDefaultEstCardinality() {
    return DEFAULT_EST_CARDINALITY;
  }

  @VisibleForTesting
  public static void setMaxNumEntries(int maxNumEntries) {
    MAX_NUM_ENTRIES = maxNumEntries;
  }

  public static class SegmentStats implements Serializable {
    private static final long serialVersionUID = 1L;
    private int _numRowsConsumed;   // Number of rows consumed
    private int _numSeconds;        // Number of seconds taken to consume them
    private long _memUsedBytes;          // Memory used for consumption (bytes)
    private Map<String, ColumnStats> _colNameToStats = new HashMap();

    public int getNumRowsConsumed() {
      return _numRowsConsumed;
    }

    public void setNumRowsConsumed(int numRowsConsumed) {
      _numRowsConsumed = numRowsConsumed;
    }

    public int getNumSeconds() {
      return _numSeconds;
    }

    public void setNumSeconds(int numSeconds) {
      _numSeconds = numSeconds;
    }

    public long getMemUsedBytes() {
      return _memUsedBytes;
    }

    public void setMemUsedBytes(long memUsedBytes) {
      _memUsedBytes = memUsedBytes;
    }

    public void setColumnStats(@Nonnull String columnName, @Nonnull ColumnStats columnStats) {
      _colNameToStats.put(columnName, columnStats);
    }

    public @Nullable ColumnStats getColumnStats(String columnName) {
      return _colNameToStats.get(columnName);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("nRows=" + getNumRowsConsumed())
          .append(",nMinutes=" + getNumSeconds())
          .append(",memUsed=" + getMemUsedBytes());

      for (Map.Entry<String, ColumnStats> entry : _colNameToStats.entrySet()) {
        sb.append(",")
            .append(entry.getKey())
            .append(":")
            .append(entry.getValue().toString());
      }
      return sb.toString();
    }
  }

  public static class ColumnStats implements Serializable {
    private static final long serialVersionUID = 1L;  // Change this if a new field is added to this class

    private int _avgColumnSize;     // Used only for string columns when building dictionary
    private int _cardinality;       // Used for all dictionary columns

    public int getCardinality() {
      return _cardinality;
    }

    public void setCardinality(int cardinality) {
      _cardinality = cardinality;
    }

    public int getAvgColumnSize() {
      return _avgColumnSize;
    }

    public void setAvgColumnSize(int avgColumnSize) {
      _avgColumnSize = avgColumnSize;
    }

    @Override
    public String toString() {
      return "cardinality=" + getCardinality() + ",avgSize=" + getAvgColumnSize();
    }
  }

  /**
   * Constructor called when there is no file present.
   *
   * @param historyFilePath
   */
  private RealtimeSegmentStatsHistory(String historyFilePath) {
    _entries = new SegmentStats[MAX_NUM_ENTRIES];
    _historyFilePath = historyFilePath;
    _historyFile = new File(_historyFilePath);
    normalize();
  }

  // We may choose to change the number of segments we keep in history. So, if MAX_NUM_ENTRIES is changed,
  // then after deserialization we need to copy old array entries to an array of new size. Depending on
  // whether the new size is higher or lower, the values of _cursor and _isFull will change.
  private void normalize() {
    if (_entries.length == MAX_NUM_ENTRIES) {
      _arraySize = MAX_NUM_ENTRIES;
      return;
    }
    int toCopy;
    if (isFull()) {
      toCopy = Math.min(_entries.length, MAX_NUM_ENTRIES);
      if (_entries.length > MAX_NUM_ENTRIES) {
        _cursor = 0;
      } else {
        _isFull = false;
        _cursor = _entries.length;
      }
    } else {
      toCopy = Math.min(_cursor, MAX_NUM_ENTRIES);
      if (_cursor > MAX_NUM_ENTRIES) {
        _cursor = 0;
        _isFull = true;
      }
    }

    SegmentStats[] tmp = _entries;
    _entries = new SegmentStats[MAX_NUM_ENTRIES];

    for (int i = 0; i < toCopy; i++) {
      _entries[i] = tmp[i];
    }
    _arraySize = _entries.length;
  }

  public int getCursor() {
    return _cursor;
  }

  public int getArraySize() {
    return _arraySize;
  }

  public boolean isFull() {
    return _isFull;
  }

  public synchronized void addSegmentStats(SegmentStats segmentStats) {
    _entries[_cursor] = segmentStats;
    if (_cursor >= _arraySize -1) {
      _isFull = true;
    }
    _cursor = (_cursor + 1) % _arraySize;
  }

  /**
   * Estimate the cardinality of a column based on past segments of a table
   * For now, we return the average value.
   *
   * @param columnName
   * @return estimated
   */
  public synchronized int getEstimatedCardinality(@Nonnull String columnName) {
    int numEntriesToScan = getNumntriesToScan();
    if (numEntriesToScan == 0) {
      return DEFAULT_EST_CARDINALITY;
    }
    int estCardinality = 0;
    int numValidValues = 0;
    for (int i = 0; i < numEntriesToScan; i++) {
      SegmentStats segmentStats = getSegmentStatsAt(i);
      ColumnStats columnStats = segmentStats.getColumnStats(columnName);
      if (columnStats != null) {
        estCardinality += columnStats.getCardinality();
        numValidValues++;
      }
    }
    if (numValidValues > 0) {
      return estCardinality / numValidValues;
    }
    return DEFAULT_EST_CARDINALITY;
  }

  /**
   * Estimate the average size of a string column based on the past segments of the table.
   * For now, we return the average value.
   *
   * @param columnName
   * @return estimated average string size
   */
  public synchronized int getEstimatedAvgColSize(@Nonnull String columnName) {
    int numEntriesToScan = getNumntriesToScan();
    if (numEntriesToScan == 0) {

      return DEFAULT_EST_AVG_COL_SIZE;
    }
    int estAvgColSize = 0;
    int numValidValues = 0;
    for (int i = 0; i < numEntriesToScan; i++) {
      SegmentStats segmentStats = getSegmentStatsAt(i);
      ColumnStats columnStats = segmentStats.getColumnStats(columnName);
      if (columnStats != null) {
        estAvgColSize += columnStats.getAvgColumnSize();
        numValidValues++;
      }
    }
    if (numValidValues > 0) {
      return estAvgColSize / numValidValues;
    }
    return DEFAULT_EST_AVG_COL_SIZE;
  }

  public SegmentStats getSegmentStatsAt(int index) {
    return _entries[index];
  }

  @Override
  public String toString() {
    return "cursor=" + getCursor() + ",numEntries=" + getArraySize() + ",isFull=" + isFull();
  }

  public synchronized void save() {
    try (OutputStream os = new FileOutputStream(new File(_historyFilePath));
        ObjectOutputStream obos = new ObjectOutputStream(os)
    ) {
      obos.writeObject(this);
      obos.flush();
      os.flush();
    } catch (IOException e) {
      LOGGER.warn("Could not update stats file {}", _historyFile, e);
    }
  }

  public static synchronized RealtimeSegmentStatsHistory deserialzeFrom(File inFile) throws IOException, ClassNotFoundException {
    if (inFile.exists()) {
      try (FileInputStream is = new FileInputStream(inFile); ObjectInputStream obis = new ObjectInputStream(is)) {
        RealtimeSegmentStatsHistory history = (RealtimeSegmentStatsHistory) (obis.readObject());
        history.normalize();
        return history;
      }
    } else {
      return new RealtimeSegmentStatsHistory(inFile.getAbsolutePath());
    }
  }

  private int getNumntriesToScan() {
    if (isFull()) {
      return getArraySize();
    }
    return getCursor();
  }

  public static void main(String[] args) throws Exception {
    RealtimeSegmentStatsHistory history = RealtimeSegmentStatsHistory.deserialzeFrom(new File("/tmp/stats.ser"));
    System.out.println(history.toString());
    for (int i = 0; i <history.getNumntriesToScan(); i++) {
      SegmentStats segmentStats = history.getSegmentStatsAt(i);
      System.out.println(segmentStats.toString());

    }
  }
}
