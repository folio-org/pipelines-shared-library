import org.folio.Constants
import org.folio.client.jira.JiraClient
import org.folio.karate.results.KarateExecutionResult
import org.folio.karate.results.KarateModuleExecutionSummary
import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignment

KarateTestsExecutionSummary collectTestsResults(String karateSummaryFolder) {
    def retVal = new KarateTestsExecutionSummary()
    def karateSummaries = findFiles(glob: karateSummaryFolder)
    karateSummaries.each { karateSummary ->
        echo "Collecting tests execution result from '${karateSummary.path}' file"
        String[] split = karateSummary.path.split("/")
        String moduleName = split[split.size() - 4]

        def contents = readJSON file: karateSummary.path
        retVal.addModuleResult(moduleName, contents)
    }

    retVal
}

def sendSlackNotification(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
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
        def message = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n"
        entry.value.each { moduleTestResult ->
            if (moduleTestResult.getExecutionResult() == KarateExecutionResult.FAIL) {
                message += "Module '${moduleTestResult.getName()}' has ${moduleTestResult.getFeaturesFailed()} failures of ${moduleTestResult.getFeaturesTotal()} total tests.\n"
            }
        }

        message += "Target channel: ${entry.key.slackChannel}"
        // TODO: change channel to ${entry.key.slackChannel} after real integration in scope of https://issues.folio.org/browse/RANCHER-250
        slackSend(color: getSlackColor(buildStatus), message: message, channel: "#jenkins-test")
    }
}

def getSlackColor(def buildStatus) {
    if (buildStatus == 'STARTED') {
        '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        '#FFFE89'
    } else {
        '#FF9FA1'
    }
}

def createJiraTickets(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    def teamByModule = teamAssignment.getTeamsByModules()

    karateTestsExecutionSummary.modulesExecutionSummary.values()
        .findAll() { it.executionResult == KarateExecutionResult.FAIL }
        .each { moduleSummary ->
            moduleSummary.features
                .findAll() { it.failed }
                .each { featureSummary ->
                    echo "Create jira ticket for ${moduleSummary.name} '${featureSummary.name}'"
                    def summary = "${moduleSummary.name} '${featureSummary.name}' karate test fail"
                    def description = "${featureSummary.failedCount} of ${featureSummary.scenarioCount} scenarios have failed for ${featureSummary.name} feature.\n" +
                        "Feature name: ${featureSummary.name}\n" +
                        "Feature package name: ${featureSummary.packageQualifiedName}\n" +
                        "Feature path: ${featureSummary.relativePath}\n" +
                        "Jenkins job : ${env.JOB_NAME} #${env.BUILD_NUMBER}: ${env.BUILD_URL}\n" +
                        "Cucumber report: ${env.BUILD_URL}/cucumber-html-reports/overview-features.html"

                    def teamName = null
                    if (teamByModule[moduleSummary.name]) {
                        teamName = teamByModule[moduleSummary.name].name
                    } else {
                        echo "Module ${moduleSummary.name} is not assigned to any team."
                    }

                    echo "Summary: ${summary}"
                    echo "Description: ${description}"
                    echo "Team: ${teamName}"

                    //createFailedFeatureJiraTicket(summary, description, teamName)
                }
        }

}

def createFailedFeatureJiraTicket(String summary, String description, String team) {
    withCredentials([
        usernamePassword(credentialsId: Constants.JIRA_CREDENTIALS_ID, usernameVariable: 'jiraUsername', passwordVariable: 'jiraPassword')
    ]) {
        JiraClient jiraClient = new JiraClient(Constants.FOLIO_JIRA_URL, jiraUsername, jiraPassword)

        jiraClient.createJiraTicket "KRD",
            "Bug",
            summary,
            [Description       : description,
             Priority          : "P1",
             //Labels            : ["reviewed"],
             "Development Team": team]
    }
}

