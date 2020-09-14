/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.plugin.inputformat.thrift;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.data.readers.AbstractDefaultRecordExtractor;
import org.apache.pinot.spi.data.readers.RecordExtractorConfig;
import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;


/**
 * Extractor for records of Thrift input
 */
public class ThriftRecordExtractor extends AbstractDefaultRecordExtractor<TBase, TBase> {

  private Map<String, Integer> _fieldIds;
  private Set<String> _fields;
  private boolean _extractAll = false;

  @Override
  public void init(@Nullable Set<String> fields, RecordExtractorConfig recordExtractorConfig) {
    _fields = fields;
    _fieldIds = ((ThriftRecordExtractorConfig) recordExtractorConfig).getFieldIds();
    if (fields == null || fields.isEmpty()) {
      _extractAll = true;
    }
  }

  @Override
  public GenericRow extract(TBase from, GenericRow to) {
    if (_extractAll) {
      _fieldIds.entrySet().forEach(nameToId ->
          to.putValue(
              nameToId.getKey(),
              convert(from.getFieldValue(from.fieldForId(nameToId.getValue()))))
      );
    } else {
      for (String fieldName : _fields) {
        Object value = null;
        Integer fieldId = _fieldIds.get(fieldName);
        if (fieldId != null) {
          //noinspection unchecked
          value = from.getFieldValue(from.fieldForId(fieldId));
        }
        to.putValue(fieldName, convert(value));
      }
    }
    return to;
  }

  /**
   * Returns whether the object is a Thrift object.
   */
  @Override
  protected boolean isInstanceOfRecord(Object value) {
    return value instanceof TBase;
  }

  /**
   * Handles the conversion of each field of a Thrift object.
   */
  @Override
  protected Object convertRecord(TBase value) {
    Map<Object, Object> convertedRecord = new HashMap<>();
    for (TFieldIdEnum tFieldIdEnum: FieldMetaData.getStructMetaDataMap(value.getClass()).keySet()) {
      convertedRecord.put(tFieldIdEnum.getFieldName(), convert(value.getFieldValue(tFieldIdEnum)));
    }
    return convertedRecord;
  }
}
