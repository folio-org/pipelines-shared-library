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
        try  {
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
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #3", "Time(ms)")
                }
            }
            markup.tbody {
                groupByTenant[tenantName].each { tenantInfo -> 
                    def tenantName = tenantInfo.tenantName
                    def moduleName = tenantInfo.moduleInfo.moduleName
                    def execTime = tenantInfo.moduleInfo.execTime

                    totalTime += execTime.isNumber() ? execTime as Integer: 0
                    markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", moduleName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", execTime)
                    }
                    if(execTime == "failed") {
                        modulesMigrationFailed += moduleName
                    } else if(execTime.isNumber() ? execTime as Integer: 0 > 300000){
                        modulesLongMigrationTime.put(moduleName, execTime)
                    }
                }
                markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", TimeUnit.MILLISECONDS.toMinutes(totalTime.toInteger()) 
                        + ' min')
                }
            }
        }
    }
    return [writer.toString(), totalTime, modulesLongMigrationTime, modulesMigrationFailed]
}

void sendSlackNotification(String slackChannel, Integer totalTimeInHours = null, LinkedHashMap modulesLongMigrationTime = [:]) {
    def buildStatus = currentBuild.result
    def message = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n"

    if(totalTimeInHours) {
        message += "Please check: Data Migration takes $totalTimeInHours hours!\n"
    }

    if(modulesLongMigrationTime) {
        message += "List of modules with activation time is bigger than 5 minutes:\n"
        modulesLongMigrationTime.each { moduleName ->
            message += "$moduleName\n"
        }
    }
    message += "Detailed time report: ${env.BUILD_URL}Data_20Migration_20Time/\n"

    try {
        slackSend(color: karateTestUtils.getSlackColor(buildStatus), message: message, channel: slackChannel)
    } catch (Exception e) {
        println("Unable to send slack notification to channel '${slackChannel}'")
        e.printStackTrace()
    }
}