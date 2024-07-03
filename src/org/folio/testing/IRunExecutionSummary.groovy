package org.folio.testing

import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

interface IRunExecutionSummary extends IExecutionSummary {

  Map<Team, List<IModuleExecutionSummary>> getModuleResultByTeam(TeamAssignment teamAssignment)

  int getModulesPassedCount()

  int getModulesFailedCount()

  int getModulesTotalCount()

  int getModulesPassRate()
}
