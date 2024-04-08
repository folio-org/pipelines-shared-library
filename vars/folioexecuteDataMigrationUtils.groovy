#!groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.*
import groovy.xml.MarkupBuilder
import org.folio.rest.model.DataMigrationTenant
import java.time.LocalDateTime
import java.util.concurrent.*
import java.util.Date
import groovy.text.GStringTemplateEngine
import org.folio.utilities.Tools
import org.folio.client.jira.JiraClient
import org.folio.client.jira.model.JiraIssue
import org.folio.karate.teams.TeamAssignment

def getMigrationTime(rancher_cluster_name,rancher_project_name,resultMap,srcInstallJson,dstInstallJson,totalTimeInMs,modulesLongMigrationTimeSlack,modulesMigrationFailedSlack,startMigrationTime,pgadminURL){


    srcInstallJson.each { item ->
        def (fullModuleName, moduleName, moduleVersion) = (item.id =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)[0]
        resultMap[moduleName] = [srcVersion: moduleVersion]
    }

    dstInstallJson.each { item ->
        def (fullModuleName, moduleName, moduleVersion) = (item.id =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)[0]
        if (!resultMap.containsKey(moduleName)) {
            // Create an empty map if it doesn't exist
            resultMap[moduleName] = [:]
        }
        resultMap[moduleName]['dstVersion'] = moduleVersion
    }


    // Get logs about activating modules from elasticseach
    def result = getESLogs(rancher_cluster_name, "logstash-$rancher_project_name", startMigrationTime)

    println("STARTTIME: ${startMigrationTime}")
    println("SOURCE: ${srcInstallJson}")
    println("DESTINATION: ${dstInstallJson}")
    println("RESULT: ${result}")


    // Create tenants map with information about each module: moduleName, moduleVersionDst, moduleVersionSrc and migration time
    def tenants = []
    result.hits.hits.each {
        def logField = it.fields.log[0]
        def parsedMigrationInfo= logField.split("'")
        def (fullModuleName,moduleName,moduleVersion) = (parsedMigrationInfo[1] =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)[0]
        def time

        try {
            def parsedTime = logField.split("completed successfully in ")
            time = parsedTime[1].minus("ms").trim()
        } catch (ArrayIndexOutOfBoundsException exception) {
            time = "failed"
        }

        if (moduleName.startsWith("mod-") && resultMap[moduleName].dstVersion == moduleVersion) {
            def bindingMap = [tenantName: parsedMigrationInfo[3],
                              moduleInfo: [moduleName: moduleName,
                                           moduleVersionDst: resultMap[moduleName].dstVersion,
                                           moduleVersionSrc: resultMap[moduleName].srcVersion,
                                           execTime: time]]

            tenants += new DataMigrationTenant(bindingMap)
        }
    }
    println("TENANTS: ${tenants}")
    println("UNIQUE: ${uniqTenants}")
    // Grouped modules by tenant name and generate HTML report
    def uniqTenants = tenants.tenantName.unique()
    uniqTenants.each { tenantName ->
        (htmlData, totalTime, modulesLongMigrationTime, modulesMigrationFailed) = createTimeHtmlReport(tenantName, tenants)
        totalTimeInMs += totalTime
        modulesLongMigrationTimeSlack += modulesLongMigrationTime
        modulesMigrationFailedSlack += modulesMigrationFailed
        writeFile file: "reportTime/${tenantName}.html", text: htmlData
    }
    return [totalTimeInMs, modulesLongMigrationTimeSlack, modulesMigrationFailedSlack]

}

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


