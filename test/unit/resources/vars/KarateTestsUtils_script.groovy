import org.folio.testing.karate.results.KarateRunExecutionSummary
import org.folio.testing.teams.TeamAssignment
import org.jenkinsci.plugins.workflow.libs.Library

def execute(KarateRunExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    @Library('local') _

    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
}
