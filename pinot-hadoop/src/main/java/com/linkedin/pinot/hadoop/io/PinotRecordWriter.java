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
package com.linkedin.pinot.hadoop.io;

import com.linkedin.pinot.common.utils.TarGzCompressionUtils;
import com.linkedin.pinot.core.indexsegment.generator.SegmentGeneratorConfig;
import com.linkedin.pinot.core.segment.creator.SegmentIndexCreationDriver;
import com.linkedin.pinot.core.segment.creator.impl.SegmentIndexCreationDriverImpl;
import com.linkedin.pinot.hadoop.job.JobConfigConstants;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Basic Single Threaded {@link RecordWriter}
 */
public class PinotRecordWriter<K, V> extends RecordWriter<K, V> {

    private final static Logger LOGGER = LoggerFactory.getLogger(PinotRecordWriter.class);

    private final TaskAttemptContext _context;
    private final SegmentGeneratorConfig _segmentConfig;
    private FileHandler _handler;
    // default max file size
    private long MAX_FILE_SIZE = 64 * 1000000L;
    private final Path _workDir;
    private final String _baseDataDir;
    private DataWriteSupport _dataWriteSupport;


    public PinotRecordWriter(SegmentGeneratorConfig segmentConfig, TaskAttemptContext context, Path workDir, DataWriteSupport dataWriteSupport) {
        _context = context;
        _segmentConfig = segmentConfig;
        _workDir = workDir;
        _baseDataDir = PinotOutputFormat.getTempSegmentDir(context) + "/data";
        String filename = PinotOutputFormat.getTableName(context);
        String extension = PinotOutputFormat.getFileFormat(context).name().toLowerCase();

        try {
            _handler = new FileHandler(_baseDataDir, filename, extension, MAX_FILE_SIZE);
            _handler.open(true);
            _dataWriteSupport = dataWriteSupport;
            _dataWriteSupport.init(segmentConfig, context);
        } catch (Exception e) {
            throw new RuntimeException("Error initialize PinotRecordReader", e);
        }
    }

    @Override
    public void write(K key, V value) throws IOException, InterruptedException {
        byte[] b = _dataWriteSupport.write(value);
        _handler.write(b);
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException, InterruptedException {
        _dataWriteSupport.close(context);
        _handler.close();
        File dir = new File(_baseDataDir);
        _segmentConfig.setSegmentName(PinotOutputFormat.getSegmentName(context));
        SegmentIndexCreationDriver driver = new SegmentIndexCreationDriverImpl();
        for (File f : dir.listFiles()) {
            createSegment(f.getPath(), driver);
        }

        final FileSystem fs = FileSystem.get(new Configuration());
        String localSegmentPath = new File(_segmentConfig.getOutDir(), _segmentConfig.getSegmentName()).getAbsolutePath();
        String localTarPath = getLocalTarFile(PinotOutputFormat.getTempSegmentDir(context));
        LOGGER.info("Trying to tar the segment to: {}", localTarPath);
        TarGzCompressionUtils.createTarGzOfDirectory(localSegmentPath, localTarPath);
        String hdfsTarPath = _workDir + "/segmentTar/" + _segmentConfig.getSegmentName() + JobConfigConstants.TARGZ;


        LOGGER.info("*********************************************************************");
        LOGGER.info("Copy from : {} to {}", localTarPath, hdfsTarPath);
        LOGGER.info("*********************************************************************");
        fs.copyFromLocalFile(true, true, new Path(localTarPath), new Path(hdfsTarPath));
        clean(PinotOutputFormat.getTempSegmentDir(context));
    }

    private void createSegment(String inputFile, SegmentIndexCreationDriver driver) {
        try {
            _segmentConfig.setInputFilePath(inputFile);
            driver.init(_segmentConfig);
            driver.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getLocalTarFile(String baseDir) {
        String localTarDir = baseDir + "/segmentTar";
        File f = new File(localTarDir);
        if (!f.exists()) {
            f.mkdirs();
        }
        return localTarDir + "/" + _segmentConfig.getSegmentName() + JobConfigConstants.TARGZ;
    }

    private void clean(String baseDir) {
        File f = new File(baseDir);
        if (f.exists()) {
            f.delete();
        }
    }
}
