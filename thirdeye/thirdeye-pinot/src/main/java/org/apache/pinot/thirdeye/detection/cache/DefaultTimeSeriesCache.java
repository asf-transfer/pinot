package org.apache.pinot.thirdeye.detection.cache;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.pinot.thirdeye.auto.onboard.AutoOnboardUtility;
import org.apache.pinot.thirdeye.common.time.TimeSpec;
import org.apache.pinot.thirdeye.datalayer.bao.DatasetConfigManager;
import org.apache.pinot.thirdeye.datalayer.bao.MetricConfigManager;
import org.apache.pinot.thirdeye.datasource.RelationalThirdEyeResponse;
import org.apache.pinot.thirdeye.datasource.ThirdEyeResponse;
import org.apache.pinot.thirdeye.datasource.cache.QueryCache;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultTimeSeriesCache implements TimeSeriesCache {

  private static final Logger LOG = LoggerFactory.getLogger(AutoOnboardUtility.class);

  private final MetricConfigManager metricDAO;
  private final DatasetConfigManager datasetDAO;
  private final QueryCache cache;
  private CouchbaseCacheDAO cacheDAO = null;

  public DefaultTimeSeriesCache(MetricConfigManager metricDAO, DatasetConfigManager datasetDAO, QueryCache cache) {
    this.metricDAO = metricDAO;
    this.datasetDAO = datasetDAO;
    this.cache = cache;
    this.cacheDAO = new CouchbaseCacheDAO();
  }

  public ThirdEyeResponse fetchTimeSeries(ThirdEyeCacheRequestContainer rc) throws Exception {
    LOG.info("trying to fetch data from cache...");

    ThirdEyeResponse response = null;
    ThirdEyeCacheResponse cacheResponse = cacheDAO.tryFetchExistingTimeSeries(rc);

    DateTime start = rc.getRequest().getStartTimeInclusive();
    DateTime end = rc.getRequest().getEndTimeExclusive();

    if (cacheResponse == null || cacheResponse.isMissingSlice(start, end)) {
      LOG.info("cache miss or bad cache response received");
      response = this.cache.getQueryResult(rc.getRequest());
      this.insertTimeSeriesIntoCache(rc.getDetectionId(), response);
    } else {

      TimeSpec responseSpec = cacheResponse.getTimeSpec();
      Period granularityPeriod = responseSpec.getDataGranularity().toPeriod();

      DateTime cacheStart = new DateTime(Long.valueOf(cacheResponse.getStart()), start.getZone());
      DateTime cacheEnd = new DateTime(Long.valueOf(cacheResponse.getEnd()), end.getZone());

      // keep adding from beginning
      int startIndexOffset = 0;
      while (!cacheStart.isEqual(start)) {
        cacheStart = cacheStart.withPeriodAdded(granularityPeriod,1);
        startIndexOffset++;
      }

      // keep subtracting from end
      int endIndexOffset = 0;
      while (!cacheEnd.isEqual(end)) {
        cacheEnd = cacheEnd.withPeriodAdded(granularityPeriod,-1);
        endIndexOffset++;
      }

      List<String[]> rowList = cacheResponse.getMetrics();

      List<String[]> rows = rowList
          .subList(startIndexOffset, rowList.size() - 1 - endIndexOffset);

      response = new RelationalThirdEyeResponse(rc.getRequest(), rows, responseSpec);
    }

    // TODO: Write logic to grab missing slices and merge rows later.
    // TODO: for now, just fetch the whole series and work on that logic.
    // fetch start to cacheStart - 1 => append to beginning
    // fetch cacheEnd to end => append to end
    LOG.info("cache fetch success :)");
    return response;
  }


  public void insertTimeSeriesIntoCache(String detectionId, ThirdEyeResponse response) {

    String dataSourceType = response.getClass().getSimpleName();

    switch (dataSourceType) {
      case "RelationalThirdEyeResponse":
        cacheDAO.insertRelationalTimeSeries(detectionId, response);
      case "CSVThirdEyeResponse":
        // do something
    }
  }

  public boolean detectionIdExistsInCache(long detectionId) {
    return cacheDAO.checkIfDetectionIdExistsInCache(String.valueOf(detectionId));
  }
}
