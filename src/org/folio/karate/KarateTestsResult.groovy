package org.folio.karate

class KarateTestsResult {

    Map<String, KarateModuleTestResult> modules = [:];

    void addModuleResult(String moduleName, int errorCount) {
        modules.computeIfAbsent(moduleName, new KarateModuleTestResult(moduleName))

        if (errorCount > 0) {
            modules[moduleName].addErrors(errorCount)
        }
    }

    KarateModuleTestResult getKarateModuleTestResult(String name) {
        modules[name]
    }



}
