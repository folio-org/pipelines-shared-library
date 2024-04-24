package org.folio.testing.cypress.results

import org.folio.testing.karate.results.KarateModuleExecutionSummary

class CypressTestsExecutionSummary {

//TBD

  @Override
  public String toString() {
    return "CypressTestsResult{" +
      "modulesTestResult=" + modulesExecutionSummary +
      '}';
  }
}
