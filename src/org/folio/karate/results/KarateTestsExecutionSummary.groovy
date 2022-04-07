package org.folio.karate.results

class KarateTestsExecutionSummary {

    Map<String, KarateModuleExecutionSummary> modulesExecutionSummary = [:];

    void addModuleResult(String moduleName, def summaryJson) {
        if (!modulesExecutionSummary.containsKey(moduleName)) {
            modulesExecutionSummary.put(moduleName, new KarateModuleExecutionSummary(moduleName))
        }

        def moduleSummary = modulesExecutionSummary[moduleName]
        moduleSummary.addFeaturesExecutionStatistics(featureSummaryJson.featuresPassed,
            featureSummaryJson.featuresFailed,
            featureSummaryJson.featuresSkipped)

        summaryJson.featureSummary.each { featureSummaryJson ->
            moduleSummary.addFeatureSummary(featureSummaryJson)
        }
    }

    @Override
    public String toString() {
        return "KarateTestsResult{" +
            "modulesTestResult=" + modulesExecutionSummary +
            '}';
    }
}
