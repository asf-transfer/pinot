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
package com.linkedin.pinot.core.segment;

import com.linkedin.pinot.common.utils.SegmentNameBuilder;
import com.linkedin.pinot.core.segment.creator.AbstractColumnStatisticsCollector;


public class SimpleSegmentNameGenerator implements DefaultSegmentNameGenerator {
  public String getSegmentName(AbstractColumnStatisticsCollector statsCollector, SegmentNameConfig config) throws Exception {
    final String timeColumnName = config.getTimeColumnName();
    String segmentName;
    if (timeColumnName != null && timeColumnName.length() > 0) {
      final Object minTimeValue = statsCollector.getMinValue();
      final Object maxTimeValue = statsCollector.getMaxValue();
      segmentName = SegmentNameBuilder
          .buildBasic(config.getTableName(), minTimeValue, maxTimeValue, config.getSegmentNamePostfix());
    } else {
      segmentName = SegmentNameBuilder.buildBasic(config.getTableName(), config.getSegmentNamePostfix());
    }
    return segmentName;
  }
}
