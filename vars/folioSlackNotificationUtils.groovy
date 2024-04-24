import org.folio.jira.JiraConstants
import org.folio.client.reportportal.ReportPortalTestType
import org.folio.slack.SlackBuildResultRenderer
import org.folio.slack.SlackHelper
import org.folio.slack.SlackTeamTestResultRenderer
import org.folio.slack.SlackTestResultRenderer
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment
import org.folio.testing.IExecutionSummary
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.ITestExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.TestType

String renderBuildResultSection(){
  SlackBuildResultRenderer slackBuildResult =
    SlackBuildResultRenderer.fromResult(currentBuild.result == null ? "SUCCESS" : currentBuild.result)

  return renderBuildResultSection(slackBuildResult)
}

String renderBuildResultSection(SlackBuildResultRenderer buildResult){
  return buildResult.renderSection(
    env.JOB_NAME
    , env.BUILD_NUMBER
    , STAGE_NAME
    , "${env.BUILD_URL}console"
  )
}

String renderFailedBuildResultMessage(){
  return SlackHelper
    .renderMessage([renderBuildResultSection(SlackBuildResultRenderer.FAILURE)])
}

@SuppressWarnings('GrMethodMayBeStatic')
String renderTestResultSection(TestType type, IExecutionSummary summary
                               , String buildName, boolean useReportPortal, String url){

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
                                       , String buildName, boolean useReportPortal, String url){
  return SlackHelper.renderMessage(
    [
      renderBuildResultSection()
      , renderTestResultSection(type, summary, buildName, useReportPortal, url)
    ]
  )
}

Map<Team, String> renderTeamsTestResultMessages(TestType type
                                                , ITestExecutionSummary summary
                                                , TeamAssignment teamAssignment
                                                , String buildName, boolean useReportPortal, String url) {

  Map<Team, List<IModuleExecutionSummary>> teamsResults = summary.getModuleResultByTeam(teamAssignment)
  Map<Team, String> teamsRenderSection = [:]

  teamsResults.each {teamResults ->
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
String renderTeamTestResultSection(TestType type, Team team, List<IModuleExecutionSummary> results){
  // Existing tickets - created more than 1 hour ago
  def existingTickets = karateTestUtils.getJiraIssuesByTeam("Kitfox", "created < -1h")
//      def existingTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created < -1h")

  // Created tickets by this run - Within the last 20 min
  def createdTickets = karateTestUtils.getJiraIssuesByTeam("Kitfox", "created > -20m")
//      def createdTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created > -20m")

  String existingIssuesFilter = "(${existingTickets.join('%2C%20')})"
  String createdIssuesFilter = "(${createdTickets.join('%2C%20')})"

  String existingIssuesUrl = "${JiraConstants.FILTERED_ISSUES_VIEW_URL}issuekey%20in%20${existingIssuesFilter}"
  String createdIssuesUrl = "${JiraConstants.FILTERED_ISSUES_VIEW_URL}issuekey%20in%20${createdIssuesFilter}"

  return SlackTeamTestResultRenderer
    .fromType(type, TestExecutionResult.byTestResults(results))
    .renderSection(team, results, existingIssuesUrl, createdIssuesUrl)
}

@Deprecated
String renderSlackTestResultMessageSection(TestType type, LinkedHashMap<String, Integer> testResults
                                           , String buildName, boolean useReportPortal, String url){

  def totalTestsCount = testResults.passed + testResults.failed + testResults.broken
  def passRateInDecimal = totalTestsCount > 0 ? (testResults.passed * 100) / totalTestsCount : 0
  def passRate = passRateInDecimal.intValue()

  println "Total passed tests: ${testResults.passed}"
  println "Total failed tests: ${testResults.failed}"
  println "Total broken tests: ${testResults.broken}"

  SlackTestResultRenderer slackTestType =
    SlackTestResultRenderer.fromType(type, passRate > 50 ? TestExecutionResult.SUCCESS : TestExecutionResult.FAILED)

  return slackTestType.renderSection(
    "${buildName}"
    , "${testResults.passed}"
    , "${testResults.broken}"
    , "${testResults.failed}"
    , "${passRate}"
    , "${url}"
    , useReportPortal
    , ReportPortalTestType.fromType(type).reportPortalLaunchesURL())
}
