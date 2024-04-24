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

  println("folioSlackNotificationUtils.groovy renderTestResultSection/40 TestExecutionResult.byPassRate(summary)=${TestExecutionResult.byPassRate(summary)}")
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

//Map<KarateTeam, String> renderTeamTestResultSection(TestType type
//                                                    , ITestExecutionSummary summary
//                                                    , TeamAssignment teamAssignment){
//
//  Map<KarateTeam, List<IExecutionSummary>> teamResults = summary.getModuleResultByTeam(teamAssignment)
//
//  teamResults.each { entry ->
//
//    List<String> failedFields = []
//    entry.value.each { moduleTestResult ->
//      if (moduleTestResult.getExecutionResult() == TestExecutionResult.FAILED) {
//        failedFields << SlackHelper.renderField(
//          ":gear: ${moduleTestResult.getName()}"
//          , "Has ${moduleTestResult.getFeaturesFailed()} failures of ${moduleTestResult.getFeaturesTotal()} total tests"
//          , true
//        )
//      }
//    }
//
//    println("folioSlackNotificationUtils created < -1h karateTestUtils.getJiraIssuesByTeam.size()=${karateTestUtils.getJiraIssuesByTeam("Kitfox", "created < -1h").size()}")
//    println("folioSlackNotificationUtils created > -20m karateTestUtils.getJiraIssuesByTeam.size()=${karateTestUtils.getJiraIssuesByTeam("Kitfox", "created > -20m").size()}")
//
//    // Existing tickets - created more than 1 hour ago
//    def existingTickets = karateTestUtils.getJiraIssuesByTeam("Kitfox", "created < -1h")
////      def existingTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created < -1h")
//
//    // Created tickets by this run - Within the last 20 min
//    def createdTickets = karateTestUtils.getJiraIssuesByTeam("Kitfox", "created > -20m")
////      def createdTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created > -20m")
//
//    def existingIssuesFilter = "(${existingTickets.join('%2C%20')})"
//    def createdIssuesFilter = "(${createdTickets.join('%2C%20')})"
//
//    List<String> actions =
//    [
//      SlackHelper.renderAction(
//        "${JiraConstants.FILTERED_ISSUES_VIEW_URL}issuekey%20in%20${existingIssuesFilter}"
//        , "*Check out the existing issues* :information_source: "
//      )
//      , SlackHelper.renderAction(
//        "${JiraConstants.FILTERED_ISSUES_VIEW_URL}issuekey%20in%20${createdIssuesFilter}"
//        ,"*Check out the created issues* :information_source: "
//      )
//    ]
//
//    String moduleInfoSection = ""
//    if (failedFields.isEmpty()) {
//      moduleInfoSection = SlackHelper.renderSection(
//          ""
//          , "All modules for ${entry.key.name} team have successful result"
//          , "good"
//          , []
//          , []
//        )
//    } else {
//      moduleInfoSection = SlackHelper.renderSection(
//        "Jira issues :warning:"
//        , ""
//        , "#E9D502"
//        , actions
//        , failedFields
//      )
//    }
//
//    slackSend(
//      attachments: SlackHelper.addSectionsToMessage(commonMessage, [ moduleInfoSection ])
//      , channel: "#rancher-test-notifications"
////      , channel: entry.key.slackChannel
//    )
//  }
//}


