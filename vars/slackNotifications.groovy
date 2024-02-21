import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.folio.karate.results.KarateExecutionResult
import org.folio.karate.results.KarateModuleExecutionSummary
import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignment

def renderSlackMessage(String testName, buildStatus, testsStatus, message, moduleFailureFields = [], List<String> jiraIssueLinksExisting = [], List<String> jiraIssueLinksCreated = []) {

    Map pipelineTemplates = [
        SUCCESS: libraryResource("slackNotificationsTemplates/pipelineSuccessTemplate"),
        UNSTABLE: libraryResource("slackNotificationsTemplates/pipelineUnstableTemplate"),
        FAILED: libraryResource("slackNotificationsTemplates/pipelineFailedTemplate")
    ]
    Map karateTemplates = [
        SUCCESS: libraryResource("slackNotificationsTemplates/karateTemplates/successTemplate"),
        FAILED: libraryResource("slackNotificationsTemplates/karateTemplates/failureTemplate")
    ]
    Map cypressTemplates = [
        SUCCESS: libraryResource("slackNotificationsTemplates/cypressTemplates/successTemplate"),
        FAILED: libraryResource("slackNotificationsTemplates/cypressTemplates/failureTemplate")
    ]

    if (message.contains("Pass rate:")){
      def match = (message =~ /Pass rate: (\d+.\d+|\d+)%/)
      def passRate = match ? match[0][1].toFloat() : 0.0
      testsStatus = passRate > 50.0 ? "SUCCESS" : "FAILED"
    }

    def pipelineTemplate = pipelineTemplates[buildStatus]

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
            if(!moduleFailureFields.isEmpty()){
                extraFields << [
                    title: "Module Failures :no_entry:",
                    color: "#FF0000",
                    fields: moduleFailureFields
                ]
                testsStatus = "FAILED"
            }

            if (!jiraIssueLinksExisting.isEmpty() || !jiraIssueLinksCreated.isEmpty()) {

                def existingIssuesFilter = "(${jiraIssueLinksExisting.join('%2C%20')})"
                def createdIssuesFilter = "(${jiraIssueLinksCreated.join('%2C%20')})"

                def jiraIssueLinksExistingButton = [
                    type: "button",
                    url: "https://issues.folio.org/issues/?jql=issuekey%20in%20${existingIssuesFilter}",
                    text: "*Check out the existing issues* :information_source: "
                ]

                def jiraIssueLinksCreatedButton = [
                    type: "button",
                    url: "https://issues.folio.org/issues/?jql=issuekey%20in%20${createdIssuesFilter}",
                    text: "*Check out the created issues* :information_source: "
                ]

                extraFields << [
                    title: "Jira issues :warning:",
                    color: "#E9D502",
                    actions: [(jiraIssueLinksExisting ? jiraIssueLinksExistingButton : null), (jiraIssueLinksCreated ? jiraIssueLinksCreatedButton : null)].findAll { it != null }
                ]

            }

            def testsTemplate = testName == "karate" ? karateTemplates[testsStatus] :
                                testName == "cypress" ? cypressTemplates[testsStatus] : null

            def messageLines = message.tokenize("\n")
            message = messageLines.join("\\n")

            def finaleTemplate = new JsonSlurper().parseText(pipelineTemplate)
            def additionTemplate = new JsonSlurper().parseText(testsTemplate)

            finaleTemplate += additionTemplate
            finaleTemplate += extraFields
            updatedTemplate = JsonOutput.toJson(finaleTemplate)

            def output = updatedTemplate
                .replace('$BUILD_URL', env.BUILD_URL)
                .replace('$BUILD_NUMBER', env.BUILD_NUMBER)
                .replace('$JOBNAME', env.JOB_NAME)
                .replace('$MESSAGE', !moduleFailureFields.isEmpty() ? "" : message)

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
void sendKarateTeamSlackNotification(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    // collect modules tests execution results by team
    Map<KarateTeam, List<KarateModuleExecutionSummary>> teamResults = [:]
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
            if (moduleTestResult.getExecutionResult() == KarateExecutionResult.FAIL) {
                def moduleName = moduleTestResult.getName()
                def failures = moduleTestResult.getFeaturesFailed()
                def totalTests = moduleTestResult.getFeaturesTotal()

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

            slackSend(attachments: renderSlackMessage("karate", buildStatus, testsStatus, message, moduleFailureFields, existingTickets, createdTickets), channel: entry.key.slackChannel)
        } catch (Exception e) {
            println("Unable to send slack notification to channel '${entry.key.slackChannel}'")
            e.printStackTrace()
        }
    }
}

void sendKarateSlackNotification(message, channel, buildStatus) {
    def attachments = renderSlackMessage("karate", buildStatus, "", message)
    slackSend(attachments: attachments, channel: channel)
}

void sendCypressSlackNotification(message, channel, buildStatus) {
    def attachments = renderSlackMessage("cypress", buildStatus, "", message)
    slackSend(attachments: attachments, channel: channel)
}

void sendPipelineFailSlackNotification(channel) {
    def attachments = renderSlackMessage("", "FAILED", "", "")
    slackSend(attachments: attachments, channel: channel)
}
