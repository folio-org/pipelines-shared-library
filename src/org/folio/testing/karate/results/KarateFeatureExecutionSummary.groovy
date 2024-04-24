package org.folio.testing.karate.results

import org.folio.testing.IExecutionSummary
import org.folio.testing.TestExecutionResult

class KarateFeatureExecutionSummary implements IExecutionSummary {

  String name

  String displayName

  String description

  String packageQualifiedName

  String relativePath

  int passedCount = 0

  int failedCount = 0

  int scenarioCount = 0

  String cucumberReportFile

  boolean failed

  @Override
  int getSkippedCount() {
    return 0
  }

  @Override
  int getTotalCount() {
    return passedCount + failedCount
  }

  @Override
  int getPassRate() {
    def passRateInDecimal = getTotalCount() > 0 ? (passedCount * 100) / getTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  TestExecutionResult getExecutionResult(int passRate) {
    return failed ? TestExecutionResult.FAILED : TestExecutionResult.SUCCESS
  }

  @Override
  public String toString() {
    return "KarateFeatureExecutionSummary{" +
      "name='" + name + '\'' +
      ", displayName='" + displayName + '\'' +
      ", description='" + description + '\'' +
      ", packageQualifiedName='" + packageQualifiedName + '\'' +
      ", relativePath='" + relativePath + '\'' +
      ", passedCount=" + passedCount +
      ", failedCount=" + failedCount +
      ", scenarioCount=" + scenarioCount +
      ", cucumberReportFile='" + cucumberReportFile + '\'' +
      ", failed=" + failed +
      '}';
  }
}
