import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.jenkinsci.plugins.workflow.libs.Library

def execute(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    @Library('local') _

    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
}
