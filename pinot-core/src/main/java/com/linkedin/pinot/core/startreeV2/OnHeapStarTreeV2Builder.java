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

package com.linkedin.pinot.core.startreeV2;


import java.io.File;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.io.IOException;
import com.linkedin.pinot.common.utils.Pairs;
import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.common.data.MetricFieldSpec;
import com.linkedin.pinot.common.segment.SegmentMetadata;
import com.linkedin.pinot.common.data.DimensionFieldSpec;
import com.linkedin.pinot.core.data.readers.PinotSegmentColumnReader;
import com.linkedin.pinot.core.segment.creator.ColumnIndexCreationInfo;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegment;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegmentLoader;


public class OnHeapStarTreeV2Builder implements StarTreeV2Builder {

  // Segment
  SegmentMetadata _segmentMetadata;
  ImmutableSegment _immutableSegment;

  // Dimensions
  private int _dimensionsCount;
  private List<String> _dimensionsName;
  private List<String> _dimensionsSplitOrder;
  private List<String> _dimensionsWithoutStarNode;
  private Map<String, DimensionFieldSpec> _dimensionsSpecMap;

  // Metrics
  private int _metricsCount;
  private Set<String> _metricsName;
  private int _metricAggfuncPairsCount;
  private List<Met2AggfuncPair> _met2aggfuncPairs;
  private Map<String, MetricFieldSpec> _metricsSpecMap;

  // General
  private int _nodesCount;
  private int _rawDocsCount;
  private TreeNode _rootNode;
  private int _maxNumLeafRecords;


  @Override
  public void init(File indexDir, StarTreeV2Config config) throws Exception {

    // segment
    _immutableSegment = ImmutableSegmentLoader.load(indexDir, ReadMode.mmap);
    _segmentMetadata = _immutableSegment.getSegmentMetadata();
    _rawDocsCount = _segmentMetadata.getTotalRawDocs();

    // dimension
    _dimensionsSpecMap = new HashMap<>();
    _dimensionsName = config.getDimensions();
    _dimensionsCount = _dimensionsName.size();

    List<DimensionFieldSpec> _dimensionsSpecList = _segmentMetadata.getSchema().getDimensionFieldSpecs();
    for ( DimensionFieldSpec dimension : _dimensionsSpecList) {
        if (_dimensionsName.contains(dimension.getName())) {
          _dimensionsSpecMap.put(dimension.getName(), dimension);
      }
    }

    // dimension split order.
    _dimensionsSplitOrder = config.getDimensionsSplitOrder();
    /*
      TODO:if the dimensions split order is not given, compute the default order.
      if (_dimensionsSplitOrder.empty() || _dimensionsSplitOrder == null ) {
        _dimensionsSplitOrder = OnHeapStarTreeV2BuilderHelper.computeDefaultSplitOrder();
      }
     */
    _dimensionsWithoutStarNode = config.getDimensionsWithoutStarNode();

    // metric
    _metricsName = new HashSet<>();
    _metricsSpecMap = new HashMap<>();
    _met2aggfuncPairs = config.getMetric2aggFuncPairs();
    _metricAggfuncPairsCount = _met2aggfuncPairs.size();
    for (Met2AggfuncPair pair: _met2aggfuncPairs) {
        _metricsName.add(pair.getMetricValue());
    }
    _metricsCount = _metricsName.size();

    List<MetricFieldSpec> _metricsSpecList = _segmentMetadata.getSchema().getMetricFieldSpecs();
    for (MetricFieldSpec metric: _metricsSpecList) {
      if (_metricsName.contains(metric.getName())) {
        _metricsSpecMap.put(metric.getName(), metric);
      }
    }

    // other initialisation
    _maxNumLeafRecords = config.getMaxNumLeafRecords();
    _rootNode = new TreeNode();
    _nodesCount++;
  }

  @Override
  public void build() throws IOException {

    /*
     TODO: figure out if the data in columns will always be sorted or not.
     If data is not sorted, write a logic for sorting of the data.
    */

    // Recursively construct the star tree
    constructStarTree(_rootNode, 0, _rawDocsCount, 0 );
  }

