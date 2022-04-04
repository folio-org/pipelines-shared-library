package org.folio.karate

class KarateTestsResult {

    Map<String, KarateModuleTestResult> modules = [:];

    void addModuleResult(String moduleName, int errorCount) {
        if (!modules.containsKey(moduleName)) {
            modules.put(moduleName, new KarateModuleTestResult(moduleName))
        }

        if (errorCount > 0) {
            modules[moduleName].addErrors(errorCount)
        }
    }

    KarateModuleTestResult getKarateModuleTestResult(String name) {
        modules[name]
    }

}
