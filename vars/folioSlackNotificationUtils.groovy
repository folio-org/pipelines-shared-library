import org.folio.client.reportportal.ReportPortalTestType
import org.folio.client.slack.SlackBuildResultRenderer
import org.folio.client.slack.SlackHelper
import org.folio.client.slack.SlackTestResultRenderer
import org.folio.karate.results.KarateExecutionResult
import org.folio.karate.results.KarateModuleExecutionSummary
import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignment
import org.folio.shared.TestResult
import org.folio.shared.TestType

String renderSlackBuildResultMessageSection(){
  SlackBuildResultRenderer slackBuildResult =
    SlackBuildResultRenderer.fromResult(currentBuild.result == null ? "SUCCESS" : currentBuild.result)

  return renderSlackBuildResultMessageSection(slackBuildResult)
}

String renderSlackBuildResultMessageSection(SlackBuildResultRenderer buildResult){
  return buildResult.renderSection(
    env.JOB_NAME
    , env.BUILD_NUMBER
    , STAGE_NAME
    , "${env.BUILD_URL}console"
  )
}

String renderSlackFailedResultMessage(){
  return SlackHelper
    .renderMessage([renderSlackBuildResultMessageSection(SlackBuildResultRenderer.FAILURE)])
}

String renderSlackTestResultMessageSection(TestType type, LinkedHashMap<String, Integer> testResults
                                           , String buildName, boolean useReportPortal, String url){

  def totalTestsCount = testResults.passed + testResults.failed + testResults.broken
  def passRateInDecimal = totalTestsCount > 0 ? (testResults.passed * 100) / totalTestsCount : 0
  def passRate = passRateInDecimal.intValue()

  println "Total passed tests: ${testResults.passed}"
  println "Total failed tests: ${testResults.failed}"
  println "Total broken tests: ${testResults.broken}"

  SlackTestResultRenderer slackTestType =
    SlackTestResultRenderer.fromType(type, passRate > 50 ? TestResult.SUCCESS : TestResult.FAILURE)

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

String renderSlackTestResultMessage(TestType type, LinkedHashMap<String, Integer> testResults
                                    , String buildName, boolean useReportPortal, String url){
  return SlackHelper.renderMessage(
    [
      renderSlackBuildResultMessageSection()
      , renderSlackTestResultMessageSection(type, testResults, buildName, useReportPortal, url)
    ]
  )
}

String sendSlackJiraTicketTeamNotification(KarateTestsExecutionSummary karateTestsExecutionSummary
                                             , TeamAssignment teamAssignment){

  Map<KarateTeam, List<KarateModuleExecutionSummary>> teamResults =
    karateTestsExecutionSummary.getModuleResultByTeam(teamAssignment)

  // iterate over teams and send slack notifications
  teamResults.each { entry ->

    List<String> failedFields = []
    entry.value.each { moduleTestResult ->
      if (moduleTestResult.getExecutionResult() == KarateExecutionResult.FAIL) {
        failedFields << SlackHelper.renderField(
          ":gear: ${moduleTestResult.getName()}"
          , "Has ${moduleTestResult.getFeaturesFailed()} failures of ${moduleTestResult.getFeaturesTotal()} total tests"
          , true
        )
      }
    }

    println("folioSlackNotificationUtils created < -1h karateTestUtils.getJiraIssuesByTeam.size()=${karateTestUtils.getJiraIssuesByTeam("Kitfox", "created < -1h").size()}")
    println("folioSlackNotificationUtils created > -20m karateTestUtils.getJiraIssuesByTeam.size()=${karateTestUtils.getJiraIssuesByTeam("Kitfox", "created > -20m").size()}")

    String moduleInfoSection = ""
//    if (failedFields.isEmpty()) {
//      moduleInfoSection = SlackHelper.renderSection(
//          ""
//          , "All modules for ${entry.key.name} team have successful result"
//          , "good"
//          , []
//          , []
//        )
//    } else {
      // Existing tickets - created more than 1 hour ago
      def existingTickets = karateTestUtils.getJiraIssuesByTeam("Kitfox", "created < -1h")
//      def existingTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created < -1h")
      // Created tickets by this run - Within the last 20 min
      def createdTickets = karateTestUtils.getJiraIssuesByTeam("Kitfox", "created > -20m")
//      def createdTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created > -20m")

      def existingIssuesFilter = "(${existingTickets.join('%2C%20')})"
      def createdIssuesFilter = "(${createdTickets.join('%2C%20')})"

      List<String> actions =
        [
          SlackHelper.renderAction(
            "https://issues.folio.org/issues/?jql=issuekey%20in%20${existingIssuesFilter}"
            , "*Check out the existing issues* :information_source: "
          )
          , SlackHelper.renderAction(
            "https://issues.folio.org/issues/?jql=issuekey%20in%20${createdIssuesFilter}"
                ,"*Check out the created issues* :information_source: "
          )
        ]

      moduleInfoSection = SlackHelper.renderSection(
        "Jira issues :warning:"
        , ""
        , "#E9D502"
        , actions
        , failedFields
      )
//    }

    slackSend(
      attachments: SlackHelper.renderMessage(
        [
          renderSlackBuildResultMessageSection()
          , moduleInfoSection
        ]
      )
      , channel: "#rancher-test-notifications"
//      , channel: entry.key.slackChannel
    )
  }
}


