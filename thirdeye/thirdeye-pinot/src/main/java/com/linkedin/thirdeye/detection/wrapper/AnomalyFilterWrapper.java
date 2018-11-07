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

package com.linkedin.thirdeye.detection.wrapper;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.linkedin.thirdeye.datalayer.dto.DetectionConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.detection.ConfigUtils;
import com.linkedin.thirdeye.detection.DataProvider;
import com.linkedin.thirdeye.detection.DetectionPipeline;
import com.linkedin.thirdeye.detection.DetectionPipelineResult;
import com.linkedin.thirdeye.detection.spi.components.AnomalyFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.collections.MapUtils;


/**
 * This anomaly filter wrapper allows user to plug in filter rules in the detection pipeline.
 */
public class AnomalyFilterWrapper extends DetectionPipeline {
  private static final String PROP_NESTED = "nested";
  private static final String PROP_CLASS_NAME = "className";
  private static final String PROP_METRIC_URN = "metricUrn";
  private static final String PROP_FILTER = "filter";

  private final List<Map<String, Object>> nestedProperties;
  private final AnomalyFilter anomalyFilter;
  private String metricUrn;

  public AnomalyFilterWrapper(DataProvider provider, DetectionConfigDTO config, long startTime, long endTime)
      throws Exception {
    super(provider, config, startTime, endTime);
    Map<String, Object> properties = config.getProperties();
    this.nestedProperties = ConfigUtils.getList(properties.get(PROP_NESTED));

    Preconditions.checkArgument(this.config.getProperties().containsKey(PROP_FILTER));
    String detectorReferenceKey = DetectionUtils.getReferenceKey(MapUtils.getString(config.getProperties(), PROP_FILTER));
    Preconditions.checkArgument(this.config.getComponents().containsKey(detectorReferenceKey));
    this.anomalyFilter = (AnomalyFilter) this.config.getComponents().get(detectorReferenceKey);

    this.metricUrn = MapUtils.getString(properties, PROP_METRIC_URN);
  }

  /**
   * Runs the nested pipelines and calls the isQualified method in the anomaly filter stage to check if an anomaly passes the filter.
   * @return the detection pipeline result
   * @throws Exception
   */
  @Override
  public final DetectionPipelineResult run() throws Exception {
    List<MergedAnomalyResultDTO> candidates = new ArrayList<>();
    for (Map<String, Object> properties : this.nestedProperties) {
      DetectionConfigDTO nestedConfig = new DetectionConfigDTO();

      Preconditions.checkArgument(properties.containsKey(PROP_CLASS_NAME), "Nested missing " + PROP_CLASS_NAME);
      nestedConfig.setId(this.config.getId());
      nestedConfig.setName(this.config.getName());
      nestedConfig.setProperties(properties);
      nestedConfig.setComponents(this.config.getComponents());
      if (this.metricUrn != null){
        properties.put(PROP_METRIC_URN, this.metricUrn);
      }
      DetectionPipeline pipeline = this.provider.loadPipeline(nestedConfig, this.startTime, this.endTime);

      DetectionPipelineResult intermediate = pipeline.run();
      candidates.addAll(intermediate.getAnomalies());
    }

    Collection<MergedAnomalyResultDTO> anomalies =
        Collections2.filter(candidates, mergedAnomaly -> mergedAnomaly != null && !mergedAnomaly.isChild() && anomalyFilter.isQualified(
            DetectionUtils.getDataForSpec(provider, anomalyFilter.getInputDataSpec(mergedAnomaly))));

    return new DetectionPipelineResult(new ArrayList<>(anomalies));
  }
}
