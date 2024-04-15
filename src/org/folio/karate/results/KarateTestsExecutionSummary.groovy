package org.folio.karate.results

import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignment

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

  Map<KarateTeam, List<KarateModuleExecutionSummary>> getModuleResultByTeam(TeamAssignment teamAssignment) {
    Map<KarateTeam, List<KarateModuleExecutionSummary>> teamResults = [:]

    Map<String, KarateTeam> teamByModule = teamAssignment.getTeamsByModules()
    getModulesExecutionSummary().values().each { moduleExecutionSummary ->

      if (teamByModule.containsKey(moduleExecutionSummary.getName())) {
        KarateTeam team = teamByModule.get(moduleExecutionSummary.getName())
        if (!teamResults.containsKey(team)) teamResults[team] = []

        teamResults[team].add(moduleExecutionSummary)
      }
    }

    return teamResults
  }

  @Override
  public String toString() {
    return "KarateTestsResult{" +
      "modulesTestResult=" + modulesExecutionSummary +
      '}';
  }
}
