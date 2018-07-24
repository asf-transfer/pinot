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

package com.linkedin.pinot.core.startreeV2;

import java.util.Map;
import java.io.IOException;
import com.linkedin.pinot.core.common.DataSource;
import com.linkedin.pinot.core.startree.StarTree;


public class StarTreeV2Impl implements StarTreeV2 {

  private final StarTree _starTree;
  private final Map<String, StarTreeV2DimensionDataSource> _dimensionDataSources;
  private final Map<String, StarTreeV2AggfunColumnPairDataSource> _aggfuncColumnPairSources;

  public StarTreeV2Impl(StarTree starTree, Map<String, StarTreeV2DimensionDataSource> dimensionDataSources,
      Map<String, StarTreeV2AggfunColumnPairDataSource> aggfuncColumnPairSources) {

    _starTree = starTree;
    _dimensionDataSources = dimensionDataSources;
    _aggfuncColumnPairSources = aggfuncColumnPairSources;
  }

  @Override
  public StarTree getStarTree() throws IOException {
    return _starTree;
  }

  @Override
  public DataSource getDataSource(String column) throws Exception {
    if (_dimensionDataSources.containsKey(column)) {
      return _dimensionDataSources.get(column);
    } else {
      return _aggfuncColumnPairSources.get(column);
    }
  }

  @Override
  public void close() throws IOException {
    if (_starTree != null) {
      _starTree.close();
    }

    if (_dimensionDataSources.size() > 0) {
      for (String dimension: _dimensionDataSources.keySet()) {
        StarTreeV2DimensionDataSource source = _dimensionDataSources.get(dimension);
        source.getForwardIndex().close();
      }
    }

    if (_aggfuncColumnPairSources.size() > 0) {
      for (String pair: _aggfuncColumnPairSources.keySet()) {
        StarTreeV2AggfunColumnPairDataSource source = _aggfuncColumnPairSources.get(pair);
        source.getForwardIndex().close();
      }
    }
  }
}
