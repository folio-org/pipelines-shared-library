package org.folio.karate.results

class KarateTestsResult {

    Map<String, KarateModuleTestResult> modulesTestResult = [:];

    void addModuleResult(String moduleName, int success, int failed, int skipped) {
        if (!modulesTestResult.containsKey(moduleName)) {
            modulesTestResult.put(moduleName, new KarateModuleTestResult(moduleName))
        }

        modulesTestResult[moduleName].addStatistics(success, failed, skipped)
    }

    KarateModuleTestResult getKarateModuleTestResult(String name) {
        modulesTestResult[name]
    }

    @Override
    public String toString() {
        return "KarateTestsResult{" +
            "modulesTestResult=" + modulesTestResult +
            '}';
    }
}
