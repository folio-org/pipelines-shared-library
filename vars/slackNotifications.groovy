import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.folio.client.reportportal.ReportPortalTestType
import org.folio.testing.TestExecutionResult
import org.folio.testing.TestType
import org.folio.testing.karate.results.KarateModuleExecutionSummary
import org.folio.testing.karate.results.KarateRunExecutionSummary
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

@Deprecated
def renderSlackMessage(TestType testType, buildStatus, testsStatus, message, boolean useReportPortal = false,
                       moduleFailureFields = [],
                       List<String> jiraIssueLinksExisting = [], List<String> jiraIssueLinksCreated = []) {

  String rpTemplate = libraryResource("slackNotificationsTemplates/reportPortalTemplate")

  Map pipelineTemplates = [
    SUCCESS : libraryResource("slackNotificationsTemplates/pipelineSuccessTemplate"),
    UNSTABLE: libraryResource("slackNotificationsTemplates/pipelineUnstableTemplate"),
    FAILURE : libraryResource("slackNotificationsTemplates/pipelineFailedTemplate"),
    FAILED  : libraryResource("slackNotificationsTemplates/pipelineFailedTemplate")
  ]
  Map karateTemplates = [
    SUCCESS: libraryResource("slackNotificationsTemplates/karateTemplates/successTemplate"),
    FAILED : libraryResource("slackNotificationsTemplates/karateTemplates/failureTemplate")
  ]
  Map cypressTemplates = [
    SUCCESS: libraryResource("slackNotificationsTemplates/cypressTemplates/successTemplate"),
    FAILED : libraryResource("slackNotificationsTemplates/cypressTemplates/failureTemplate")
  ]

  if (message.contains("Pass rate:")) {
    def match = (message =~ /Pass rate: (\d+.\d+|\d+)%/)
    def passRate = match ? match[0][1].toFloat() : 0.0
    testsStatus = passRate > 50.0 ? "SUCCESS" : "FAILED"
  }

  def pipelineTemplate = pipelineTemplates[buildStatus]
    ?.replace('$RP_COMMA', useReportPortal ? "," : "")
    ?.replace('$RP_TEMPLATE', useReportPortal ? rpTemplate : "")

  switch (buildStatus) {
    case "FAILURE":
    case "FAILED":
      message = STAGE_NAME
      def output = pipelineTemplate
        .replace('$BUILD_URL', env.BUILD_URL)
        .replace('$BUILD_NUMBER', env.BUILD_NUMBER)
        .replace('$JOBNAME', env.JOB_NAME)
        .replace('$MESSAGE', message)
      return output
      break
    case "UNSTABLE":
    case "SUCCESS":
      def extraFields = []
      if (!moduleFailureFields.isEmpty()) {
        extraFields << [
          title : "Module Failures :no_entry:",
          color : "#FF0000",
          fields: moduleFailureFields
        ]
        testsStatus = "FAILED"
      }

      if (!jiraIssueLinksExisting.isEmpty() || !jiraIssueLinksCreated.isEmpty()) {

        def existingIssuesFilter = "(${jiraIssueLinksExisting.join('%2C%20')})"
        def createdIssuesFilter = "(${jiraIssueLinksCreated.join('%2C%20')})"

        def jiraIssueLinksExistingButton = [
          type: "button",
          url : "https://issues.folio.org/issues/?jql=issuekey%20in%20${existingIssuesFilter}",
          text: "*Check out the existing issues* :information_source: "
        ]

        def jiraIssueLinksCreatedButton = [
          type: "button",
          url : "https://issues.folio.org/issues/?jql=issuekey%20in%20${createdIssuesFilter}",
          text: "*Check out the created issues* :information_source: "
        ]

        extraFields << [
          title  : "Jira issues :warning:",
          color  : "#E9D502",
          actions: [(jiraIssueLinksExisting ? jiraIssueLinksExistingButton : null), (jiraIssueLinksCreated ? jiraIssueLinksCreatedButton : null)].findAll { it != null }
        ]

      }

      String testsTemplate = testType == TestType.KARATE ? karateTemplates[testsStatus] :
        testType == TestType.CYPRESS ? cypressTemplates[testsStatus] : null
      testsTemplate = testsTemplate
        ?.replace('$RP_COMMA', useReportPortal ? "," : "")
        ?.replace('$RP_TEMPLATE', useReportPortal ? rpTemplate : "")

      def messageLines = message.tokenize("\n")
      message = messageLines.join("\\n")

      def finalTemplate = new JsonSlurper().parseText(pipelineTemplate)
      def additionTemplate = new JsonSlurper().parseText(testsTemplate)

      finalTemplate += additionTemplate
      finalTemplate += extraFields
      def updatedTemplate = JsonOutput.toJson(finalTemplate)

      def output = updatedTemplate
        .replace('$BUILD_URL', env.BUILD_URL)
        .replace('$BUILD_NUMBER', env.BUILD_NUMBER)
        .replace('$JOBNAME', env.JOB_NAME)
        .replace('$MESSAGE', !moduleFailureFields.isEmpty() ? "" : message)
        .replace('$RP_URL',
          useReportPortal ? ReportPortalTestType.fromType(testType).reportPortalDashboardURL() : "")

      return output
      break
    default:
      throw new IllegalArgumentException("Failed to render Slack Notification")
  }
}

