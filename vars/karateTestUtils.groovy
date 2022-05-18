import org.folio.Constants
import org.folio.client.jira.JiraClient
import org.folio.client.jira.model.JiraIssue
import org.folio.karate.KarateConstants
import org.folio.karate.results.KarateExecutionResult
import org.folio.karate.results.KarateFeatureExecutionSummary
import org.folio.karate.results.KarateModuleExecutionSummary
import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignment

/**
 * Collect karate tests execution statistics based on "karate-summary-json.txt" files content
 *
 * @param karateSummaryFolder karate summary folder (ant-style)
 * @return collected statistics
 */
KarateTestsExecutionSummary collectTestsResults(String karateSummaryFolder) {
    def retVal = new KarateTestsExecutionSummary()
    def karateSummaries = findFiles(glob: karateSummaryFolder)
    karateSummaries.each { karateSummary ->
        String path = karateSummary.path
        echo "Collecting tests execution result from '${path}' file"
        String[] split = path.split("/")
        String moduleName = split[split.size() - 4]

        def contents = readJSON file: path

        // find corresponding cucumber reports
        Map<String, String> displayNames = [:]
        def folder = path.substring(0, path.lastIndexOf("/"))
        println "Folder: ${folder}"
        findFiles(glob: "${folder}/*.json").each { report ->
            def reportContents = readJSON file: report.path

            String displayName = reportContents.name
            String nameSplit = displayName.split(" ")

            println nameSplit
            println "Feature name: ${nameSplit[nameSplit.size() - 1]}"
            println "Feature display name: ${displayName}"
            displayNames[nameSplit[nameSplit.size() - 1]] = displayName
        }

        retVal.addModuleResult(moduleName, contents, displayNames)
    }

    retVal
}

/**
 * Add cucumber reports feature report urls to karate tests execution statistics
 * @param summary karate tests execution statistics
 */
void attachCucumberReports(KarateTestsExecutionSummary summary) {
    copyCucumberReports()

    List<KarateFeatureExecutionSummary> features = summary.modulesExecutionSummary.collect { name, moduleSummary ->
        moduleSummary.features
    }.flatten()


    findFiles(glob: "**/cucumber-html-reports/report-feature*").each { file ->
        def contents = readFile(file.path)
        def feature = features.find { feature ->
            if (contents.contains(feature.relativePath)) {
                String pattern = KarateConstants.CUCUMBER_REPORT_PATTERN_START + feature.relativePath + KarateConstants.CUCUMBER_REPORT_PATTERN_END
                contents =~ pattern
            } else {
                false
            }
        }
        if (feature) {
            println "Cucumber report for '${feature.name}' feature is '${file.name}'"
            feature.cucumberReportFile = file.name
        }
    }
}

/**
 * Copy cucumber report plugin results from master node to slave workspace
 */
void copyCucumberReports() {
    def stashName = "cucumber-reports"
    node(Constants.JENKINS_MASTER_NODE) {
        def targetFolder = "${WORKSPACE}/${env.BUILD_NUMBER}"
        sh "mkdir -p '${targetFolder}'"

        def jobFolder = ""
        env.JOB_NAME.split("/").each { entry ->
            jobFolder += "/jobs/${entry}"
        }
        dir("${JENKINS_HOME}${jobFolder}/builds/${env.BUILD_NUMBER}") {
            sh "cp -r cucumber-html-reports '${targetFolder}'"
        }
        stash name: stashName, includes: "${env.BUILD_NUMBER}/cucumber-html-reports/**/*.*"
    }
    unstash name: stashName
}

/**
 * Send slack notifications regarding karate tests execution results
 * @param karateTestsExecutionSummary karate tests execution statistics
 * @param teamAssignment teams assignment to modules
 */
