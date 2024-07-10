package org.folio.testing.karate.results

import org.folio.testing.IExecutionSummary
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.TestExecutionResult

class KarateModuleExecutionSummary implements IModuleExecutionSummary {

  String name

  TestExecutionResult executionResult = TestExecutionResult.SUCCESS

  List<KarateFeatureExecutionSummary> features = []

  int featuresPassedCount = 0

  int featuresFailedCount = 0

  int featuresSkippedCount = 0

  KarateModuleExecutionSummary(String name) {
    this.name = name
  }

  void addFeaturesExecutionStatistics(int success, int failed, int skipped) {
    featuresPassedCount += success
    featuresFailedCount += failed
    featuresSkippedCount += skipped

    if (featuresFailedCount > 0) {
      executionResult = TestExecutionResult.FAILED
    }
  }

  void addFeatureSummary(def summaryJson, Map<String, String> displayNames) {
    String displayName = displayNames[summaryJson.relativePath]
    if (!displayName) {
      displayName = summaryJson.relativePath
    }

    KarateFeatureExecutionSummary feature = new KarateFeatureExecutionSummary(
      name: summaryJson.name,
      displayName: displayName,
      description: summaryJson.description,
      packageQualifiedName: summaryJson.packageQualifiedName,
      relativePath: summaryJson.relativePath,
      passedCount: summaryJson.passedCount,
      failedCount: summaryJson.failedCount,
      scenarioCount: summaryJson.scenarioCount,
      failed: summaryJson.failed)

    features.add(feature)
  }

  @Override
  int getPassedCount() {
    int count = 0
    for (IExecutionSummary feature : features) {
      count += feature.getPassedCount()
    }

    return count
  }

  @Override
  int getFailedCount() {
    int count = 0
    for (IExecutionSummary feature : features) {
      count += feature.getFailedCount()
    }

    return count
  }

  @Override
  int getSkippedCount() {
    int count = 0
    for (IExecutionSummary feature : features) {
      count += feature.getSkippedCount()
    }

    return count
  }

  @Override
  int getBrokenCount() {
    int count = 0
    for (IExecutionSummary feature : features) {
      count += feature.getBrokenCount()
    }

    return count
  }

  @Override
  int getTotalCount() {
    return getPassedCount() + getFailedCount() + getSkippedCount() + getBrokenCount()
  }

  @Override
  int getPassRate() {
    def passRateInDecimal = getTotalCount() > 0 ? (featuresPassedCount * 100) / getTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  TestExecutionResult getExecutionResult(int passRate) {
    return executionResult
  }

  @Override
  String getModuleName() {
    return name
  }

  @Override
  int getFeaturesTotalCount() {
    return featuresPassedCount + featuresFailedCount + featuresSkippedCount
  }

  @Override
  int getFeaturesPassRate() {
    def passRateInDecimal = getFeaturesTotalCount() > 0 ? (featuresPassedCount * 100) / getFeaturesTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  String toString() {
    return "KarateModuleExecutionSummary{" +
      "name='" + name + '\'' +
      ", executionResult=" + executionResult +
      ", features=" + features +
      ", featuresPassed=" + featuresPassedCount +
      ", featuresFailed=" + featuresFailedCount +
      ", featuresSkipped=" + featuresSkippedCount +
      '}'
  }
}
