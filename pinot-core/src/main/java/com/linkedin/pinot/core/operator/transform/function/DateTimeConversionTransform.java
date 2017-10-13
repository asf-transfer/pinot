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
package com.linkedin.pinot.core.operator.transform.function;

import java.lang.reflect.Array;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.commons.lang3.EnumUtils;

import com.google.common.base.Preconditions;
import com.linkedin.pinot.common.data.DateTimeFieldSpec;
import com.linkedin.pinot.common.data.DateTimeFieldSpec.TimeFormat;
import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.utils.time.DateTimeFieldSpecUtils;
import com.linkedin.pinot.core.common.BlockValSet;
import com.linkedin.pinot.core.operator.transform.DateTimeConvertor;
import com.linkedin.pinot.core.operator.transform.DateTimeConvertor.DateTimeTransformUnit;
import com.linkedin.pinot.core.operator.transform.DateTimeConvertorFactory;
import com.linkedin.pinot.core.plan.DocIdSetPlanNode;

/**
 * This class implements the date time conversion transform.
 * <ul>
 * <li>This udf should be invoked with arguments:</li>
 * <ul>
 * <li>1. Column name to convert eg. Date</li>
 * <li>2. format(as defined in {@link DateTimeFieldSpec}) of the input column. eg: 1:HOURS:EPOCH,
 * 1:DAYS:SIMPLE_DATE_FORMAT:yyyyMMdd</li>
 * <li>3. format(as defined in {@link DateTimeFieldSpec}) of the output expected. eg:
 * 1:MILLISECONDS:EPOCH, 1:WEEKS:EPOCH, 1:DAYS:SIMPLE_DATE_FORMAT:yyyyMMdd</li>
 * <li>4. granularity to bucket data into eg: 1:HOURS, 15:MINUTES</li>
 * </ul>
 * </ul>
 * <ul>
 * <li>End to end example:</li>
 * <ul>
 * <li>if Date column is expressed in millis, and output is expected in millis but bucketed to 15
 * minutes,</li>
 * <li>dateTimeConvert(Date, '1:MILLISECONDS:EPOCH', '1:MILLISECONDS:EPOCH', '15:MINUTES')</li>
 * <li>if Date column is expressed in hoursSinceEpoch, and output is expected in weeksSinceEpoch
 * bucketed to weeks</li>
 * <li>dateTimeConvert(Date, '1:HOURS:EPOCH', '1:WEEKS:EPOCH', '1:WEEKS')</li>
 * </ul>
 * </ul>
 * <ul>
 * <li>Outputs:</li>
 * <ul>
 * <li>Time values converted to the desired format and bucketed to desired granularity</li>
 * </ul>
 * </ul>
 */
@NotThreadSafe
public class DateTimeConversionTransform implements TransformFunction {
  private static final String TRANSFORM_NAME = "dateTimeConvert";
  private long[] _output = null;

  @Override
  public <T> T transform(int length, BlockValSet... input) {
    Preconditions.checkArgument(input.length == 4, TRANSFORM_NAME + " expects four arguments");

    String[] inputDateTimeFormat = input[1].getStringValuesSV();
    String inputFormat = inputDateTimeFormat[0];
    String[] outputDateTimeFormat = input[2].getStringValuesSV();
    String outputFormat = outputDateTimeFormat[0];
    String[] outputDateTimeGranularity = input[3].getStringValuesSV();
    String outputGranularity = outputDateTimeGranularity[0];

    Preconditions.checkArgument(DateTimeFieldSpecUtils.isValidFormat(inputFormat));
    Preconditions.checkArgument(isValidFormat(outputFormat));
    Preconditions.checkArgument(DateTimeFieldSpecUtils.isValidGranularity(outputGranularity));

    if (_output == null || _output.length < length) {
      _output = new long[Math.max(length, DocIdSetPlanNode.MAX_DOC_PER_CALL)];
    }

    DataType valueType = input[0].getValueType();
    Object inputValues = null;
    switch (valueType) {
      case STRING:
        inputValues = input[0].getStringValuesSV();
        break;
      case INT:
        inputValues = input[0].getIntValuesSV();
        break;
     case LONG:
      default:
        inputValues = input[0].getLongValuesSV();
        break;
    }

    DateTimeConvertor dateTimeConvertor =
        DateTimeConvertorFactory.getDateTimeConvertorFromFormats(inputFormat, outputFormat, outputGranularity);
    for (int i = 0; i < Array.getLength(inputValues); i ++) {
      _output[i] = dateTimeConvertor.convert(Array.get(inputValues, i));
    }
    return (T) _output;
  }

  @Override
  public FieldSpec.DataType getOutputType() {
    return FieldSpec.DataType.LONG;
  }

  @Override
  public String getName() {
    return TRANSFORM_NAME;
  }

  /**
   * Check correctness of format of {@link DateTimeFieldSpec}, but with TimeUnit extended to
   * {@link DateTimeTransformUnit}
   * @param format
   * @return
   */
  private boolean isValidFormat(String format) {

    Preconditions.checkNotNull(format);

    String[] formatTokens =
        format.split(DateTimeFieldSpecUtils.COLON_SEPARATOR, DateTimeFieldSpecUtils.MAX_FORMAT_TOKENS);
    Preconditions.checkArgument(
        formatTokens.length == DateTimeFieldSpecUtils.MIN_FORMAT_TOKENS ||
        formatTokens.length == DateTimeFieldSpecUtils.MAX_FORMAT_TOKENS,
        DateTimeFieldSpecUtils.FORMAT_TOKENS_ERROR_STR);
    Preconditions.checkArgument(
        formatTokens[DateTimeFieldSpecUtils.FORMAT_SIZE_POSITION].matches(DateTimeFieldSpecUtils.NUMBER_REGEX),
        DateTimeFieldSpecUtils.FORMAT_PATTERN_ERROR_STR);
    Preconditions.checkArgument(
        EnumUtils.isValidEnum(DateTimeConvertor.DateTimeTransformUnit.class, formatTokens[DateTimeFieldSpecUtils.FORMAT_UNIT_POSITION]),
        DateTimeFieldSpecUtils.FORMAT_PATTERN_ERROR_STR);

    if (formatTokens.length == DateTimeFieldSpecUtils.MIN_FORMAT_TOKENS) {
      Preconditions.checkArgument(
          formatTokens[DateTimeFieldSpecUtils.FORMAT_TIMEFORMAT_POSITION].equals(TimeFormat.EPOCH.toString()),
          DateTimeFieldSpecUtils.TIME_FORMAT_ERROR_STR);
    } else {
      Preconditions.checkArgument(
          formatTokens[DateTimeFieldSpecUtils.FORMAT_TIMEFORMAT_POSITION].equals(TimeFormat.SIMPLE_DATE_FORMAT.toString()),
          DateTimeFieldSpecUtils.TIME_FORMAT_ERROR_STR);
      Preconditions.checkNotNull(formatTokens[DateTimeFieldSpecUtils.FORMAT_PATTERN_POSITION]);
    }
    return true;
  }

}
