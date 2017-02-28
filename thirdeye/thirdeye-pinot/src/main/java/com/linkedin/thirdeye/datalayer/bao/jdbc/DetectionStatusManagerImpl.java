package com.linkedin.thirdeye.datalayer.bao.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

import com.linkedin.thirdeye.datalayer.bao.DetectionStatusManager;
import com.linkedin.thirdeye.datalayer.dto.DetectionStatusDTO;
import com.linkedin.thirdeye.datalayer.pojo.DetectionStatusBean;
import com.linkedin.thirdeye.datalayer.util.Predicate;

public class DetectionStatusManagerImpl extends AbstractManagerImpl<DetectionStatusDTO>
    implements DetectionStatusManager {

  public DetectionStatusManagerImpl() {
    super(DetectionStatusDTO.class, DetectionStatusBean.class);
  }

  @Override
  public DetectionStatusDTO findLatestEntryForFunctionId(long functionId) {
    Predicate predicate = Predicate.EQ("functionId", functionId);
    List<DetectionStatusBean> list = genericPojoDao.get(predicate, DetectionStatusBean.class);
    DetectionStatusDTO result = null;
    if (CollectionUtils.isNotEmpty(list)) {
      Collections.sort(list);
      result = (DetectionStatusDTO) MODEL_MAPPER.map(list.get(list.size() - 1), DetectionStatusDTO.class);
    }
    return result;
  }

  @Override
  public List<DetectionStatusDTO> findAllInTimeRangeForFunctionAndDetectionRun(long startTime, long endTime,
      long functionId, boolean detectionRun) {
    Predicate predicate = Predicate.AND(
        Predicate.EQ("functionId", functionId),
        Predicate.LE("dateToCheckInMS", endTime),
        Predicate.GE("dateToCheckInMS", startTime),
        Predicate.EQ("detectionRun", detectionRun));

    List<DetectionStatusBean> list = genericPojoDao.get(predicate, DetectionStatusBean.class);
    List<DetectionStatusDTO> results = new ArrayList<>();
    for (DetectionStatusBean bean : list) {
      results.add((DetectionStatusDTO) MODEL_MAPPER.map(bean, DetectionStatusDTO.class));
    }
    return results;
  }
}
