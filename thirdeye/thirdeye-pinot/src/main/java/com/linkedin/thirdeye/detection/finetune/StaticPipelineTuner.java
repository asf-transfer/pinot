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

package com.linkedin.thirdeye.detection.finetune;

import com.linkedin.thirdeye.detection.DataProvider;
import com.linkedin.thirdeye.detection.InputData;
import com.linkedin.thirdeye.detection.InputDataSpec;
import com.linkedin.thirdeye.detection.algorithm.stage.StageUtils;
import java.util.Map;


public abstract class StaticPipelineTuner implements PipelineTuner {
  @Override
  public Map<String, Object> tune(Map<String, Object> pipelineSpec, DataProvider provider) {
    return this.tune(pipelineSpec, StageUtils.getDataForSpec(provider, this.getInputDataSpec()));
  }

  abstract Map<String, Object> tune(Map<String, Object> configDTO, InputData data);

  abstract InputDataSpec getInputDataSpec();
}
