package org.folio.testing

interface IExecutionSummary {
  int getPassedCount()
  int getFailedCount()
  int getSkippedCount()
  int getTotalCount()
  int getPassRate()
  TestExecutionResult getExecutionResult(int passRate)
}
