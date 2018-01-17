/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.thirdeye.hadoop.push;

import static com.linkedin.thirdeye.hadoop.push.SegmentPushPhaseConstants.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.common.utils.FileUploadUtils;
import com.linkedin.thirdeye.hadoop.config.ThirdEyeConfigProperties;
import com.linkedin.thirdeye.hadoop.config.ThirdEyeConstants;

/**
 * This class pushed pinot segments generated by SegmentCreation
 * onto the pinot cluster
 */
public class SegmentPushPhase  extends Configured {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentPushPhase.class);
  private final String name;
  private final Properties props;
  private String[] hosts;
  private String port;
  private String tablename;
  private boolean uploadSuccess = true;
  private String segmentName = null;
  private String segmentPushUDFClass;
  SegmentPushControllerAPIs segmentPushControllerAPIs;


  public SegmentPushPhase(String jobName, Properties properties) throws Exception {
    super(new Configuration());
    name = jobName;
    props = properties;
  }

  public void run() throws Exception {
    Configuration configuration = new Configuration();
    FileSystem fs = FileSystem.get(configuration);

    long startTime = System.currentTimeMillis();

    String segmentPath = getAndSetConfiguration(configuration, SEGMENT_PUSH_INPUT_PATH);
    LOGGER.info("Segment path : {}", segmentPath);
    hosts = getAndSetConfiguration(configuration, SEGMENT_PUSH_CONTROLLER_HOSTS).split(ThirdEyeConstants.FIELD_SEPARATOR);
    port = getAndSetConfiguration(configuration, SEGMENT_PUSH_CONTROLLER_PORT);
    tablename = getAndCheck(ThirdEyeConfigProperties.THIRDEYE_TABLE_NAME.toString());
    segmentPushUDFClass = props.getProperty(SEGMENT_PUSH_UDF_CLASS.toString(), DefaultSegmentPushUDF.class.getCanonicalName());

    Path path = new Path(segmentPath);
    FileStatus[] fileStatusArr = fs.globStatus(path);
    for (FileStatus fileStatus : fileStatusArr) {
      if (fileStatus.isDirectory()) {
        pushDir(fs, fileStatus.getPath());
      } else {
        pushOneTarFile(fs, fileStatus.getPath());
      }
    }
    long endTime = System.currentTimeMillis();

    if (uploadSuccess && segmentName != null) {
      props.setProperty(SEGMENT_PUSH_START_TIME.toString(), String.valueOf(startTime));
      props.setProperty(SEGMENT_PUSH_END_TIME.toString(), String.valueOf(endTime));

      segmentPushControllerAPIs = new SegmentPushControllerAPIs(hosts, port);
      LOGGER.info("Deleting segments overlapping to {} from table {}  ", segmentName, tablename);
      segmentPushControllerAPIs.deleteOverlappingSegments(tablename, segmentName);

      try {
        LOGGER.info("Initializing SegmentPushUDFClass:{}", segmentPushUDFClass);
        Constructor<?> constructor = Class.forName(segmentPushUDFClass).getConstructor();
        SegmentPushUDF segmentPushUDF = (SegmentPushUDF) constructor.newInstance();
        segmentPushUDF.emitCustomEvents(props);
      } catch (Exception e) {
        throw new IOException(e);
      }
    }

  }

  public void pushDir(FileSystem fs, Path path) throws Exception {
    LOGGER.info("******** Now uploading segments tar from dir: {}", path);
    FileStatus[] fileStatusArr = fs.listStatus(new Path(path.toString() + "/"));
    for (FileStatus fileStatus : fileStatusArr) {
      if (fileStatus.isDirectory()) {
        pushDir(fs, fileStatus.getPath());
      } else {
        pushOneTarFile(fs, fileStatus.getPath());
      }
    }
  }

  public void pushOneTarFile(FileSystem fs, Path path) throws Exception {
    String fileName = path.getName();
    if (!fileName.endsWith(".tar.gz")) {
      return;
    }
    long length = fs.getFileStatus(path).getLen();
    for (String host : hosts) {
      InputStream inputStream = null;
      try {
        inputStream = fs.open(path);
        fileName = fileName.split(".tar")[0];
        if (fileName.lastIndexOf(ThirdEyeConstants.SEGMENT_JOINER) != -1) {
          segmentName = fileName.substring(0, fileName.lastIndexOf(ThirdEyeConstants.SEGMENT_JOINER));
        }
        LOGGER.info("******** Uploading file: {} to Host: {} and Port: {} *******", fileName, host, port);
        try {
          int responseCode = FileUploadUtils.uploadSegment(host, Integer.parseInt(port), fileName, inputStream);
          LOGGER.info("Response code: {}", responseCode);

          if (uploadSuccess == true && responseCode != 200) {
            uploadSuccess = false;
          }

        } catch (Exception e) {
          LOGGER.error("******** Error Uploading file: {} to Host: {} and Port: {}  *******", fileName, host, port);
          LOGGER.error("Caught exception during upload", e);
          throw new RuntimeException("Got Error during send tar files to push hosts!");
        }
      } finally {
        inputStream.close();
      }
    }
  }


  private String getAndSetConfiguration(Configuration configuration,
      SegmentPushPhaseConstants constant) {
    String value = getAndCheck(constant.toString());
    configuration.set(constant.toString(), value);
    return value;
  }

  private String getAndCheck(String propName) {
    String propValue = props.getProperty(propName);
    if (propValue == null) {
      throw new IllegalArgumentException(propName + " required property");
    }
    return propValue;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("usage: config.properties");
    }

    Properties props = new Properties();
    props.load(new FileInputStream(args[0]));

    SegmentPushPhase job = new SegmentPushPhase("segment_push_job", props);
    job.run();
  }


}