/**
 * Send slack notifications regarding karate tests execution results
 * @param karateTestsExecutionSummary karate tests execution statistics
 * @param teamAssignment teams assignment to modules
 */
@Deprecated
void sendKarateTeamSlackNotification(KarateRunExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
  // collect modules tests execution results by team
  Map<Team, List<KarateModuleExecutionSummary>> teamResults = [:]
  def teamByModule = teamAssignment.getTeamsByModules()
  karateTestsExecutionSummary.getModulesExecutionSummary().values().each { moduleExecutionSummary ->
    if (teamByModule.containsKey(moduleExecutionSummary.getName())) {
      def team = teamByModule.get(moduleExecutionSummary.getName())
      if (!teamResults.containsKey(team)) {
        teamResults[team] = []
      }
      teamResults[team].add(moduleExecutionSummary)
      println "Module '${moduleExecutionSummary.name}' is assignned to '${team.name}' team"
    } else {
      println "Module '${moduleExecutionSummary.name}' is not assigned to any team"
    }
  }

  // iterate over teams and send slack notifications
  def buildStatus = currentBuild.result
  teamResults.each { entry ->
    def message = ""
    def moduleFailureFields = []
    def testsStatus = "SUCCESS"
    entry.value.each { moduleTestResult ->
      if (moduleTestResult.getExecutionResult() == TestExecutionResult.FAILED) {
        def moduleName = moduleTestResult.getName()
        def failures = moduleTestResult.getFeaturesFailedCount()
        def totalTests = moduleTestResult.getFeaturesTotalCount()

        moduleFailureFields << [
          title: ":gear: $moduleName",
          value: "Has $failures failures of $totalTests total tests",
          short: true
        ]
        testsStatus = "FAILED"
      }
    }
    try {
      if (moduleFailureFields.isEmpty()) {
        message += "All modules for ${entry.key.name} team have successful result\n"
      }
      // Existing tickets - created more than 1 hour ago
      def existingTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created < -1h")
      // Created tickets by this run - Within the last 20 min
      def createdTickets = karateTestUtils.getJiraIssuesByTeam(entry.key.name, "created > -20m")

      slackSend(
        attachments: renderSlackMessage(TestType.KARATE, buildStatus, testsStatus, message, false,
          moduleFailureFields, existingTickets, createdTickets),
        channel: entry.key.slackChannel
      )
    } catch (Exception e) {
      println("Unable to send slack notification to channel '${entry.key.slackChannel}'")
      e.printStackTrace()
    }
  }
}

@Deprecated
void sendSlackNotification(TestType type, String message, String channel, String buildStatus,
                           boolean useReportPortal = false) {
  def attachments = renderSlackMessage(type, buildStatus, "", message, useReportPortal)
  slackSend(attachments: attachments, channel: channel)
}

@Deprecated
void sendPipelineFailSlackNotification(channel) {
  def attachments = renderSlackMessage(null, "FAILED", "", "")
  slackSend(attachments: attachments, channel: channel)
}
