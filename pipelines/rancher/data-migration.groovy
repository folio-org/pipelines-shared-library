#!groovy
import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.rest.model.DataMigrationTenant
import java.time.*
import groovy.xml.MarkupBuilder

@Library('pipelines-shared-library@RANCHER-385') _

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
def tenant_id = 'fs09000000'
def tenant_id_clean ='clean'
def startMigrationTime = LocalDateTime.now()
Integer totalTimeInMs = 0
LinkedHashMap modulesLongMigrationTimeSlack = [:]
List modulesMigrationFailedSlack = []
def diff = [:]

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
                    propagate: false,
                    parameters: [
                        string(name: 'action', value: 'destroy'),
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch_src),
                        string(name: 'okapi_version', value: getOkapiVersion(params.folio_repository, params.folio_branch_src)),
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name),
                        string(name: 'config_type', value: config_type),
                        booleanParam(name: 'pg_embedded', value: false),
                        booleanParam(name: 'kafka_shared', value: false),
                        booleanParam(name: 'opensearch_shared', value: false),
                        booleanParam(name: 's3_embedded', value: false)
                    ]
            }
            stage('Restore data-migration project from backup') {
                build job: Constants.JENKINS_JOB_PROJECT,
                    propagate: false,
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
                        string(name: 'tenant_id', value: tenant_id),
                        string(name: 'admin_username', value: "folio"),
                        string(name: 'admin_password', value: "folio"),
                        booleanParam(name: 'pg_embedded', value: false),
                        booleanParam(name: 'kafka_shared', value: true),
                        booleanParam(name: 'opensearch_shared', value: true),
                        booleanParam(name: 's3_embedded', value: false)
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
                        string(name: 'tenant_id', value: tenant_id),
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
                        string(name: 'tenant_id', value: tenant_id),
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
                    writeFile file: "reportTime/${tenantName}.html", text: htmlData
                }         
            }

            stage('Create clean tenant') {
                build job: "Rancher/Update/create-tenant",
                    parameters: [
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name),
                        string(name: 'reference_tenant_id', value: tenant_id),
                        string(name: 'tenant_id', value: tenant_id_clean),
                        string(name: 'tenant_name', value: "Clean tenant"),
                        string(name: 'admin_username', value: "folio"),
                        string(name: 'admin_password', value: "folio"),
                        booleanParam(name: 'deploy_ui', value: false),
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch_dst)
                    ]
            }

            stage('Get schemas difference') {
                helm.k8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, rancher_cluster_name)
                        // Get psql connection parameters
                        Map psqlConnection = [
                            password : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_PASSWORD'),
                            host     : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_HOST'),
                            user     : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_USERNAME'),
                            db       : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_DATABASE'),
                            port     : kubectl.getSecretValue(rancher_project_name, 'db-connect-modules', 'DB_PORT')                                    
                        ]

                        // Preparation steps
                        sh "kubectl create deployment -n $rancher_project_name atlas --image=arigaio/atlas:0.10.1-alpine -- /bin/sh -c 'while true; do sleep 86400; done'"
                        
                        def atlasPod = sh(returnStdout: true, script: "kubectl get pods -n $rancher_project_name --selector=app=atlas -o=jsonpath='{.items[0].metadata.name}'").trim()
                        def psqlPod = sh(returnStdout: true, script: "kubectl get pods -n $rancher_project_name --selector=app=psql-client -o=jsonpath='{.items[0].metadata.name}'").trim()                                
                        
                        def srcSchemasList = getSchemaTenantList(tenant_id, psqlPod, rancher_project_name)
                        def dstSchemasList = getSchemaTenantList(tenant_id_clean, psqlPod, rancher_project_name)

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
                        
                        println "Grouped values: $groupedValues"
                        println "Unique values: $uniqueValues"
                        groupedValues.each {
                            try {
                                def currentDiff =  sh(returnStdout: true, script: "kubectl exec ${atlasPod} -n $rancher_project_name -- ./atlas schema diff --from 'postgres://${psqlConnection.user}:${psqlConnection.password}@${psqlConnection.host}:${psqlConnection.port}/${psqlConnection.db}?search_path=${it.key}' --to 'postgres://${psqlConnection.user}:${psqlConnection.password}@${psqlConnection.host}:${psqlConnection.port}/${psqlConnection.db}?search_path=${it.value}'").trim()
                                if (currentDiff == "Schemas are synced, no changes to be made.") {
                                    println "Schemas are synced, no changes to be made."
                                } else {
                                    diff.put(it.key, currentDiff)
                                }
                            } catch(exception) {
                                println exception
                                diff.put(it.key, "Changes were found in this scheme, but cannot be processed. \n Please compare ${it.key} and ${it.value} in pgAdmin Schema Diff UI")
                            }
                        }

                        if(diff) {
                            dataMigrationReport.createDiffHtmlReport(diff)
                            jobStatus = 'UNSTABLE'
                        } else {
                            diff.put('All schemas', 'Schemas are synced, no changes to be made.')
                            dataMigrationReport.createDiffHtmlReport(diff)
                            jobStatus = 'SUCCESS'
                        } 
                }                
            }

            stage('Publish HTML Reports') {
                publishHTML([
                    reportDir: 'reportSchemas',
                    reportFiles: 'diff.html',
                    reportName: 'Schemas Diff',
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true ])


                def htmlFiles = findFiles glob: 'reportTime/*.html'
                publishHTML([
                    reportDir: 'reportTime',
                    reportFiles: htmlFiles.join(','),
                    reportName: 'Data Migration Time',
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true])
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

def getSchemaTenantList(tenantId, psqlPod, namespace) {
  println("Getting schemas list for $tenantId tenant")

  return sh(
      script: """
        kubectl exec ${psqlPod} -n $namespace -- psql --tuples-only -c \"SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE '${tenantId}\\_%'\"
        """,
      returnStdout: true
      ).split('\n').collect({it.trim()})
}
