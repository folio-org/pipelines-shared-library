package org.folio.testing.cypress.results

import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.karate.results.KarateModuleExecutionSummary
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

class CypressRunExecutionSummary implements IRunExecutionSummary {

  Map<String, KarateModuleExecutionSummary> modulesExecutionSummary = [:]

  static List<CypressExecutionDefect> defects = []


  //TBD

  Map<Team, List<IModuleExecutionSummary>> getModuleResultByTeam(TeamAssignment teamAssignment) {
    Map<Team, List<IModuleExecutionSummary>> teamResults = [:]

    //TBD

    return teamResults
  }

  @Override
  int getModulesPassedCount() {
    return 0
  }

  @Override
  int getModulesFailedCount() {
    return 0
  }

  @Override
  int getModulesTotalCount() {
    return 0
  }

  @Override
  int getModulesPassRate() {
    return 0
  }

  @Override
  int getPassedCount() {
    return 0
  }

  @Override
  int getFailedCount() {
    return 0
  }

  @Override
  int getSkippedCount() {
    return 0
  }

  @Override
  int getTotalCount() {
    return 0
  }

  @Override
  int getPassRate() {
    return 0
  }

  @Override
  TestExecutionResult getExecutionResult(int passRate) {
    return null
  }

  @Override
  String toString() {
    return "CypressTestsResult{" +
      "modulesTestResult=" + modulesExecutionSummary +
      '}'
  }

  static void addDefect(CypressExecutionDefect defect){
    if(!defects.contains(defect))
      defects.add(defect)
  }
}
