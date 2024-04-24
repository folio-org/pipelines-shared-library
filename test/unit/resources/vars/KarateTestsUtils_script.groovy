import org.folio.testing.karate.results.KarateTestsExecutionSummary
import org.folio.testing.teams.TeamAssignment
import org.jenkinsci.plugins.workflow.libs.Library

def execute(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    @Library('local') _

    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
}
