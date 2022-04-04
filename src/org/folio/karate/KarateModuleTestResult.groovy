package org.folio.karate

class KarateModuleTestResult {

    String name

    KarateExecutionResult executionResult = KarateExecutionResult.SUCCESS

    int successCount = 0

    int failedCount = 0

    int skippedCount = 0

    KarateModuleTestResult(String name) {
        this.name = name
    }

    void addStatistics(int success, int failed, int skipped) {
        successCount += success
        failedCount += failed
        skippedCount += skipped

        if (failedCount > 0) {
            executionResult = KarateExecutionResult.FAIL
        }
    }

    @Override
    public String toString() {
        return "KarateModuleTestResult{" +
            "name='" + name + '\'' +
            ", executionResult=" + executionResult +
            ", successCount=" + successCount +
            ", failedCount=" + failedCount +
            ", skippedCount=" + skippedCount +
            '}';
    }
}