  @Override
  public void serialize(File starTreeFile, Map<String, ColumnIndexCreationInfo> indexCreationInfoMap)
      throws IOException {

  }

  @Override
  public List<String> getMetaData() {
    return null;
  }

  @Override
  public void close() throws IOException {

  }

  /**
   * Helper function to construct a star tree.
   *
   * @param node TreeNode to start with.
   * @param startDocId Start document id of the range to be grouped
   * @param endDocId End document id (exclusive) of the range to be grouped
   * @param level Name of the dimension to group on
   *
   * @return void.
   */
  private void constructStarTree(TreeNode node, int startDocId, int endDocId, int level) throws IOException {
    if (level == _dimensionsSplitOrder.size()) {
      return;
    }

    int numDocs = endDocId - startDocId;
    String splitDimensionName = _dimensionsSplitOrder.get(level);
    Map<Object, Pairs.IntPair> dimensionRangeMap = groupOnDimension(startDocId, endDocId, splitDimensionName);

    node._childDimensionName = splitDimensionName;

    // Reserve one space for star node
    Map<Object, TreeNode> children = new HashMap<>(dimensionRangeMap.size() + 1);

    node._children = children;
    for (Object key : dimensionRangeMap.keySet()) {
      Object childDimensionValue = key;
      Pairs.IntPair range = dimensionRangeMap.get(childDimensionValue);

      TreeNode child = new TreeNode();
      int childStartDocId = range.getLeft();
      child._startDocId = childStartDocId;
      int childEndDocId = range.getRight();
      child._endDocId = childEndDocId;
      children.put(childDimensionValue, child);
      if (childEndDocId - childStartDocId > _maxNumLeafRecords) {
        constructStarTree(child, childStartDocId, childEndDocId, level + 1);
      }
      _nodesCount++;
      AggregatedDataDocument aggDoc = getAggregatedDocument(childStartDocId, childEndDocId);
      child._aggDoc = aggDoc;
    }

    // Directly return if we don't need to create star-node
    if (_dimensionsWithoutStarNode != null && _dimensionsWithoutStarNode.contains(splitDimensionName)) {
      return;
    }

    // Create star node
    TreeNode starChild = new TreeNode();
    _nodesCount++;
    children.put(-1, starChild);
    starChild._dimensionName = splitDimensionName;
    starChild._dimensionValue = "ALL";
    starChild._startDocId = startDocId;
    starChild._endDocId = endDocId;

    if (endDocId - startDocId > _maxNumLeafRecords) {
      constructStarTree(starChild, startDocId, startDocId, level + 1);
    }
  }

  /**
   * Group all documents based on a dimension's value.
   *
   * @param startDocId Start document id of the range to be grouped
   * @param endDocId End document id (exclusive) of the range to be grouped
   * @param dimensionName Name of the dimension to group on
   *
   * @return Map from dimension value to a pair of start docId and end docId (exclusive)
   */
  private Map<Object, Pairs.IntPair> groupOnDimension(int startDocId, int endDocId, String dimensionName) {
    DimensionFieldSpec dimensionFieldSpec = _dimensionsSpecMap.get(dimensionName);

    return getRangeMap(dimensionFieldSpec.getDataType(), startDocId, endDocId, dimensionName);
  }

  /**
   * Helper function to get the unique value range map for a column
   *
   * @param dataType Data type of the column.
   * @param startDocId Start document id of the range to be grouped
   * @param endDocId End document id (exclusive) of the range to be grouped
   * @param dimensionName Name of the dimension to group on
   *
   * @return Range Map.
   */
  private Map<Object, Pairs.IntPair> getRangeMap (FieldSpec.DataType dataType, int startDocId, int endDocId, String dimensionName) {
    Map<Object, Pairs.IntPair> rangeMap = new HashMap<>();
    PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, dimensionName);
    Object currentValue = readHelper(columnReader, dataType, startDocId);

    int groupStartDocId = startDocId;

