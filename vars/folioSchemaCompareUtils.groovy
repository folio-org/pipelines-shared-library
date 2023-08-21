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
import groovy.json.JsonSlurperClassic




void sendSlackNotification(String slackChannel) {
    def buildStatus = currentBuild.result
    def message = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n"

    if (buildStatus == "FAILURE") {
        message += "Data Migration Failed. Please check logs in job."
    } else {
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
    JiraClient jiraClient = karateTestUtils.getJiraClient()

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
        List<JiraIssue> issues = jiraClient.searchIssuesKarate(Constants.DM_ISSUES_JQL, ["summary", "status"])
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


// Temporary solution. After migartion to New Jenkins we can connect from jenkins to RDS. Need to rewrite
def getSchemaTenantList(namespace, psqlPod, tenantId, dbParams) {
    println("Getting schemas list for $tenantId tenant")
    def getSchemasListCommand = """psql 'postgres://${dbParams.user}:${dbParams.password}@${dbParams.host}:${dbParams.port}/${dbParams.db}' --tuples-only -c \"SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE '${tenantId}_%'\""""

    kubectl.waitPodIsRunning(namespace, psqlPod)
    def schemasList = kubectl.execCommand(namespace, psqlPod, getSchemasListCommand)
    return schemasList.split('\n').collect({it.trim()})
}
