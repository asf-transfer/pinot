package com.linkedin.thirdeye.detector.db;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;

import com.linkedin.thirdeye.detector.api.AnomalyFunctionSpec;

import io.dropwizard.hibernate.AbstractDAO;
import io.dropwizard.hibernate.UnitOfWork;

public class AnomalyFunctionSpecDAO extends AbstractDAO<AnomalyFunctionSpec> {
  public AnomalyFunctionSpecDAO(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public AnomalyFunctionSpec findById(Long id) {
    AnomalyFunctionSpec anomalyFunctionSpec = get(id);
    return anomalyFunctionSpec;
  }

  public Long createOrUpdate(AnomalyFunctionSpec anomalyFunctionSpec) {
    long id = persist(anomalyFunctionSpec).getId();
    currentSession().getTransaction().commit();
    return id;
  }


  public void toggleActive(Long id, boolean isActive) {
    namedQuery("com.linkedin.thirdeye.api.AnomalyFunctionSpec#toggleActive").setParameter("id", id)
        .setParameter("isActive", isActive).executeUpdate();
  }

  public void delete(Long id) {
    AnomalyFunctionSpec anomalyFunctionSpec = new AnomalyFunctionSpec();
    anomalyFunctionSpec.setId(id);
    currentSession().delete(anomalyFunctionSpec);
  }

  public void delete(AnomalyFunctionSpec anomalyFunctionSpec) {
    currentSession().delete(anomalyFunctionSpec);
  }

  public List<AnomalyFunctionSpec> findAll() {
    return list(namedQuery("com.linkedin.thirdeye.api.AnomalyFunctionSpec#findAll"));
  }

  public List<AnomalyFunctionSpec> findAllByCollection(String collection) {
    return list(namedQuery("com.linkedin.thirdeye.api.AnomalyFunctionSpec#findAllByCollection")
        .setParameter("collection", collection));
  }

  public List<String> findDistinctMetricsByCollection(String collection) {
    List<String> metrics = new ArrayList<>();
    List<AnomalyFunctionSpec> anomalyFunctionSpecs = list(namedQuery("com.linkedin.thirdeye.api.AnomalyFunctionSpec#findDistinctMetricsByCollection")
        .setParameter("collection", collection));
    for (Object anomalyFunctionSpec : anomalyFunctionSpecs) {
      metrics.add(anomalyFunctionSpec.toString());
    }
    return metrics;
  }

}
