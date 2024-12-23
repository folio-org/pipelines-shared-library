import org.folio.client.reportportal.ReportPortalTestType
import org.folio.jira.JiraConstants
import org.folio.slack.SlackBuildResultRenderer
import org.folio.slack.SlackHelper
import org.folio.slack.SlackTeamTestResultRenderer
import org.folio.slack.SlackTestResultRenderer
import org.folio.testing.*
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

String renderBuildResultSection() {
  SlackBuildResultRenderer slackBuildResult =
    SlackBuildResultRenderer.fromResult(currentBuild.result == null ? "SUCCESS" : currentBuild.result)

  return renderBuildResultSection(slackBuildResult)
}

String renderBuildResultSection(SlackBuildResultRenderer buildResult) {
  return buildResult.renderSection(
    env.JOB_BASE_NAME
    , env.BUILD_NUMBER
    , STAGE_NAME
    , "${env.BUILD_URL}console"
  )
}

String renderBuildResultMessage() {
  return SlackHelper
    .renderMessage([renderBuildResultSection()])
}

String renderSuccessBuildResultMessage() {
  return SlackHelper
    .renderMessage([renderBuildResultSection(SlackBuildResultRenderer.SUCCESS)])
}

String renderFailedBuildResultMessage() {
  return SlackHelper
    .renderMessage([renderBuildResultSection(SlackBuildResultRenderer.FAILURE)])
}

String renderAbortedBuildResultMessage() {
  return SlackHelper
    .renderMessage([renderBuildResultSection(SlackBuildResultRenderer.ABORTED)])
}

@SuppressWarnings('GrMethodMayBeStatic')
String renderTestResultSection(TestType type, IExecutionSummary summary
                               , String buildName, boolean useReportPortal, String url) {

  return SlackTestResultRenderer.fromType(type, TestExecutionResult.byPassRate(summary))
    .renderSection(
      "${buildName}"
      , summary
      , "${url}"
      , useReportPortal
      , ReportPortalTestType.fromType(type).reportPortalLaunchesURL()
    )
}

String renderBuildAndTestResultMessage(TestType type, IExecutionSummary summary
                                       , String buildName, boolean useReportPortal, String url) {
  return SlackHelper.renderMessage(
    [
      renderBuildResultSection()
      , renderTestResultSection(type, summary, buildName, useReportPortal, url)
    ]
  )
}

Map<Team, String> renderTeamsTestResultMessages(TestType type
                                                , IRunExecutionSummary summary
                                                , TeamAssignment teamAssignment
                                                , String buildName, boolean useReportPortal, String url) {

  Map<Team, List<IModuleExecutionSummary>> teamsResults = summary.getModuleResultByTeam(teamAssignment)
  Map<Team, String> teamsRenderSection = [:]

  teamsResults.each { teamResults ->
    teamsRenderSection[teamResults.key] = SlackHelper.renderMessage(
      [
        renderBuildResultSection()
        , renderTestResultSection(type, summary, buildName, useReportPortal, url)
        , renderTeamTestResultSection(type, teamResults.key, teamResults.value)
      ]
    )
  }

  return teamsRenderSection
}

@SuppressWarnings('GrMethodMayBeStatic')
String renderTeamTestResultSection(TestType type, Team team, List<IModuleExecutionSummary> results) {
  List<String> existingTickets = []
  List<String> createdTickets = []

  try {
    // Existing tickets - created more than 1 hour ago
    existingTickets = karateTestUtils.getJiraIssuesByTeam(team.getName(), "created < -1h")

    // Created tickets by this run - Within the last 20 min
    createdTickets = karateTestUtils.getJiraIssuesByTeam(team.getName(), "created > -20m")
  } catch (e) {
    println("An error happened while fetching Jira issues for the team ${team.getName()}")
    println(e)
  }

  String existingIssuesFilter = "(${existingTickets.join('%2C%20')})"
  String createdIssuesFilter = "(${createdTickets.join('%2C%20')})"

  String existingIssuesUrl = "${JiraConstants.FILTERED_ISSUES_VIEW_URL}issuekey%20in%20${existingIssuesFilter}"
  String createdIssuesUrl = "${JiraConstants.FILTERED_ISSUES_VIEW_URL}issuekey%20in%20${createdIssuesFilter}"

  return SlackTeamTestResultRenderer
    .fromType(type, TestExecutionResult.byTestResults(results))
    .renderSection(team, results, existingIssuesUrl, createdIssuesUrl)
}

/**
 * Render a section for a sub-job build result
 * @param jobName Name of sub job
 * @param buildNumber Number of sub job build
 * @param buildUrl URL of sub job build
 * @param buildStatus Status of sub job build
 * @param errorMessage Error message for sub job build
 * @return String Rendered section for Slack notification
 */
@SuppressWarnings('GrMethodMayBeStatic')
String renderSubJobBuildResultSection(String jobName, String buildNumber, String buildUrl, String buildStatus, String errorMessage) {
  def message = "*${buildNumber} - ${buildStatus}* \\n _Error message_ - `${errorMessage}`\\n"

  List<String> actions = [
    SlackHelper.renderAction(buildUrl, ":page_facing_up: Check *${buildNumber}* console output")
  ]

  String section = SlackHelper.renderSection("Failed to create Environment with '${jobName}' pipeline", message, '#FF0000', actions, [])

  return section
}
