package org.folio.models.parameters

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CypressTestsParametersTest {

  @Test
  void testDefaults() {
    def params = new CypressTestsParameters()

    Assertions.assertEquals(1, params.retryCount)
    Assertions.assertTrue(params.multiThread)
    Assertions.assertEquals(1, params.numberOfWorkers)
  }

  @Test
  void testRetryCountSetterGetter() {
    def params = new CypressTestsParameters()
    params.setRetryCount(3)

    Assertions.assertEquals(3, params.retryCount)
    Assertions.assertEquals(3, params.getRetryCount())
  }

  @Test
  void testMultiThreadSetterGetter() {
    def params = new CypressTestsParameters()
    params.setMultiThread(false)

    Assertions.assertFalse(params.multiThread)
    Assertions.assertFalse(params.getMultiThread())
  }
}
