#!groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.*
import groovy.xml.MarkupBuilder
import java.util.concurrent.*
import java.util.Date
import groovy.text.GStringTemplateEngine
import org.folio.utilities.Tools
import org.folio.client.jira.JiraClient
import org.folio.client.jira.model.JiraIssue
import org.folio.Constants
import org.folio.karate.teams.TeamAssignment

def getESLogs(cluster, indexPattern, startDate) {
    def template = "get-logs-ES.json.template"
    def binding = [
        cluster     : cluster,
        indexPattern: indexPattern,
        startDate   : startDate
    ]

    Tools tools = new Tools(this)
    tools.copyResourceFileToWorkspace("dataMigration/$template")
    def content = steps.readFile template
    String elasticRequestBody = new GStringTemplateEngine().createTemplate(content).make(binding).toString()

    def response = httpRequest httpMode: 'GET', url: "https://${cluster}-elasticsearch.ci.folio.org/${indexPattern}*/_search", validResponseCodes: '100:599', requestBody: elasticRequestBody, contentType: "APPLICATION_JSON"
    def result = new JsonSlurperClassic().parseText(response.content)

    return result
}

@NonCPS
def createTimeHtmlReport(tenantName, tenants) {
    def sortedList = tenants.sort {
        try {
            it.moduleInfo.execTime.toInteger()
        } catch (NumberFormatException ex) {
            println "Activation of module $it failed"
        }
    }

    def groupByTenant = sortedList.reverse().groupBy({
        it.tenantName
    })

    int totalTime = 0
    def modulesLongMigrationTime = [:]
    def modulesMigrationFailed = []
    def writer = new StringWriter()
    def markup = new groovy.xml.MarkupBuilder(writer)
    markup.html {
        markup.table(style: "border-collapse: collapse;") {
            markup.thead(style: "padding: 5px; border: solid 1px #777;") {
                markup.tr {
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #1", "Tenant name")
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #2", "Module name")
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #3", "Source version (from)")
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #4", "Destination version (to)")
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #5", "Time(HH:MM:SS)")
                }
            }
            markup.tbody {
                groupByTenant[tenantName].each { tenantInfo ->
                    def moduleName = tenantInfo.moduleInfo.moduleName
                    def moduleVersionDst = tenantInfo.moduleInfo.moduleVersionDst
                    def moduleVersionSrc = tenantInfo.moduleInfo.moduleVersionSrc
                    def execTime = tenantInfo.moduleInfo.execTime
                    def moduleTime
                    if(execTime == "failed") {
                        modulesMigrationFailed += moduleName
                        moduleTime = "failed"
                    } else if(execTime.isNumber()) {
                        totalTime += execTime.toInteger()
                        moduleTime = convertTime(execTime.toInteger())
                        if(execTime.toInteger() >= 300000) {
                            modulesLongMigrationTime.put(moduleName, execTime)
                        }
                    }

                    markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.tenantName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", moduleName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", moduleVersionSrc)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", moduleVersionDst)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", moduleTime)
                    }
                }
                markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", convertTime(totalTime.toInteger()))
                }
            }
        }
    }
    return [writer.toString(), totalTime, modulesLongMigrationTime, modulesMigrationFailed]
}

void sendSlackNotification(String slackChannel, Integer totalTimeInMs = null, LinkedHashMap modulesLongMigrationTime = [:], modulesMigrationFailed = []) {
    def buildStatus = currentBuild.result
    def message = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n"

    def totalTimeInHours = TimeUnit.MILLISECONDS.toHours(totalTimeInMs)
    if(totalTimeInHours >= 3) {
        message += "Please check: Data Migration takes $totalTimeInHours hours!\n"
    }

    if(modulesLongMigrationTime) {
        message += "List of modules with activation time bigger than 5 minutes:\n"
        modulesLongMigrationTime.each { moduleName ->
            def moduleTimeMinutes = TimeUnit.MILLISECONDS.toMinutes(moduleName.value.toInteger())
            message += "${moduleName.key} takes $moduleTimeMinutes minutes\n"
        }
    }

    if(modulesMigrationFailed) {
        message += "Modules with failed activation:\n"
        modulesMigrationFailed.each { moduleName ->
            message += "$moduleName\n"
        }
    }

    if (buildStatus == "FAILURE") {
        message += "Data Migration Failed. Please check logs in job."
    } else {
        message += "Detailed time report: ${env.BUILD_URL}Data_20Migration_20Time/\n"
        message += "Detailed Schemas Diff: ${env.BUILD_URL}Schemas_20Diff/\n"
    }

    try {
        slackSend(color: karateTestUtils.getSlackColor(buildStatus), message: message, channel: slackChannel)
    } catch (Exception e) {
        println("Unable to send slack notification to channel '${slackChannel}'")
        e.printStackTrace()
    }
}

@NonCPS
def convertTime(int ms) {
    long hours = TimeUnit.MILLISECONDS.toHours(ms);
    long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % TimeUnit.HOURS.toMinutes(1);
    long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % TimeUnit.MINUTES.toSeconds(1);

    String format = String.format("%02d:%02d:%02d", Math.abs(hours), Math.abs(minutes), Math.abs(seconds));

    return format
}

def getBackendModulesList(String repoName, String branchName){
    def installJson = new URL("https://raw.githubusercontent.com/folio-org/${repoName}/${branchName}/install.json").openConnection()
    if (installJson.getResponseCode().equals(200)) {
        List modules_list = ['okapi']
        new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.findAll { it ==~ /mod-.*/ }.each { value ->
            modules_list.add(value)
        }
        return modules_list.sort()
    }
}

