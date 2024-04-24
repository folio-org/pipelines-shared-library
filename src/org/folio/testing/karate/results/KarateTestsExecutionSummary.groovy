package org.folio.testing.karate.results

import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.ITestExecutionSummary
import org.folio.testing.TestExecutionResult

class KarateTestsExecutionSummary implements ITestExecutionSummary {

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
  Map<Team, List<IModuleExecutionSummary>> getModuleResultByTeam(TeamAssignment teamAssignment) {
    Map<Team, List<KarateModuleExecutionSummary>> teamResults = [:]

    Map<String, Team> teamByModule = teamAssignment.getTeamsByModules()
    getModulesExecutionSummary().values().each { moduleExecutionSummary ->

      if (teamByModule.containsKey(moduleExecutionSummary.getName())) {
        Team team = teamByModule.get(moduleExecutionSummary.getName())
        if (!teamResults.containsKey(team)) teamResults[team] = []

        teamResults[team].add(moduleExecutionSummary)
      }
    }

    return teamResults
  }

  @Override
  int getPassedCount() {
    int passed = 0

    modulesExecutionSummary.each { moduleSummary ->
      passed += moduleSummary.value.featuresPassed
    }

    return passed
  }

  @Override
  int getFailedCount() {
    int failed = 0

    modulesExecutionSummary.each { moduleSummary ->
      failed += moduleSummary.value.featuresFailed
    }

    return failed
  }

  @Override
  int getSkippedCount() {
    int skipped = 0

    modulesExecutionSummary.each { moduleSummary ->
      skipped += moduleSummary.value.featuresSkipped
    }

    return skipped
  }

  @Override
  int getTotalCount() {
    int total = 0

    modulesExecutionSummary.each { moduleSummary ->
      total += moduleSummary.value.getTotalCount()
    }

    return total
  }

  @Override
  int getPassRate() {
    def passRateInDecimal = getTotalCount() > 0 ? (featuresPassed * 100) / getTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  TestExecutionResult getExecutionResult(int passRateLimit = 50) {
    return TestExecutionResult.byPassRate(this, passRateLimit)
  }

  @Override
  public String toString() {
    return "KarateTestsResult{" +
      "modulesTestResult=" + modulesExecutionSummary +
      '}';
  }
}
