package org.folio.testing.karate.results

class KarateTestsExecutionSummary {

    Map<String, KarateModuleExecutionSummary> modulesExecutionSummary = [:];

    void addModuleResult(String moduleName, def summaryJson, Map<String, String> displayNames) {
        if (!modulesExecutionSummary.containsKey(moduleName)) {
            modulesExecutionSummary.put(moduleName, new KarateModuleExecutionSummary(moduleName))
        }

        def moduleSummary = modulesExecutionSummary[moduleName]
        moduleSummary.addFeaturesExecutionStatistics(summaryJson.featuresPassed,
            summaryJson.featuresFailed,
            summaryJson.featuresSkipped)

        summaryJson.featureSummary.each { featureSummaryJson ->
            moduleSummary.addFeatureSummary(featureSummaryJson, displayNames)
        }
    }

    @Override
    public String toString() {
        return "KarateTestsResult{" +
            "modulesTestResult=" + modulesExecutionSummary +
            '}';
    }
}
