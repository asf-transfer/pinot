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
package com.linkedin.pinot.core.io.writer.impl.v2;

import com.google.common.base.Preconditions;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.core.io.writer.SingleColumnMultiValueWriter;
import com.linkedin.pinot.core.io.writer.impl.FixedByteSingleValueMultiColWriter;
import com.linkedin.pinot.core.segment.memory.PinotDataBuffer;
import com.linkedin.pinot.core.util.PinotDataCustomBitSet;
import com.linkedin.pinot.core.util.SizeUtil;
import java.io.File;
import java.nio.channels.FileChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storage Layout
 * ==============
 * There will be three sections HEADER section, BITMAP and RAW DATA
 * CHUNK OFFSET HEADER will contain one line per chunk, each line corresponding to the start offset
 * and length of the chunk
 * BITMAP This will contain sequence of bits. The number of bits will be equal to the
 * totalNumberOfValues.A bit is set to 1 if its start of a new docId. The number of bits set to 1
 * will be equal to the number of docs.
 * RAWDATA This simply has the actual multivalued data stored in sequence of int's. The number of
 * ints is equal to the totalNumberOfValues
 * We divide all the documents into groups referred to as CHUNK. Each CHUNK will
 * - Have the same number of documents.
 * - Started Offset of each CHUNK in the BITMAP will stored in the HEADER section. This is to speed
 * the look up.
 * Over all each look up will take log(NUM CHUNKS) for binary search + CHUNK to linear scan on the
 * bitmap to find the right offset in the raw data section
 */
public class FixedBitMultiValueWriter implements SingleColumnMultiValueWriter {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixedBitMultiValueWriter.class);

  private static int SIZE_OF_INT = 4;
  private static int NUM_COLS_IN_HEADER = 1;
  private static final int PREFERRED_NUM_VALUES_PER_CHUNK = 2048;

  private PinotDataBuffer indexDataBuffer;
  private PinotDataBuffer chunkOffsetsBuffer;
  private PinotDataBuffer bitsetBuffer;
  private PinotDataBuffer rawDataBuffer;

  private PinotDataCustomBitSet customBitSet;

  private FixedByteSingleValueMultiColWriter chunkOffsetsWriter;
  private FixedBitSingleValueWriter rawDataWriter;

  private int numChunks;
  int prevRowStartIndex = 0;
  int prevRowLength = 0;
  int prevRowId = -1;
  private int chunkOffsetHeaderSize;
  private int bitsetSize;
  private long rawDataSize;
  private long totalSize;
  private int docsPerChunk;

  public FixedBitMultiValueWriter(File file, int numDocs, int totalNumValues, int columnSizeInBits)
      throws Exception {

    float averageValuesPerDoc = totalNumValues / numDocs;
    this.docsPerChunk = (int) (Math.ceil(PREFERRED_NUM_VALUES_PER_CHUNK / averageValuesPerDoc));
    this.numChunks = (numDocs + docsPerChunk - 1) / docsPerChunk;
    chunkOffsetHeaderSize = numChunks * SIZE_OF_INT * NUM_COLS_IN_HEADER;
    bitsetSize = (totalNumValues + 7) / 8;
    rawDataSize =  SizeUtil.computeBytesRequired(totalNumValues, columnSizeInBits, SizeUtil.BIT_UNPACK_BATCH_SIZE);
    LOGGER.info("Allocating:{} for rawDataSize to store {} values of bits:{}", rawDataSize,
        totalNumValues, columnSizeInBits);
    totalSize = chunkOffsetHeaderSize + bitsetSize + rawDataSize;
    Preconditions.checkState(totalSize > 0 && totalSize < Integer.MAX_VALUE, "Total size exceeds 2GB for file: " + file.toString());
    this.indexDataBuffer = PinotDataBuffer.fromFile(file, 0, totalSize, ReadMode.mmap, FileChannel.MapMode.READ_WRITE,
        file.getAbsolutePath() + ".fixedBitMVWriter");
    chunkOffsetsBuffer = indexDataBuffer.view(0, chunkOffsetHeaderSize);
    int bitSetEnd = chunkOffsetHeaderSize + bitsetSize;
    bitsetBuffer = indexDataBuffer.view(chunkOffsetHeaderSize, bitSetEnd);
    rawDataBuffer = indexDataBuffer.view(bitSetEnd, bitSetEnd + rawDataSize);
    chunkOffsetsWriter = new FixedByteSingleValueMultiColWriter(chunkOffsetsBuffer, numDocs,
        NUM_COLS_IN_HEADER, new int[] {
            SIZE_OF_INT
    });
    customBitSet = PinotDataCustomBitSet.withDataBuffer(bitsetSize, bitsetBuffer);
    rawDataWriter = new FixedBitSingleValueWriter(rawDataBuffer, totalNumValues, columnSizeInBits);

  }

  public long getTotalSize() {
    return totalSize;
  }

  @Override
  public void close() {
    rawDataWriter.close();
    customBitSet.close();
    chunkOffsetsWriter.close();
    this.indexDataBuffer.close();

    chunkOffsetsBuffer = null;
    bitsetBuffer = null;
    rawDataBuffer = null;
    customBitSet = null;
    chunkOffsetsWriter = null;
    rawDataWriter = null;
  }

  private int updateHeader(int rowId, int length) {
    assert (rowId == prevRowId + 1);
    int newStartIndex = prevRowStartIndex + prevRowLength;
    if (rowId % docsPerChunk == 0) {
      int chunkId = rowId / docsPerChunk;
      chunkOffsetsWriter.setInt(chunkId, 0, newStartIndex);
    }
    customBitSet.setBit(newStartIndex);
    prevRowStartIndex = newStartIndex;
    prevRowLength = length;
    prevRowId = rowId;
    return newStartIndex;
  }

  @Override
  public void setCharArray(int row, char[] charArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");
  }

  @Override
  public void setShortArray(int row, short[] shortsArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");
  }

  @Override
  public void setIntArray(int row, int[] intArray) {
    int newStartIndex = updateHeader(row, intArray.length);
    for (int i = 0; i < intArray.length; i++) {
      rawDataWriter.setInt(newStartIndex + i, intArray[i]);
    }
  }

  @Override
  public void setLongArray(int row, long[] longArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");
  }

  @Override
  public void setFloatArray(int row, float[] floatArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");
  }

  @Override
  public void setDoubleArray(int row, double[] doubleArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");

  }

  @Override
  public void setStringArray(int row, String[] stringArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");
  }

  @Override
  public void setBytesArray(int row, byte[][] bytesArray) {
    throw new UnsupportedOperationException("Only int data type is supported in fixedbit format");
  }

}
