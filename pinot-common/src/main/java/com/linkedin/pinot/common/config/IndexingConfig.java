/**
 * Copyright (C) 2014-2018 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.common.config;

import com.linkedin.pinot.common.data.StarTreeIndexSpec;
import com.linkedin.pinot.common.utils.EqualityUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexingConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(IndexingConfig.class);

  @ConfigKey("invertedIndexColumns")
  private List<String> _invertedIndexColumns;

  @ConfigKey("autoGeneratedInvertedIndex")
  private boolean _autoGeneratedInvertedIndex;

  @ConfigKey("createInvertedIndexDuringSegmentGeneration")
  private boolean _createInvertedIndexDuringSegmentGeneration;

  @ConfigKey("sortedColumn")
  private List<String> _sortedColumn = new ArrayList<>();

  @ConfigKey("loadMode")
  private String _loadMode;

  @ConfigKey("streamConfigs")
  @UseChildKeyHandler(SimpleMapChildKeyHandler.class)
  private Map<String, String> _streamConfigs = new HashMap<>();

  @ConfigKey("streamConsumptionConfig")
  private StreamConsumptionConfig _streamConsumptionConfig;

  @ConfigKey("segmentFormatVersion")
  private String _segmentFormatVersion;

  @ConfigKey("columnMinMaxValueGeneratorMode")
  private String _columnMinMaxValueGeneratorMode;

  @ConfigKey("noDictionaryColumns")
  private List<String> _noDictionaryColumns; // TODO: replace this with noDictionaryConfig.

  @ConfigKey("noDictionaryConfig")
  @UseChildKeyHandler(SimpleMapChildKeyHandler.class)
  private Map<String, String> _noDictionaryConfig;

  @ConfigKey("onHeapDictionaryColumns")
  private List<String> _onHeapDictionaryColumns;

  @ConfigKey("starTreeIndexSpec")
  private StarTreeIndexSpec _starTreeIndexSpec;

  @ConfigKey("starTreeIndexConfigs")
  private List<StarTreeIndexConfig> _starTreeIndexConfigs;

  @ConfigKey("segmentPartitionConfig")
  private SegmentPartitionConfig _segmentPartitionConfig;

  @ConfigKey("aggregateMetrics")
  private boolean _aggregateMetrics;

  public List<String> getInvertedIndexColumns() {
    return _invertedIndexColumns;
  }

  public void setInvertedIndexColumns(List<String> invertedIndexColumns) {
    _invertedIndexColumns = invertedIndexColumns;
  }

  public boolean isAutoGeneratedInvertedIndex() {
    return _autoGeneratedInvertedIndex;
  }

  public void setAutoGeneratedInvertedIndex(boolean autoGeneratedInvertedIndex) {
    _autoGeneratedInvertedIndex = autoGeneratedInvertedIndex;
  }

  public boolean isCreateInvertedIndexDuringSegmentGeneration() {
    return _createInvertedIndexDuringSegmentGeneration;
  }

  public void setCreateInvertedIndexDuringSegmentGeneration(boolean createInvertedIndexDuringSegmentGeneration) {
    _createInvertedIndexDuringSegmentGeneration = createInvertedIndexDuringSegmentGeneration;
  }

  public List<String> getSortedColumn() {
    return _sortedColumn;
  }

  public void setSortedColumn(List<String> sortedColumn) {
    _sortedColumn = sortedColumn;
  }

  public String getLoadMode() {
    return _loadMode;
  }

  public void setLoadMode(String loadMode) {
    _loadMode = loadMode;
  }

  public Map<String, String> getStreamConfigs() {
    return _streamConfigs;
  }

  public void setStreamConfigs(Map<String, String> streamConfigs) {
    _streamConfigs = streamConfigs;
  }

  public StreamConsumptionConfig getStreamConsumptionConfig() {
    return _streamConsumptionConfig;
  }

  public void setStreamConsumptionConfig(StreamConsumptionConfig streamConsumptionConfig) {
    _streamConsumptionConfig = streamConsumptionConfig;
  }

  public String getSegmentFormatVersion() {
    return _segmentFormatVersion;
  }

  public void setSegmentFormatVersion(String segmentFormatVersion) {
    _segmentFormatVersion = segmentFormatVersion;
  }

  public String getColumnMinMaxValueGeneratorMode() {
    return _columnMinMaxValueGeneratorMode;
  }

  public void setColumnMinMaxValueGeneratorMode(String columnMinMaxValueGeneratorMode) {
    _columnMinMaxValueGeneratorMode = columnMinMaxValueGeneratorMode;
  }

  public List<String> getNoDictionaryColumns() {
    return _noDictionaryColumns;
  }

  public Map<String, String> getnoDictionaryConfig() {
    return _noDictionaryConfig;
  }

  public List<String> getOnHeapDictionaryColumns() {
    return _onHeapDictionaryColumns;
  }

  public void setNoDictionaryColumns(List<String> noDictionaryColumns) {
    _noDictionaryColumns = noDictionaryColumns;
  }

  public void setnoDictionaryConfig(Map<String, String> noDictionaryConfig) {
    _noDictionaryConfig = noDictionaryConfig;
  }

  public void setOnHeapDictionaryColumns(List<String> onHeapDictionaryColumns) {
    _onHeapDictionaryColumns = onHeapDictionaryColumns;
  }

  public void setStarTreeIndexSpec(StarTreeIndexSpec starTreeIndexSpec) {
    _starTreeIndexSpec = starTreeIndexSpec;
  }

  public StarTreeIndexSpec getStarTreeIndexSpec() {
    return _starTreeIndexSpec;
  }

  public List<StarTreeIndexConfig> getStarTreeIndexConfigs() {
    return _starTreeIndexConfigs;
  }

  public void setStarTreeIndexConfigs(List<StarTreeIndexConfig> starTreeIndexConfigs) {
    _starTreeIndexConfigs = starTreeIndexConfigs;
  }

  public void setSegmentPartitionConfig(SegmentPartitionConfig config) {
    _segmentPartitionConfig = config;
  }

  public void setAggregateMetrics(boolean value) {
    _aggregateMetrics = value;
  }

  public SegmentPartitionConfig getSegmentPartitionConfig() {
    return _segmentPartitionConfig;
  }

  public boolean getAggregateMetrics() {
    return _aggregateMetrics;
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    final String newLine = System.getProperty("line.separator");

    result.append(this.getClass().getName());
    result.append(" Object {");
    result.append(newLine);

    //determine fields declared in this class only (no fields of superclass)
    final Field[] fields = this.getClass().getDeclaredFields();

    //print field names paired with their values
    for (final Field field : fields) {
      result.append("  ");
      try {
        result.append(field.getName());
        result.append(": ");
        //requires access to private field:
        result.append(field.get(this));
      } catch (final IllegalAccessException ex) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn("Caught exception while processing field " + field, ex);
        }
      }
      result.append(newLine);
    }
    result.append("}");

    return result.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (EqualityUtils.isSameReference(this, o)) {
      return true;
    }

    if (EqualityUtils.isNullOrNotSameClass(this, o)) {
      return false;
    }

    IndexingConfig that = (IndexingConfig) o;

    return EqualityUtils.isEqual(_autoGeneratedInvertedIndex, that._autoGeneratedInvertedIndex)
        && EqualityUtils.isEqual(_createInvertedIndexDuringSegmentGeneration,
        that._createInvertedIndexDuringSegmentGeneration) && EqualityUtils.isEqual(_invertedIndexColumns,
        that._invertedIndexColumns) && EqualityUtils.isEqual(_sortedColumn, that._sortedColumn)
        && EqualityUtils.isEqual(_loadMode, that._loadMode) && EqualityUtils.isEqual(_streamConfigs,
        that._streamConfigs) && EqualityUtils.isEqual(_segmentFormatVersion, that._segmentFormatVersion)
        && EqualityUtils.isEqual(_columnMinMaxValueGeneratorMode, that._columnMinMaxValueGeneratorMode) && EqualityUtils
        .isEqual(_noDictionaryColumns, that._noDictionaryColumns) && EqualityUtils.isEqual(_noDictionaryConfig,
        that._noDictionaryConfig) && EqualityUtils.isEqual(_onHeapDictionaryColumns, that._onHeapDictionaryColumns)
        && EqualityUtils.isEqual(_starTreeIndexSpec, that._starTreeIndexSpec) && EqualityUtils.isEqual(
        _segmentPartitionConfig, that._segmentPartitionConfig);
  }

  @Override
  public int hashCode() {
    int result = EqualityUtils.hashCodeOf(_invertedIndexColumns);
    result = EqualityUtils.hashCodeOf(result, _autoGeneratedInvertedIndex);
    result = EqualityUtils.hashCodeOf(result, _createInvertedIndexDuringSegmentGeneration);
    result = EqualityUtils.hashCodeOf(result, _sortedColumn);
    result = EqualityUtils.hashCodeOf(result, _loadMode);
    result = EqualityUtils.hashCodeOf(result, _streamConfigs);
    result = EqualityUtils.hashCodeOf(result, _segmentFormatVersion);
    result = EqualityUtils.hashCodeOf(result, _columnMinMaxValueGeneratorMode);
    result = EqualityUtils.hashCodeOf(result, _noDictionaryColumns);
    result = EqualityUtils.hashCodeOf(result, _noDictionaryConfig);
    result = EqualityUtils.hashCodeOf(result, _onHeapDictionaryColumns);
    result = EqualityUtils.hashCodeOf(result, _starTreeIndexSpec);
    result = EqualityUtils.hashCodeOf(result, _segmentPartitionConfig);
    return result;
  }
}
