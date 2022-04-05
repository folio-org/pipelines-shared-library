import org.folio.karate.results.KarateExecutionResult
import org.folio.karate.results.KarateModuleTestResult
import org.folio.karate.results.KarateTestsResult
import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignment

KarateTestsResult collectTestsResults(String karateSummaryFolder) {
    def retVal = new KarateTestsResult()
    def karateSummaries = findFiles(glob: karateSummaryFolder)
    karateSummaries.each { karateSummary ->
        echo "Collecting tests execution result from '${karateSummary.path}' file"
        String[] split = karateSummary.path.split("/")
        String moduleName = split[split.size() - 4]

        def contents = readJSON file: karateSummary.path
        retVal.addModuleResult(moduleName, contents.featuresPassed, contents.featuresFailed, contents.featuresSkipped)
    }

    retVal
}

def sendSlackNotification(KarateTestsResult karateTestsResult, TeamAssignment teamAssignment) {
    // collect modules tests execution results by team
    Map<KarateTeam, List<KarateModuleTestResult>> teamResults = [:]
    def teamByModule = teamAssignment.getTeamsByModules()
    karateTestsResult.getModulesTestResult().values().each { moduleTestResult ->
        if (teamByModule.containsKey(moduleTestResult.getName())) {
            def team = teamByModule.get(moduleTestResult.getName())
            if (!teamResults.containsKey(team)) {
                teamResults[team] = []
            }
            teamResults[team].add(moduleTestResult)
            println "Module '${moduleTestResult.name}' is assignned to '${team.name}'"
        } else {
            println "Module '${moduleTestResult.name}' is not assigned to any team"
        }
    }

    // iterate over teams and send slack notifications
    def buildStatus = currentBuild.result
    teamResults.each { entry ->
        def message = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n"
        entry.value.each { moduleTestResult ->
            if (moduleTestResult.getExecutionResult() == KarateExecutionResult.FAIL) {
                message += "Module '${moduleTestResult.getName()}' has ${moduleTestResult.getFailedCount()} failures of ${moduleTestResult.getTotalCount()} total tests.\n"
            }
        }

        message += "Target channel: ${entry.key.slackChannel}"
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