@NonCPS
def createDiffHtmlReport(diff, pgadminURL, resultMap = null) {
    def writer = new StringWriter()
    def builder = new MarkupBuilder(writer)

    builder.html {
        builder.head {
            builder.style("""
                body {
                    font-family: Arial, sans-serif;
                }
                nav ul {
                    list-style: none;
                    padding: 0;
                    margin: 0;
                }
                nav ul li {
                    display: inline-block;
                    margin-right: 20px;
                }
                section {
                    margin-top: 40px;
                    border-top: 1px solid #ccc;
                }
                h2 {
                    display: inline-block;
                    font-size: 24px;
                    margin-bottom: 10px;
                    margin-right: 20px;
                }
                p {
                    font-size: 16px;
                    line-height: 1.5;
                    margin-bottom: 20px;
                }
            """)
        }
        builder.body {
            builder.a(href: pgadminURL, target: "_blank") {
                builder.h2("pgAdmin")
            }
            builder.a(href: "https://www.pgadmin.org/docs/pgadmin4/7.1/schema_diff.html", target: "_blank") {
                builder.h2("Documentation")
            }
            // Make navigation tab
            builder.nav {
                builder.ul {
                    diff.each { schema ->
                        builder.li {
                            builder.a(href: "#" + schema.key) {
                                builder.h4(schema.key)
                            }
                        }
                    }
                }
            }
            diff.each { schema ->
                if (schema.key == "Unique schemas") {
                    builder.section(id: schema.key) {
                        builder.h2(schema.key)
                        builder.p(style: "white-space: pre-line", schema.value)
                    }
                } else if (schema.key == "All schemas"){
                    builder.section(id: schema.key) {
                        builder.h2(schema.key)
                        builder.p(style: "white-space: pre-line", schema.value)
                    }
                } else {
                    def moduleName = schema.key.replaceFirst(/^[^_]*_mod_/, "mod_").replace("_", "-")
                    // Find srcVersion and dstVersion for the given schema name
                    def srcVersion = resultMap[moduleName]?.srcVersion
                    def dstVersion = resultMap[moduleName]?.dstVersion
                    builder.section(id: schema.key) {
                        builder.h2(schema.key)
                        builder.h4("Migrated from $srcVersion to $dstVersion version for $moduleName module")
                        builder.p(style: "white-space: pre-line", schema.value)
                    }
                }
            }
        }
    }

    return writer.toString()
}

def createSchemaDiffJiraIssue(schemaName, schemaDiff, resultMap, teamAssignment) {
    JiraClient jiraClient = JiraClient.getJiraClient(this)

    def moduleName = schemaName.replaceFirst(/^[^_]*_mod_/, "mod_").replace("_", "-")
    def srcVersion = resultMap[moduleName]?.srcVersion
    def dstVersion = resultMap[moduleName]?.dstVersion
    def summary = "${moduleName} from ${srcVersion} to ${dstVersion} version"

    String description = getIssueDescription(schemaName, schemaDiff, srcVersion, dstVersion)

    def fields = [
        Summary    : "${Constants.DM_ISSUE_SUMMARY_PREFIX} ${summary}",
        Description: description,
        Priority   : Constants.DM_JIRA_ISSUE_PRIORITY,
        Labels     : [Constants.DM_ISSUE_LABEL]
    ]

    def teamName = "TEAM_MISSING"
    def teamByModule = teamAssignment.getTeamsByModules()
    def team = teamByModule[moduleName]
    if (team) {
        teamName = team.name
        fields["Development Team"] = teamName
    } else {
        println "Module ${moduleName} is not assigned to any team."
    }

    try {
        List<JiraIssue> issues = jiraClient.searchIssues(Constants.DM_ISSUES_JQL, ["summary", "status"])
        Map<String, JiraIssue> issuesMap = issues.collectEntries { issue ->
            def issuesSummary = issue.summary
            [issuesSummary.substring(Constants.DM_ISSUE_SUMMARY_PREFIX.length(), issuesSummary.length()).trim(), issue]
        }

        if (issuesMap.containsKey(summary.toString())) {
            JiraIssue issue = issuesMap[summary]
            println "Update jira ticket for ${moduleName}, team '${teamName}'"
            jiraClient.addIssueComment(issue.id, description)
        } else {
            println "Create jira ticket for ${moduleName}, team '${teamName}'"
            def issueId = jiraClient.createJiraTicket Constants.DM_JIRA_PROJECT, Constants.DM_JIRA_ISSUE_TYPE, fields
            println "Jira ticket '${issueId}' created for ${moduleName}, team '${teamName}'"
        }
    } catch (e) {
        println("Unable to create Jira ticket. " + e.getMessage())
        e.printStackTrace()
    }
}

def getIssueDescription(schemaName, schemaDiff, srcVersion, dstVersion) {
    def description =
        "*Schema Name:* ${schemaName}\n" +
        "*Schema diff:* ${schemaDiff}\n" +
        "*Upgraded from:* ${srcVersion} *to* ${dstVersion} module version\n" +
        "*Build:* ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BUILD_URL})\n" +
        "*Schema Diff Report:* ${env.BUILD_URL}Schemas_20Diff/ \n"

    description
        .replaceAll("\\{", "&#123;")
        .replaceAll("\\{", "&#125;")
}

def getTeamAssignment() {
    Tools tools = new Tools(this)
    def assignmentPath = "teams-assignment.json"
    tools.copyResourceFileToWorkspace("dataMigration/$assignmentPath")
    def jsonContents = readJSON file: assignmentPath
    def teamAssignment = new TeamAssignment(jsonContents)
    return teamAssignment
}
