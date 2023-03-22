#!groovy
import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.rest.model.DataMigrationTenant
import java.time.*

@Library('pipelines-shared-library') _

import org.folio.Constants
import groovy.json.JsonSlurperClassic

String getOkapiVersion(folio_repository, folio_branch) {
    def installJson = new URL('https://raw.githubusercontent.com/folio-org/' + folio_repository + '/' + folio_branch + '/install.json').openConnection()
    if (installJson.getResponseCode().equals(200)) {
        String okapi = new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.find{it ==~ /okapi-.*/}
        if(okapi){
            return okapi - 'okapi-'
        } else {
            error("Can't get okapi version from install.json in ${folio_branch} branch of ${folio_repository} repository!" )
        }
    }
    error("There is no install.json in ${folio_branch} branch of ${folio_repository} repository!" )
}

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.repository(),
        jobsParameters.branch('folio_repository', 'folio_branch_src'),
        jobsParameters.branch('folio_repository', 'folio_branch_dst'),
        string(name: 'backup_name', defaultValue: '', description: 'RDS snapshot name', trim: true),
        string(name: 'slackChannel', defaultValue: '', description: 'Slack channel name where send report (without #)', trim: true)])])

def rancher_cluster_name = 'folio-perf'
def rancher_project_name = 'data-migration'
def config_type = 'performance'
def startMigrationTime = LocalDateTime.now()
Integer totalTimeInMs = 0
LinkedHashMap modulesLongMigrationTimeSlack = [:]
List modulesMigrationFailedSlack = []

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node('rancher') {
        try {
            stage('Destroy data-migration project') {
                build job: Constants.JENKINS_JOB_PROJECT,
                    parameters: [
                        string(name: 'action', value: 'destroy'),
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch_src),
                        string(name: 'okapi_version', value: getOkapiVersion(params.folio_repository, params.folio_branch_src)),
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name),
                        string(name: 'config_type', value: config_type),
                        booleanParam(name: 'pg_embedded', value: false),
                        booleanParam(name: 'kafka_embedded', value: false),
                        booleanParam(name: 'es_embedded', value: false),
                        booleanParam(name: 's3_embedded', value: false),
                        booleanParam(name: 'opensearch_dashboards', value: false)
                    ]
            }
            stage('Restore data-migration project from backup') {
                build job: Constants.JENKINS_JOB_PROJECT,
                    parameters: [
                        string(name: 'action', value: 'apply'),
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch_src),
                        string(name: 'okapi_version', value: getOkapiVersion(params.folio_repository, params.folio_branch_src)),
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name),
                        string(name: 'config_type', value: config_type),
                        booleanParam(name: 'restore_from_backup', value: true),
                        string(name: 'backup_type', value: 'rds'),
                        string(name: 'backup_name', value: params.backup_name),
                        string(name: 'tenant_id', value: "fs09000000"),
                        string(name: 'admin_username', value: "folio"),
                        string(name: 'admin_password', value: "folio"),
                        booleanParam(name: 'pg_embedded', value: false),
                        booleanParam(name: 'kafka_embedded', value: false),
                        booleanParam(name: 'es_embedded', value: false),
                        booleanParam(name: 's3_embedded', value: false),
                        booleanParam(name: 'opensearch_dashboards', value: false)
                    ]
            }
            stage('Update with src release versions') {
                build job: Constants.JENKINS_JOB_BACKEND_MODULES_DEPLOY_BRANCH,
                    parameters: [
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch_src),
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name),
                        string(name: 'config_type', value: config_type),
                        string(name: 'tenant_id', value: "fs09000000"),
                        string(name: 'admin_username', value: "folio"),
                        string(name: 'admin_password', value: "folio")
                    ]
            }
            stage('Update with dst release versions') {
                build job: Constants.JENKINS_JOB_BACKEND_MODULES_DEPLOY_BRANCH,
                    parameters: [
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch_dst),
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name),
                        string(name: 'config_type', value: config_type),
                        string(name: 'tenant_id', value: "fs09000000"),
                        string(name: 'admin_username', value: "folio"),
                        string(name: 'admin_password', value: "folio")
                    ]
            }
            stage('Generate Data Migration Time report') {
                sleep time: 5, unit: 'MINUTES'

                def backend_modules_list = dataMigrationReport.getBackendModulesList(params.folio_repository, params.folio_branch_dst)
                def result = dataMigrationReport.getESLogs(rancher_cluster_name, "logstash-$rancher_project_name", startMigrationTime) 
                def tenants = []
                result.hits.hits.each {
                    def logField = it.fields.log[0]
                    def parsedMigrationInfo= logField.split("'")
                    def time
                    try {
                      def parsedTime = logField.split("completed successfully in ")
                      time = parsedTime[1].minus("ms").trim()
                    } catch (ArrayIndexOutOfBoundsException exception) {
                      time = "failed"
                    }

                    if(backend_modules_list.contains(parsedMigrationInfo[1])){
                        def bindingMap = [tenantName: parsedMigrationInfo[3], 
                                        moduleInfo: [moduleName: parsedMigrationInfo[1], 
                                                        execTime: time]]
                    
                        tenants += new DataMigrationTenant(bindingMap)
                    }
                }

                def uniqTenants = tenants.tenantName.unique()
                uniqTenants.each { tenantName ->
                    (htmlData, totalTime, modulesLongMigrationTime, modulesMigrationFailed) = dataMigrationReport.createHtmlReport(tenantName, tenants)
                    totalTimeInMs += totalTime
                    modulesLongMigrationTimeSlack += modulesLongMigrationTime
                    modulesMigrationFailedSlack += modulesMigrationFailed
                    writeFile file: "${tenantName}.html", text: htmlData
                }
                
                if(uniqTenants){
                    def htmlFiles = findFiles glob: '*.html'
                    publishHTML([
                            reportDir: '',
                            reportFiles: htmlFiles.join(','),
                            reportName: 'Data Migration Time',
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true])
                }         
            }
            stage('Send Slack notification') {
                dataMigrationReport.sendSlackNotification("#${params.slackChannel}", totalTimeInMs, modulesLongMigrationTimeSlack, modulesMigrationFailedSlack)
            }            

        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}
