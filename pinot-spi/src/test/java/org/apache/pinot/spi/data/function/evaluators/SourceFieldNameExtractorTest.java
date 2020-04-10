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
package org.apache.pinot.spi.data.function.evaluators;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pinot.spi.data.DimensionFieldSpec;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.TimeFieldSpec;
import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Tests that the source field names are extracted correctly
 */
public class SourceFieldNameExtractorTest {


  @Test
  public void testSourceFieldExtractorName() {

    Schema schema;

    // from groovy function
    schema = new Schema();
    DimensionFieldSpec dimensionFieldSpec = new DimensionFieldSpec("d1", FieldSpec.DataType.STRING, true);
    dimensionFieldSpec.setTransformFunction("Groovy({function}, argument1, argument2)");
    schema.addField(dimensionFieldSpec);

    List<String> extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertEquals(extract.size(), 2);
    Assert.assertTrue(extract.containsAll(Arrays.asList("argument1", "argument2")));

    // groovy function, no arguments
    schema = new Schema();
    dimensionFieldSpec = new DimensionFieldSpec("d1", FieldSpec.DataType.STRING, true);
    dimensionFieldSpec.setTransformFunction("Groovy({function})");
    schema.addField(dimensionFieldSpec);

    extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertTrue(extract.isEmpty());

    // Map implementation for Avro - map__KEYS indicates map is source column
    schema = new Schema();
    dimensionFieldSpec = new DimensionFieldSpec("map__KEYS", FieldSpec.DataType.INT, false);
    schema.addField(dimensionFieldSpec);

    extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertEquals(extract.size(), 1);
    Assert.assertTrue(extract.contains("map"));

    // Map implementation for Avro - map__VALUES indicates map is source column
    schema = new Schema();
    dimensionFieldSpec = new DimensionFieldSpec("map__VALUES", FieldSpec.DataType.LONG, false);
    schema.addField(dimensionFieldSpec);

    extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertEquals(extract.size(), 1);
    Assert.assertTrue(extract.contains("map"));

    // Time field spec

    // only incoming
    schema = new Schema();
    TimeFieldSpec timeFieldSpec = new TimeFieldSpec("time", FieldSpec.DataType.LONG, TimeUnit.MILLISECONDS);
    schema.addField(timeFieldSpec);

    extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertEquals(extract.size(), 1);
    Assert.assertTrue(extract.contains("time"));

    // incoming and outgoing same
    schema = new Schema();
    timeFieldSpec = new TimeFieldSpec("time", FieldSpec.DataType.LONG, TimeUnit.MILLISECONDS, "time", FieldSpec.DataType.LONG, TimeUnit.HOURS);
    schema.addField(timeFieldSpec);

    extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertEquals(extract.size(), 1);
    Assert.assertTrue(extract.contains("time"));

    // incoming and outgoing different
    schema = new Schema();
    timeFieldSpec = new TimeFieldSpec("in", FieldSpec.DataType.LONG, TimeUnit.MILLISECONDS, "out", FieldSpec.DataType.LONG, TimeUnit.MILLISECONDS);
    schema.addField(timeFieldSpec);

    extract = SourceFieldNameExtractor.extract(schema);
    Assert.assertEquals(extract.size(), 1);
    Assert.assertTrue(extract.contains("in"));
  }

}