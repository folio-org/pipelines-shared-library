package org.folio.testing.karate.results

import org.folio.testing.IExecutionSummary
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.ITestExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

class KarateTestsExecutionSummary implements ITestExecutionSummary {

  Map<String, KarateModuleExecutionSummary> modulesExecutionSummary = [:]

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
  int getModulesPassedCount() {
    int count = 0
    for (KarateModuleExecutionSummary module : modulesExecutionSummary.values()) {
      count += (module.executionResult == TestExecutionResult.SUCCESS) ? 1 : 0
    }

    return count
  }

  @Override
  int getModulesFailedCount() {
    int count = 0
    for (KarateModuleExecutionSummary module : modulesExecutionSummary.values()) {
      count += (module.executionResult == TestExecutionResult.FAILED) ? 1 : 0
    }

    return count
  }

  @Override
  int getModulesTotalCount() {
    return modulesExecutionSummary.size()
  }

  @Override
  int getModulesPassRate() {
    def passRateInDecimal = getModulesTotalCount() > 0 ? (getModulesPassedCount() * 100) / getModulesTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  int getPassedCount() {
    int passed = 0

    modulesExecutionSummary.values().each { moduleSummary ->
      passed += ((IExecutionSummary) moduleSummary).getPassedCount()
    }

    return passed
  }

  @Override
  int getFailedCount() {
    int failed = 0

    modulesExecutionSummary.values().each { moduleSummary ->
      failed += ((IExecutionSummary) moduleSummary).getFailedCount()
    }

    return failed
  }

  @Override
  int getSkippedCount() {
    int skipped = 0

    modulesExecutionSummary.values().each { moduleSummary ->
      skipped += ((IExecutionSummary) moduleSummary).getSkippedCount()
    }

    return skipped
  }

  @Override
  int getTotalCount() {
    int total = 0

    modulesExecutionSummary.values().each { moduleSummary ->
      total += ((IExecutionSummary) moduleSummary).getTotalCount()
    }

    return total
  }

  @Override
  int getPassRate() {
    def passRateInDecimal = getTotalCount() > 0 ? (getPassedCount() * 100) / getTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  TestExecutionResult getExecutionResult(int passRateLimit = 50) {
    return TestExecutionResult.byPassRate(this, passRateLimit)
  }

  @Override
  String toString() {
    return "KarateTestsResult{" +
      "modulesTestResult=" + modulesExecutionSummary +
      '}'
  }
}
