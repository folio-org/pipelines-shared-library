package org.folio.testing

interface IExecutionSummary {
  int getPassedCount()

  int getFailedCount()

  int getSkippedCount()

  int getBrokenCount()

  int getTotalCount()

  int getPassRate()

  TestExecutionResult getExecutionResult(int passRate)
}
