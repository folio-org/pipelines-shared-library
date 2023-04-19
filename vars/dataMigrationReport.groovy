#!groovy
import groovy.json.*
import groovy.xml.MarkupBuilder
import java.util.concurrent.*
import java.util.Date
import groovy.text.GStringTemplateEngine
import org.folio.utilities.Tools

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
def createHtmlReport(tenantName, tenants) {
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
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #3", "Time(HH:MM:SS)")
                }
            }
            markup.tbody {
                groupByTenant[tenantName].each { tenantInfo -> 
                    def moduleName = tenantInfo.moduleInfo.moduleName
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
                        markup.td(style: "padding: 5px; border: solid 1px #777;", moduleTime)
                    }
                }
                markup.tr(style: "padding: 5px; border: solid 1px #777;") {
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
    def buildStatus = currentBuild.currentResult
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
    message += "Detailed time report: ${env.BUILD_URL}Data_20Migration_20Time/\n"

    try {
        println message
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
def createDiffHtmlReport(diff, pgadminURL) {
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
            builder.a(href: "https://www.pgadmin.org/docs/pgadmin4/6.18/schema_diff.html", target: "_blank") {
                builder.h2("Documentation")
            }
            builder.a(href: pgadminURL, target: "_blank") {
                builder.h2("pgAdmin")
            }
            builder.nav {
                builder.ul {
                    diff.each { resp ->
                        builder.li {
                            builder.a(href: "#" + resp.key) {
                                builder.h4(resp.key)
                            }
                        }
                    }
                }
            }
            diff.each { resp ->
                builder.section(id: resp.key) {
                    builder.h2(resp.key)
                    builder.p(style: "white-space: pre-line", resp.value)
                }
            }
        }
    }

    // Write diff.html file to the Jenkins workspace
    writeFile file: "reportSchemas/diff.html", text: writer.toString()
}