    for (int i = startDocId + 1; i < endDocId; i++) {
      Object value = readHelper(columnReader, dataType, i);
      if (!value.equals(currentValue)) {
        int groupEndDocId = i + 1;
        rangeMap.put(currentValue, new Pairs.IntPair(groupStartDocId, groupEndDocId));
        currentValue = value;
        groupStartDocId = groupEndDocId;
      }
    }
    rangeMap.put(currentValue, new Pairs.IntPair(groupStartDocId, endDocId));

    return rangeMap;
  }

  /**
   * Helper function to read value of a doc in a column
   *
   * @param reader Pinot segment column reader
   * @param dataType Data type of the column.
   * @param docId document Id for which data has to be read.
   *
   * @return Object
   */
  private Object readHelper(PinotSegmentColumnReader reader, FieldSpec.DataType dataType, int docId) {
    switch (dataType) {
      case INT:
        return reader.readInt(docId);
      case FLOAT:
        return reader.readFloat(docId);
      case LONG:
        return reader.readLong(docId);
      case DOUBLE:
        return reader.readDouble(docId);
      case STRING:
        return reader.readString(docId);
    }

    return null;
  }

  /**
   * Create a aggregated document for this range.
   *
   * @param startDocId Start document id of the range to be grouped
   * @param endDocId End document id (exclusive) of the range to be grouped
   *
   * @return list of all metric2aggfunc value.
   */
  private AggregatedDataDocument getAggregatedDocument(int startDocId, int endDocId) {
    int val = 0;
    AggregatedDataDocument aggDoc = new AggregatedDataDocument();
    for (Met2AggfuncPair pair : _met2aggfuncPairs) {
      String metric = pair.getMetricValue();
      String aggfunc = pair.getAggregatefunction();

      if (aggfunc == "SUM") {
        val = calculateSum(metric, startDocId, endDocId);
        aggDoc.setSum(val);
      } else if (aggfunc == "MAX") {
        val = calculateMax(metric, startDocId, endDocId);
        aggDoc.setMax(val);
      } else if (aggfunc == "MIN") {
        val = calculateMin(metric, startDocId, endDocId);
        aggDoc.setMin(val);
      }
    }

    /*
     TODO: write a logic for calculating the count(*)
     val = calculateCount(metric, startDocId, endDocId);
     aggDoc.setCount(val);
    */

    return aggDoc;
  }

  /**
   * Calculate SUM of the range.
   *
   * @param metricName name of the metric for which sum has to be calculated.
   * @param startDocId Start document id of the range to be grouped.
   * @param endDocId End document id (exclusive) of the range to be grouped
   *
   * @return sum
   */
  private Integer calculateSum(String metricName, Integer startDocId, Integer endDocId) {
    int sum = 0;
    PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, metricName);
    for (int i = startDocId; i < endDocId; i++) {
      Object currentValue = columnReader.readInt(startDocId);;
      sum += (int)currentValue;
    }
    return sum;
  }

  /**
   * Calculate MAX of the range.
   *
   * @param metricName name of the metric for which max has to be calculated.
   * @param startDocId Start document id of the range to be grouped.
   * @param endDocId End document id (exclusive) of the range to be grouped
   *
   * @return max
   */
  private Integer calculateMax(String metricName, Integer startDocId, Integer endDocId) {
    int max = Integer.MIN_VALUE;
    PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, metricName);
    for (int i = startDocId; i < endDocId; i++) {
      Object currentValue = columnReader.readInt(startDocId);;
      if ((int)currentValue > max ) {
        max = (int)currentValue;
      }
    }
    return max;
  }

  /**
   * Calculate MIN of the range.
   *
   * @param metricName name of the metric for which min has to be calculated.
   * @param startDocId Start document id of the range to be grouped.
   * @param endDocId End document id (exclusive) of the range to be grouped
   *
   * @return min
   */
  private Integer calculateMin(String metricName, Integer startDocId, Integer endDocId) {
    int min = Integer.MAX_VALUE;
    PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, metricName);
    for (int i = startDocId; i < endDocId; i++) {
      Object currentValue = columnReader.readInt(startDocId);;
      if ((int)currentValue < min ) {
        min = (int)currentValue;
      }
    }
    return min;
  }
}
