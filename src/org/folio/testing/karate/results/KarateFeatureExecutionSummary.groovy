package org.folio.testing.karate.results

class KarateFeatureExecutionSummary {

    String name

    String displayName

    String description

    String packageQualifiedName

    String relativePath

    int passedCount = 0

    int failedCount = 0

    int scenarioCount = 0

    String cucumberReportFile

    boolean failed

    @Override
    public String toString() {
        return "KarateFeatureExecutionSummary{" +
            "name='" + name + '\'' +
            ", displayName='" + displayName + '\'' +
            ", description='" + description + '\'' +
            ", packageQualifiedName='" + packageQualifiedName + '\'' +
            ", relativePath='" + relativePath + '\'' +
            ", passedCount=" + passedCount +
            ", failedCount=" + failedCount +
            ", scenarioCount=" + scenarioCount +
            ", cucumberReportFile='" + cucumberReportFile + '\'' +
            ", failed=" + failed +
            '}';
    }
}
