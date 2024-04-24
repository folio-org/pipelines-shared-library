package org.folio.testing.karate.results

import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.TestExecutionResult

class KarateModuleExecutionSummary implements IModuleExecutionSummary {

  String name

  TestExecutionResult executionResult = TestExecutionResult.SUCCESS

  List<KarateFeatureExecutionSummary> features = []

  int featuresPassed = 0

  int featuresFailed = 0

  int featuresSkipped = 0

  KarateModuleExecutionSummary(String name) {
    this.name = name
  }

  void addFeaturesExecutionStatistics(int success, int failed, int skipped) {
    featuresPassed += success
    featuresFailed += failed
    featuresSkipped += skipped

    if (featuresFailed > 0) {
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

  int getFeaturesTotal() {
    featuresPassed + featuresFailed + featuresSkipped
  }

  @Override
  int getPassedCount() {
    return featuresPassed
  }

  @Override
  int getFailedCount() {
    return featuresFailed
  }

  @Override
  int getSkippedCount() {
    return featuresSkipped
  }

  @Override
  int getTotalCount() {
    return featuresPassed + featuresFailed + featuresSkipped
  }

  @Override
  int getPassRate() {
    def passRateInDecimal = getTotalCount() > 0 ? (featuresPassed * 100) / getTotalCount() : 0
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
  public String toString() {
    return "KarateModuleExecutionSummary{" +
      "name='" + name + '\'' +
      ", executionResult=" + executionResult +
      ", features=" + features +
      ", featuresPassed=" + featuresPassed +
      ", featuresFailed=" + featuresFailed +
      ", featuresSkipped=" + featuresSkipped +
      '}';
  }
}
