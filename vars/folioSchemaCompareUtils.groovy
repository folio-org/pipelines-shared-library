import com.cloudbees.groovy.cps.NonCPS
import groovy.xml.MarkupBuilder
import org.folio.Constants
import org.folio.client.jira.JiraClient
import org.folio.client.jira.model.JiraIssue
import org.folio.karate.teams.TeamAssignment
import org.folio.utilities.Tools

void getSchemasDifference(rancher_project_name, tenant_id, tenant_id_clean, pgadminURL, resultMap, diff) {
    //def diff = [:]

            Map psqlConnection = [
                password : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_PASSWORD'),
                host     : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_HOST'),
                user     : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_USERNAME'),
                db       : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_DATABASE'),
                port     : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_PORT')
            ]

            def teamAssignment = getTeamAssignment()

            def atlasPodName = "atlas"
            kubectl.runPodWithCommand(rancher_project_name, atlasPodName, 'arigaio/atlas:0.10.1-alpine')

            def psqlPodName = "psql-client"
            kubectl.runPodWithCommand(rancher_project_name, psqlPodName, 'andreswebs/postgresql-client')

            def srcSchemasList = getSchemaTenantList(rancher_project_name, psqlPodName, tenant_id, psqlConnection)
            def dstSchemasList = getSchemaTenantList(rancher_project_name, psqlPodName, tenant_id_clean, psqlConnection)

            def groupedValues = [:]
            def uniqueValues = []

            srcSchemasList.each { srcValue ->
                def currentSuffix = srcValue.split('_')[1..-1].join('_')
                dstSchemasList.each { dstValue ->
                    def newSuffix = dstValue.split('_')[1..-1].join('_')
                    if (currentSuffix == newSuffix) {
                        groupedValues[srcValue] = dstValue
                    }
                }
                if (!groupedValues.containsKey(srcValue)) {
                    uniqueValues.add(srcValue)
                }
            }

            dstSchemasList.each { dstValue ->
                def newSuffix = dstValue.split('_')[1..-1].join('_')
                def alreadyGrouped = false
                groupedValues.each { srcValue, existingValue ->
                    def currentSuffix = srcValue.split('_')[1..-1].join('_')
                    if (newSuffix == currentSuffix) {
                        alreadyGrouped = true
                    }
                }
                if (!alreadyGrouped) {
                    uniqueValues.add(dstValue)
                }
            }

            if (uniqueValues) {
                diff.put("Unique schemas", "Please check list of unique Schemas:\n $uniqueValues")
            }

            groupedValues.each { srcValue, dstValue ->
                try {
                    def dbConnectionString = "postgres://${psqlConnection.user}:${psqlConnection.password}@${psqlConnection.host}:${psqlConnection.port}/${psqlConnection.db}?sslmode=disable&search_path"
                    def getDiffCommand = "./atlas schema diff --from '${dbConnectionString}=${srcValue}' --to '${dbConnectionString}=${dstValue}'"
                    def currentDiff = sh(returnStdout: true, script: "set +x && kubectl exec ${atlasPodName} -n ${rancher_project_name} -- ${getDiffCommand}").trim()

                    if (currentDiff == "Schemas are synced, no changes to be made.") {
                        println "Schemas are synced, no changes to be made."
                    } else {
                        diff.put(srcValue, currentDiff)
                      println "Schemas are synced, but there are issues. Jira tickets creation disabled."
//                        createSchemaDiffJiraIssue(srcValue, currentDiff, resultMap, teamAssignment)
                    }
                } catch (exception) {
                    println exception
                    def messageDiff = "Changes were found in this scheme, but cannot be processed. \n" +
                        "Please compare ${srcValue} and ${dstValue} in pgAdmin Schema Diff UI \n"
                    diff.put(srcValue, messageDiff)
//                    createSchemaDiffJiraIssue(srcValue, messageDiff, resultMap, teamAssignment)
                }
            }

            def diffHtmlData
            if (diff) {
                diffHtmlData = createDiffHtmlReport(diff, pgadminURL, resultMap)
                currentBuild.result = 'UNSTABLE'
            } else {
                diff.put('All schemas', 'Schemas are synced, no changes to be made.')
                diffHtmlData = createDiffHtmlReport(diff, pgadminURL)
            }

            writeFile file: "reportSchemas/diff.html", text: diffHtmlData
}



// Jira Issue Creation Report

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


// get the TeamAssignments

def getTeamAssignment() {
    Tools tools = new Tools(this)
    def assignmentPath = "teams-assignment.json"
    tools.copyResourceFileToWorkspace("dataMigration/$assignmentPath")
    def jsonContents = readJSON file: assignmentPath
    def teamAssignment = new TeamAssignment(jsonContents)
    return teamAssignment
}


// Get the tennat list
def getSchemaTenantList(namespace, psqlPod, tenantId, dbParams) {
    println("Getting schemas list for $tenantId tenant")
    def getSchemasListCommand = """psql 'postgres://${dbParams.user}:${dbParams.password}@${dbParams.host}:${dbParams.port}/${dbParams.db}' --tuples-only -c \"SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE '${tenantId}_%'\""""

    kubectl.waitPodIsRunning(namespace, psqlPod)
    def schemasList = kubectl.execCommand(namespace, psqlPod, getSchemasListCommand)
    return schemasList.split('\n').collect({it.trim()})
}

 slack notfication
void sendSlackNotification(String slackChannel) {
    def buildStatus = currentBuild.result
    def message = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n"

    if (buildStatus == "FAILURE") {
        message += "Data Migration Failed. Please check logs in job."
    } else {
        message += "Detailed Schemas Diff: ${env.BUILD_URL}Schemas_20Diff/\n"
    }

    try {
        slackSend(color: '', message: message, channel: slackChannel)
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
