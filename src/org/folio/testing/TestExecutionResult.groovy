package org.folio.testing

enum TestExecutionResult {
  SUCCESS, FAILED

  static TestExecutionResult byPassRate(IExecutionSummary summary, int passRateLimit = 50){
    summary.passRate > passRateLimit ? SUCCESS : FAILED
  }

  static TestExecutionResult byTestResults(List<IExecutionSummary> results, int passRateLimit = 50){
    if (results.findAll({it.getExecutionResult(passRateLimit) == FAILED }).size() > 0)
      return FAILED

    return SUCCESS
  }
}
