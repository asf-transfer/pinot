/*
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

package com.linkedin.thirdeye.detection.spi.components;

import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.detection.spec.AbstractSpec;
import com.linkedin.thirdeye.detection.spi.model.InputData;
import com.linkedin.thirdeye.detection.spi.model.InputDataSpec;


public interface AnomalyFilter<T extends AbstractSpec> extends BaseComponent<T> {
  /**
   * Returns a data spec describing all required data(time series, aggregates, existing anomalies) to run this component.
   * Data is retrieved in one pass and cached between executions if possible.
   * @return input data spec
   */
  InputDataSpec getInputDataSpec(MergedAnomalyResultDTO anomaly);

  /**
   * Check if an anomaly is qualified to pass the filter
   * @param data data(time series, anomalies, etc.) as described by data spec
   * @return a boolean value to suggest if the anomaly should be filtered
   */
  boolean isQualified(InputData data);

}
