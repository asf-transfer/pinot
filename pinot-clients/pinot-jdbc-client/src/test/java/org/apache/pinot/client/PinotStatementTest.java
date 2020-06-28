package org.apache.pinot.client;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.concurrent.Future;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class PinotStatementTest {
  private DummyPinotClientTransport _dummyPinotClientTransport = new DummyPinotClientTransport();
  private PinotClientTransportFactory _previousTransportFactory = null;

  @Test
  public void testExecuteQuery() throws Exception {
    PinotConnection connection = new PinotConnection(Collections.singletonList("dummy"), _dummyPinotClientTransport);
    Statement statement = new PinotStatement(connection);
    ResultSet resultSet = statement.executeQuery("dummy");
    Assert.assertNotNull(resultSet);
    Assert.assertEquals(statement.getConnection(), connection);
  }

  @BeforeClass
  public void overridePinotClientTransport() {
    _previousTransportFactory = ConnectionFactory._transportFactory;
    ConnectionFactory._transportFactory = new DummyPinotClientTransportFactory(_dummyPinotClientTransport);
  }

  @AfterClass
  public void resetPinotClientTransport() {
    ConnectionFactory._transportFactory = _previousTransportFactory;
  }
}
