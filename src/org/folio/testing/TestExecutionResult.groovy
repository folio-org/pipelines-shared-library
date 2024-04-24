package org.folio.testing

import com.cloudbees.groovy.cps.NonCPS

enum TestExecutionResult {
  SUCCESS, FAILED

  @NonCPS
  static TestExecutionResult byPassRate(IExecutionSummary summary, int passRateLimit = 50){
    return summary.passRate > passRateLimit ? SUCCESS : FAILED
  }

  @NonCPS
  static TestExecutionResult byTestResults(List<IExecutionSummary> results, int passRateLimit = 50){
    if (results.findAll({it.getExecutionResult(passRateLimit) == FAILED }).size() > 0)
      return FAILED

    return SUCCESS
  }
}
