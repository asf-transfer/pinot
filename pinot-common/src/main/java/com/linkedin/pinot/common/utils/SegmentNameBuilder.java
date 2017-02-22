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
package com.linkedin.pinot.common.utils;

public class SegmentNameBuilder {
  public static String buildBasic(String tableName, Object minTimeValue, Object maxTimeValue, String postfix, int sequenceId) {
    if (sequenceId == -1) {
      return StringUtil.join("_", tableName, minTimeValue.toString(), maxTimeValue.toString(), postfix);
    } else {
      return StringUtil.join("_", tableName, minTimeValue.toString(), maxTimeValue.toString(), postfix, Integer.toString(sequenceId));
    }
  }

  public static String buildBasic(String tableName, String postfix, int sequenceId) {
    if (sequenceId == -1 ) {
      return StringUtil.join("_", tableName, postfix);
    } else {
      return StringUtil.join("_", tableName, postfix, Integer.toString(sequenceId));
    }
  }
}