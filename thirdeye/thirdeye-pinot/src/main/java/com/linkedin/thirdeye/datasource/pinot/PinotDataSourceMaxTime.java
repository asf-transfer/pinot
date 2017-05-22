package com.linkedin.thirdeye.datasource.pinot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.client.ResultSetGroup;
import com.linkedin.thirdeye.api.TimeSpec;
import com.linkedin.thirdeye.dashboard.Utils;
import com.linkedin.thirdeye.datalayer.dto.DatasetConfigDTO;
import com.linkedin.thirdeye.datasource.DAORegistry;
import com.linkedin.thirdeye.datasource.ThirdEyeCacheRegistry;
import com.linkedin.thirdeye.util.ThirdEyeUtils;

/**
 * This class contains methods to return max date time for datasets in pinot
 */
public class PinotDataSourceMaxTime {

  private static final Logger LOGGER = LoggerFactory.getLogger(PinotDataSourceMaxTime.class);
  private static final DAORegistry DAO_REGISTRY = DAORegistry.getInstance();
  private static final ThirdEyeCacheRegistry CACHE_REGISTRY = ThirdEyeCacheRegistry.getInstance();
  private final Map<String, Long> collectionToPrevMaxDataTimeMap = new ConcurrentHashMap<String, Long>();

  private final static String COLLECTION_MAX_TIME_QUERY_TEMPLATE = "SELECT max(%s) FROM %s WHERE %s >= %s";

  public PinotDataSourceMaxTime() {

  }

  /**
   * Returns the max time in millis for dataset in pinot
   * @param dataset
   * @return max date time in millis
   */
  public long getMaxDateTime(String dataset) {
    LOGGER.info("Loading maxDataTime cache {}", dataset);
    long maxTime = 0;
    try {
      DatasetConfigDTO datasetConfig = DAO_REGISTRY.getDatasetConfigDAO().findByDataset(dataset);
      // By default, query only offline, unless dataset has been marked as realtime
      String tableName = ThirdEyeUtils.computeTableName(dataset);
      TimeSpec timeSpec = ThirdEyeUtils.getTimestampTimeSpecFromDatasetConfig(datasetConfig);
      long prevMaxDataTime = getPrevMaxDataTime(dataset, timeSpec);
      String maxTimePql = String.format(COLLECTION_MAX_TIME_QUERY_TEMPLATE, timeSpec.getColumnName(), tableName,
          timeSpec.getColumnName(), prevMaxDataTime);
      PinotQuery maxTimePinotQuery = new PinotQuery(maxTimePql, tableName);
      CACHE_REGISTRY.getResultSetGroupCache().refresh(maxTimePinotQuery);
      ResultSetGroup resultSetGroup = CACHE_REGISTRY.getResultSetGroupCache().get(maxTimePinotQuery);
      if (resultSetGroup.getResultSetCount() == 0 || resultSetGroup.getResultSet(0).getRowCount() == 0) {
        LOGGER.info("resultSetGroup is Empty for collection {} is {}", tableName, resultSetGroup);
        this.collectionToPrevMaxDataTimeMap.remove(dataset);
      } else {
        long endTime = new Double(resultSetGroup.getResultSet(0).getDouble(0)).longValue();
        this.collectionToPrevMaxDataTimeMap.put(dataset, endTime);
        // endTime + 1 to make sure we cover the time range of that time value.
        String timeFormat = timeSpec.getFormat();
        if (StringUtils.isBlank(timeFormat) || TimeSpec.SINCE_EPOCH_FORMAT.equals(timeFormat)) {
          maxTime = timeSpec.getDataGranularity().toMillis(endTime + 1) - 1;
        } else {
          DateTimeFormatter inputDataDateTimeFormatter =
              DateTimeFormat.forPattern(timeFormat).withZone(Utils.getDataTimeZone(dataset));
          DateTime endDateTime = DateTime.parse(String.valueOf(endTime), inputDataDateTimeFormatter);
          Period oneBucket = datasetConfig.bucketTimeGranularity().toPeriod();
          maxTime = endDateTime.plus(oneBucket).getMillis() - 1;
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Exception getting maxTime from collection: {}", dataset, e);
      this.collectionToPrevMaxDataTimeMap.remove(dataset);
    }
    if (maxTime <= 0) {
      maxTime = System.currentTimeMillis();
    }
    return maxTime;
  }


  private long getPrevMaxDataTime(String collection, TimeSpec timeSpec) {
    if (this.collectionToPrevMaxDataTimeMap.containsKey(collection)) {
      return collectionToPrevMaxDataTimeMap.get(collection);
    }
    return 0;
  }

}
