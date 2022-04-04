package org.folio.karate

class KarateTestsResult {

    Map<String, KarateModuleTestResult> modules = [:];

    void addModuleResult(String moduleName, int success, int failed, int skipped) {
        if (!modules.containsKey(moduleName)) {
            modules.put(moduleName, new KarateModuleTestResult(moduleName))
        }

        modules[moduleName].addStatistics(success, failed, skipped)
    }

    KarateModuleTestResult getKarateModuleTestResult(String name) {
        modules[name]
    }

}
