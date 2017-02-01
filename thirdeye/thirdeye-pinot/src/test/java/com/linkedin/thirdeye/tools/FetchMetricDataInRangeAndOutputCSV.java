package com.linkedin.thirdeye.tools;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.linkedin.thirdeye.tools.FetchMetricDataAndExistingAnomaliesTool.TimeGranularity;


public class FetchMetricDataInRangeAndOutputCSV {
  private static final String DEFAULT_HOST = "http://localhost";
  private static final String DEFAULT_PORT = "1426";
  private static final String DEFAULT_PATH_TO_TIMESERIES = "/dashboard/data/timeseries?";

  private static final String DATASET = "dataset";
  private static final String METRIC = "metrics";
  private static final String VIEW = "view";
  private static final String DEFAULT_VIEW = "timeseries";
  private static final String TIME_START = "currentStart";
  private static final String TIME_END = "currentEnd";
  private static final String GRANULARITY = "aggTimeGranularity";
  private static final String DIMENSIONS = "dimensions"; // separate by comma
  private static final String FILTERS = "filters";

  /**
   * Fetch metric historical data from server and parse the json object
   * @param args List of arguments
   *             0: Path to the persistence file
   *             1: dataset/collection name
   *             2: metric name
   *             3: retrieval start time in ISO format, e.g. 2016-01-01T12:00:00
   *             4: timezone code
   *             5: Duration
   *             6: Aggregation time granularity, DAYS, HOURS,
   *             7: dimensions drill down, ex: product,channel
   *             8: filter in json format, ex: {"channel":["guest-email","guest-sms"]}
   *             9: Output path
   */
  public static void main(String[] args){
    if(args.length < 10){
      System.out.println("Error: Insufficient number of arguments");
      return;
    }
    String path2PersistenceFile = args[0];
    String dataset = args[1];
    String metric = args[2];
    String aggTimeGranularity = args[6];
    TimeGranularity timeGranularity = TimeGranularity.fromString(aggTimeGranularity);

    if(timeGranularity == null){
      System.out.println("Illegal time granularity");
      return;
    }
    // Training data range
    Period period = null;
    switch (timeGranularity) {
      case DAYS:
        period = new Period(0, 0, 0, Integer.valueOf(args[5]), 0, 0, 0, 0);
        break;
      case HOURS:
        period = new Period(0, 0, 0, 0, Integer.valueOf(args[5]), 0, 0, 0);
        break;
      case MINUTES:
        period = new Period(0, 0, 0, 0, 0, Integer.valueOf(args[5]), 0, 0);
        break;

    }
    DateTimeZone dateTimeZone = DateTimeZone.forID(args[4]);
    DateTime monitoringWindowStartTime = ISODateTimeFormat.dateTimeParser().parseDateTime(args[3]).withZone(dateTimeZone);
    DateTime dataRangeStart = monitoringWindowStartTime.minus(period); // inclusive start
    DateTime dataRangeEnd = monitoringWindowStartTime; // exclusive end
    String dimensions = args[7];
    String filters = args[8];
    DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");



    String fname = metric + "_" + fmt.print(dataRangeStart) + "_" + fmt.print(dataRangeEnd) + ".csv";
    Map<String, Map<Long, Long>> metricContent;
    try {
      FetchMetricDataAndExistingAnomaliesTool thirdEyeDAO = new FetchMetricDataAndExistingAnomaliesTool(new File(path2PersistenceFile));
      metricContent = thirdEyeDAO.fetchMetric(DEFAULT_HOST, Integer.valueOf(DEFAULT_PORT), dataset,
          metric, dataRangeStart, dataRangeEnd,
          FetchMetricDataAndExistingAnomaliesTool.TimeGranularity.fromString(aggTimeGranularity), dimensions,
          filters, dateTimeZone.getID());

      BufferedWriter bw = new BufferedWriter(new FileWriter(args[9] + fname));

      List<String> keys = new ArrayList<>(metricContent.keySet());
      List<Long> dateTimes = new ArrayList<>(metricContent.get(keys.get(0)).keySet());
      Collections.sort(dateTimes);

      // Print Header
      for(String str: keys){
        bw.write("," + str);
      }
      bw.newLine();

      for(Long dt : dateTimes){
        bw.write(Long.toString(dt));
        for(String key : keys){
          Map<Long, Long> map = metricContent.get(key);
          bw.write("," + map.get(dt));
        }
        bw.newLine();
      }
      bw.close();
    }
    catch (Exception e){
      System.out.println(e.getMessage());
    }
  }
}
