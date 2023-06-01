#!groovy
import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.rest.model.DataMigrationTenant
import java.time.*
import org.folio.rest.GitHubUtility
import org.folio.Constants
import groovy.json.JsonSlurperClassic

@Library('pipelines-shared-library') _

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
def resultMap = [:]
def pgadminURL = "https://$rancher_cluster_name-$rancher_project_name-pgadmin.ci.folio.org/"

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node('rancher') {
        try {
            stage('Init') {
                currentBuild.result = 'SUCCESS'
                buildName tenant_id + '-' + backup_name + '.' + env.BUILD_ID

                // Create map with moduleName, source and destination version for this module
                // This map used for time migration and schemaDiff reports
                def srcInstallJson = new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch_src)
                def dstInstallJson = new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch_dst)

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
            }
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

                // Get logs about activating modules from elasticseach
                def result = dataMigrationReport.getESLogs(rancher_cluster_name, "logstash-$rancher_project_name", startMigrationTime) 
                
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

                // Grouped modules by tenant name and generate HTML report
                def uniqTenants = tenants.tenantName.unique()
                uniqTenants.each { tenantName ->
                    (htmlData, totalTime, modulesLongMigrationTime, modulesMigrationFailed) = dataMigrationReport.createTimeHtmlReport(tenantName, tenants)
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

                        // Preparation steps, creating Atlas and psql clien pods
                        def atlasPod = "atlas"
                        kubectl.runPodWithCommand(rancher_project_name, atlasPod, 'arigaio/atlas:0.10.1-alpine')
                        
                        // Temporary solution. After migartion to New Jenkins we can connect from jenkins to RDS
                        def psqlPod = "psql-client"
                        kubectl.runPodWithCommand(rancher_project_name, psqlPod, 'andreswebs/postgresql-client')
                        
                        // Getting list of schemas for fs09000000 and clean tenants
                        def srcSchemasList = getSchemaTenantList(rancher_project_name, psqlPod, tenant_id, psqlConnection)
                        def dstSchemasList = getSchemaTenantList(rancher_project_name, psqlPod, tenant_id_clean, psqlConnection)

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
                        
                        // Make list with unique schemas 
                        if (uniqueValues) {
                            diff.put("Unique schemas", "Please check list of unique Schemas:\n $uniqueValues")
                        }
                        
                        groupedValues.each {
                            try {
                                def getDiffCommand = "./atlas schema diff --from 'postgres://${psqlConnection.user}:${psqlConnection.password}@${psqlConnection.host}:${psqlConnection.port}/${psqlConnection.db}?search_path=${it.key}' --to 'postgres://${psqlConnection.user}:${psqlConnection.password}@${psqlConnection.host}:${psqlConnection.port}/${psqlConnection.db}?search_path=${it.value}'"
                                def currentDiff =  sh(returnStdout: true, script: "set +x && kubectl exec ${atlasPod} -n ${rancher_project_name} -- ${getDiffCommand}").trim()

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

                        def diffHtmlData
                        if (diff) {
                            diffHtmlData = dataMigrationReport.createDiffHtmlReport(diff, pgadminURL, resultMap)
                            currentBuild.result = 'UNSTABLE'
                        } else {
                            diff.put('All schemas', 'Schemas are synced, no changes to be made.')
                            diffHtmlData = dataMigrationReport.createDiffHtmlReport(diff, pgadminURL)
                        } 
                        writeFile file: "reportSchemas/diff.html", text: diffHtmlData
                }                
            }

            stage('Publish HTML Reports') {
                publishHTML([
                    reportDir: 'reportSchemas',
                    reportFiles: '*.html',
                    reportName: 'Schemas Diff',
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true ])

                publishHTML([
                    reportDir: 'reportTime',
                    reportFiles: '*.html',
                    reportName: 'Data Migration Time',
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true])
            }           

        } catch (exception) {
            currentBuild.result = 'FAILURE'
            error(exception.getMessage())
        } finally {
            stage('Send Slack notification') {
                dataMigrationReport.sendSlackNotification("#${params.slackChannel}", totalTimeInMs, modulesLongMigrationTimeSlack, modulesMigrationFailedSlack)
            }
            
            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}

// Temporary solution. After migartion to New Jenkins we can connect from jenkins to RDS. Need to rewrite
def getSchemaTenantList(namespace, psqlPod, tenantId, dbParams) {
    println("Getting schemas list for $tenantId tenant")
    def getSchemasListCommand = """psql 'postgres://${dbParams.user}:${dbParams.password}@${dbParams.host}:${dbParams.port}/${dbParams.db}' --tuples-only -c \"SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE '${tenantId}_%'\""""
    
    kubectl.waitPodIsRunning(namespace, psqlPod)
    def schemasList = kubectl.execCommand(namespace, psqlPod, getSchemasListCommand)
    return schemasList.split('\n').collect({it.trim()})
}