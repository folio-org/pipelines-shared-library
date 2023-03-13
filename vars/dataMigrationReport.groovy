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
                    totalTime += tenantInfo.moduleInfo.execTime.isNumber() ? tenantInfo.moduleInfo.execTime as Integer: 0
                    markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.tenantName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.moduleInfo.moduleName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.moduleInfo.execTime)
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
    return writer.toString()
}