void sendSlackNotification(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
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

/**
 * Get slack color by build status
 * @param buildStatus jenkins build status
 * @return color code
 */
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

/**
 * Sync jira tickets for failed karate tests
 * @param karateTestsExecutionSummary karate tests execution statistics
 * @param teamAssignment teams assignment to modules
 */
void syncJiraIssues(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    JiraClient jiraClient = getJiraClient()

    // find existing karate issues
    List<JiraIssue> issues = jiraClient.searchIssues(KarateConstants.KARATE_ISSUES_JQL, ["summary", "status"])
    Map<String, JiraIssue> issuesMap = issues.collectEntries { issue ->
        def summary = issue.summary
        [summary.substring(KarateConstants.ISSUE_SUMMARY_PREFIX.length(), summary.length()).trim(), issue]
    }

    def teamByModule = teamAssignment.getTeamsByModules()
    karateTestsExecutionSummary.modulesExecutionSummary.values().each { moduleSummary ->
        moduleSummary.features.each { featureSummary ->
            // ignore features which has no report generated
            if (featureSummary.cucumberReportFile) {
                // No jira issue and feature failed
                if (!issuesMap.containsKey(featureSummary.relativePath) && featureSummary.failed) {
                    createFailedFeatureJiraIssue(moduleSummary, featureSummary, teamByModule, jiraClient)
                    // Jira issue exists
                } else if (issuesMap.containsKey(featureSummary.relativePath)) {
                    JiraIssue issue = issuesMap[featureSummary.relativePath]
                    jiraClient.addIssueComment(issue.id, getIssueDescription(featureSummary))


                    // Issue fixed and no any activity have been started on the issue
                    if (issue.status == KarateConstants.ISSUE_OPEN_STATUS && !featureSummary.failed) {
                        jiraClient.issueTransition(issue.id, KarateConstants.ISSUE_CLOSED_STATUS)
                        // Issue is in "In Review" status
                    } else if (issue.status == KarateConstants.ISSUE_IN_REVIEW_STATUS) {
                        // Feature us still failing
                        if (featureSummary.failed) {
                            jiraClient.issueTransition(issue.id, KarateConstants.ISSUE_OPEN_STATUS)
                            // Feature has been fixed
                        } else {
                            jiraClient.issueTransition(issue.id, KarateConstants.ISSUE_CLOSED_STATUS)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Create jira ticket using JiraClient
 * @param featureSummary KarateFeatureExecutionSummary object
 * @param jiraClient jira client
 */
void createFailedFeatureJiraIssue(KarateModuleExecutionSummary moduleSummary, KarateFeatureExecutionSummary featureSummary,
                                  Map<String, KarateTeam> teamByModule, JiraClient jiraClient) {
    def summary = "${KarateConstants.ISSUE_SUMMARY_PREFIX} ${featureSummary.relativePath}"
    String description = getIssueDescription(featureSummary)

    def fields = [
        Summary    : summary,
        Description: description,
        Priority   : KarateConstants.JIRA_ISSUE_PRIORITY,
        Labels     : [KarateConstants.ISSUE_LABEL]
    ]

    def teamName = teamByModule[moduleSummary.name]
    if (teamName) {
        fields["Development Team"] = teamName.name
    } else {
        echo "Module ${moduleSummary.name} is not assigned to any team."
    }

    try {
        echo "Create jira ticket for ${moduleSummary.name} '${featureSummary.name}', team '${teamName}'"
        jiraClient.createJiraTicket KarateConstants.JIRA_PROJECT, KarateConstants.JIRA_ISSUE_TYPE, fields
    } catch (e) {
        echo("Unable to create Jira ticket. " + e.getMessage())
        e.printStackTrace()
    }
}

private String getIssueDescription(KarateFeatureExecutionSummary featureSummary) {
    def title
    if (featureSummary.failed) {
        title = "${featureSummary.failedCount} of ${featureSummary.scenarioCount} scenarios have failed for '*_${featureSummary.name}_*' feature."
    } else {
        title = "No failures of ${featureSummary.scenarioCount} scenarios for '*_${featureSummary.name}_*' feature."
    }

    def description = "${title}\n" +
        "*Feature path:* ${featureSummary.relativePath}\n" +
        "*Jenkins job:* ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BUILD_URL})\n" +
        "*Cucumber overview report:* ${env.BUILD_URL}cucumber-html-reports/overview-features.html\n" +
        "*Cucumber feature report:* ${env.BUILD_URL}cucumber-html-reports/${featureSummary.cucumberReportFile}"
    description
}

private JiraClient getJiraClient() {
    withCredentials([
        usernamePassword(credentialsId: Constants.JIRA_CREDENTIALS_ID, usernameVariable: 'jiraUsername', passwordVariable: 'jiraPassword')
    ]) {
        return new JiraClient(this, Constants.FOLIO_JIRA_URL, jiraUsername, jiraPassword)
    }
}

