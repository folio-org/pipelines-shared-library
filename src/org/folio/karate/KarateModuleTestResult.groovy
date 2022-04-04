package org.folio.karate

class KarateModuleTestResult {

    String name

    KarateExecutionResult executionResult = KarateExecutionResult.SUCCESS

    int errorCount = 0

    KarateModuleTestResult(String name) {
        this.name = name
    }

    void addErrors(int count) {
        errorCount += count
        executionResult = KarateExecutionResult.FAIL
    }

    @Override
    public String toString() {
        return "KarateModuleTestResult{" +
            "name='" + name + '\'' +
            ", executionResult=" + executionResult +
            ", errorCount=" + errorCount +
            '}';
    }
}
