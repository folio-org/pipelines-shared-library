package org.folio.karate.results

class KarateFeatureExecutionSummary {

    String name

    String description

    String packageQualifiedName

    int passedCount = 0

    int failedCount = 0

    int scenarioCount = 0

    boolean failed

    @Override
    public String toString() {
        return "KarateFeatureExecutionSummary{" +
            "name='" + name + '\'' +
            ", description='" + description + '\'' +
            ", packageQualifiedName='" + packageQualifiedName + '\'' +
            ", passedCount=" + passedCount +
            ", failedCount=" + failedCount +
            ", scenarioCount=" + scenarioCount +
            ", failed=" + failed +
            '}';
    }
}
