import groovy.text.SimpleTemplateEngine
import org.folio.Constants
import org.folio.client.jira.JiraClient
import org.folio.client.jira.model.JiraIssue
import org.folio.karate.KarateConstants
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

        // find corresponding cucumber reports to get feature display name
        Map<String, String> displayNames = [:]
        def folder = path.substring(0, path.lastIndexOf("/"))
        findFiles(glob: "${folder}/*.json").each { report ->
            def reportContents = readJSON file: report.path

            String displayName = reportContents[0].name
            String[] nameSplit = displayName.split(" ")

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

    findFiles(glob: "**/${env.BUILD_NUMBER}/cucumber-html-reports/report-feature*").each { file ->
        def contents = readFile(file.path)
        def feature = features.find { feature ->
            if (contents.contains(feature.displayName)) {
                def displayNamePattern = feature.displayName
                    .replaceAll("\\[", "\\\\[")
                    .replaceAll("\\]", "\\\\]")
                    .replaceAll("\\{", "\\\\{")
                    .replaceAll("\\}", "\\\\}")
                println displayNamePattern

                String pattern = KarateConstants.CUCUMBER_REPORT_PATTERN_START + displayNamePattern + KarateConstants.CUCUMBER_REPORT_PATTERN_END
                contents =~ pattern
            } else {
                false
            }
        }
        if (feature) {
            println "Cucumber report for '${feature.displayName} (${feature.name})' feature is '${file.name}'"
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
 * Sync jira tickets for failed karate tests
 * @param karateTestsExecutionSummary karate tests execution statistics
 * @param teamAssignment teams assignment to modules
 */
void syncJiraIssues(KarateTestsExecutionSummary karateTestsExecutionSummary, TeamAssignment teamAssignment) {
    JiraClient jiraClient = JiraClient.getJiraClient(this)

    // find existing karate issues
    List<JiraIssue> issues = jiraClient.searchIssues(KarateConstants.KARATE_ISSUES_JQL, ["summary", "status"])
    Map<String, JiraIssue> issuesMap = issues.collectEntries { issue ->
        def summary = toSearchableSummary(issue.summary)
        [summary.substring(KarateConstants.ISSUE_SUMMARY_PREFIX.length(), summary.length()).trim(), issue]
    }

    println("syncJiraIssues issues=${issues}")
    println("syncJiraIssues issuesMap=${issuesMap}")

    Map<String, KarateTeam> teamByModule = teamAssignment.getTeamsByModules()
    karateTestsExecutionSummary.modulesExecutionSummary.values().each { moduleSummary ->
        moduleSummary.features.each { featureSummary ->
            // No jira issue and feature failed
            def featureName = toSearchableSummary(featureSummary.displayName)
            if (!issuesMap.containsKey(featureName) && featureSummary.failed) {
                println("syncJiraIssues inside moduleSummary.features.each featureName=${featureName}")
                println("syncJiraIssues inside moduleSummary.features.each getIssueDescription(featureSummary)=${getIssueDescription(featureSummary)}")

                createFailedFeatureJiraIssue(moduleSummary, featureSummary, teamByModule, jiraClient)
                // Jira issue exists
            } else if (issuesMap.containsKey(featureName)) {
                JiraIssue issue = issuesMap[featureName]
                def description = getIssueDescription(featureSummary)
                try {
                    jiraClient.addIssueComment(issue.id, description)
                    echo "Add comment to jira ticket '${issue.getKey()}' for ${moduleSummary.name} '${featureSummary.name}'"
                } catch (Exception e) {
                    echo "Error updating '${issue.getKey()}' jira ticket description with following comment:\n ${description}'"
                    e.printStackTrace()
                }

                // Issue fixed and no any activity have been started on the issue
                if (issue.status == KarateConstants.ISSUE_OPEN_STATUS && !featureSummary.failed) {
                    jiraClient.issueTransition(issue.id, KarateConstants.ISSUE_CLOSED_STATUS)
                    echo "Jira ticket '${issue.getKey()}' status changed to 'Closed'"
                    // Issue is in "In Review" status
                } else if (issue.status == KarateConstants.ISSUE_IN_REVIEW_STATUS) {
                    // Feature us still failing
                    if (featureSummary.failed) {
                        jiraClient.issueTransition(issue.id, KarateConstants.ISSUE_OPEN_STATUS)
                        echo "Jira ticket '${issue.getKey()}' status changed to 'Open'"
                        // Feature has been fixed
                    } else {
                        jiraClient.issueTransition(issue.id, KarateConstants.ISSUE_CLOSED_STATUS)
                        echo "Jira ticket '${issue.getKey()}' status changed to 'Closed'"
                    }
                }
            }
        }
    }
}

String toSearchableSummary(String summary) {
    if (summary.contains("{") && summary.contains("}")) {
        return summary.split("\\{")[0].trim() + " " + summary.split("\\}")[1].trim()
    } else {
        println("Unexpected summary format '{' and '}' are missing: ${summary}")
        return  summary
    }
}

/**
 * Create jira ticket using JiraClient
 * @param featureSummary KarateFeatureExecutionSummary object
 * @param jiraClient jira client
 */
void createFailedFeatureJiraIssue(KarateModuleExecutionSummary moduleSummary, KarateFeatureExecutionSummary featureSummary,
                                  Map<String, KarateTeam> teamByModule, JiraClient jiraClient) {
    def summary = "${KarateConstants.ISSUE_SUMMARY_PREFIX} ${featureSummary.displayName}"
    String description = getIssueDescription(featureSummary)

    def fields = [
        Summary    : summary,
        Description: description,
        Priority   : KarateConstants.JIRA_ISSUE_PRIORITY,
        Labels     : [KarateConstants.ISSUE_LABEL]
    ]

    fields["Development Team"] = "Kitfox"

/*    def teamName = "TEAM_MISSING"
    def team = teamByModule[moduleSummary.name]
    if (team) {
        teamName = team.name
        fields["Development Team"] = teamName
    } else {
        echo "Module ${moduleSummary.name} is not assigned to any team."
    }*/

    try {
        echo "Create jira ticket for ${moduleSummary.name} '${featureSummary.name}', team '${teamName}'"
        println("createFailedFeatureJiraIssue fields=${fields}")
        def issueId = jiraClient.createJiraTicket KarateConstants.JIRA_PROJECT, KarateConstants.JIRA_ISSUE_TYPE, fields
        echo "Jira ticket '${issueId}' created for ${moduleSummary.name} '${featureSummary.name}', team '${teamName}'"
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
        "*Name:* ${featureSummary.displayName}\n" +
        "*Feature path:* ${featureSummary.relativePath}\n" +
        "*Jenkins job:* ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BUILD_URL})\n" +
        "*Cucumber overview report:* ${env.BUILD_URL}cucumber-html-reports/overview-features.html\n" +
        "*Cucumber feature report:* ${env.BUILD_URL}cucumber-html-reports/${featureSummary.cucumberReportFile}"

    description
        .replaceAll("\\{", "&#123;")
        .replaceAll("\\{", "&#125;")
}

def getJiraIssuesByTeam(String team, String timeFilter) {
    def ticketsByTeam = []

    List<JiraIssue> issuesByTeam = JiraClient.getJiraClient(this)
      .searchIssues(
        KarateConstants.KARATE_ISSUES_JQL + """ and "Development Team" = "${team}" and ${timeFilter} """
        , ["summary", "status"]
      ) as List<JiraIssue>

    issuesByTeam.each { issue -> ticketsByTeam += issue.key }
    return ticketsByTeam
}

String renderKarateConfig(String config, params){
    withCredentials([
        string(credentialsId: 'mod-kb-ebsco-url', variable: 'ebsco_url'),
        string(credentialsId: 'mod-kb-ebsco-id', variable: 'ebsco_id'),
        string(credentialsId: 'mod-kb-ebsco-key', variable: 'ebsco_key'),
        string(credentialsId: 'mod-kb-ebsco-usageId', variable: 'ebsco_usage_id'),
        string(credentialsId: 'mod-kb-ebsco-usageSecret', variable: 'ebsco_usage_secret'),
        string(credentialsId: 'mod-kb-ebsco-usageKey', variable: 'ebsco_usage_key')
    ]) {
        def engine = new SimpleTemplateEngine()
        Map binding = [
            "baseUrl"                            : params.okapiUrl,
            "edgeUrl"                            : params.edgeUrl,
            "centralServerUrl"                   : params.okapiUrl.replaceAll("okapi", "mockserver"),
            "admin"                              : [
                tenant  : params.tenant,
                name    : params.adminUserName,
                password: params.adminPassword
            ],
            "prototypeTenant"                    : params.prototypeTenant,
            "kbEbscoCredentialsUrl"              : ebsco_url,
            "kbEbscoCredentialsCustomerId"       : ebsco_id,
            "kbEbscoCredentialsApiKey"           : ebsco_key,

            "usageConsolidationCredentialsId"    : ebsco_usage_id,
            "usageConsolidationCredentialsSecret": ebsco_usage_secret,
            "usageConsolidationCustomerKey"      : ebsco_usage_key
        ]
        return engine.createTemplate(config.replaceAll(/(\\)/, /\\$0/)).make(binding).toString()
    }
}
