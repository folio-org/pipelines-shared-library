#!groovy
import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.rest.model.DataMigrationTenant
import java.time.*
import org.folio.rest.GitHubUtility
import org.folio.Constants
import groovy.json.JsonSlurperClassic

@Library('pipelines-shared-library@RANCHER-907') _

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
        folioParameters.repository(),
        folioParameters.branch( 'folio_branch_src',params.folio_branch_src),
        folioParameters.branch('folio_branch_src', params.folio_branch_dst),
        string(name: 'backup_name', defaultValue: '', description: '(Optional) RDS snapshot name. If empty create env from scratch', trim: true),
        string(name: 'slackChannel', defaultValue: '', description: 'Slack channel name where send report (without #)', trim: true),
        folioParameters.refreshParameters()
        ])])


def rancher_cluster_name = 'folio-perf'
def rancher_project_name = 'data-migration'
def config_type = 'performance'
def tenant_id
def admin_username
def admin_password
def startMigrationTime = LocalDateTime.now()
Integer totalTimeInMs = 0
LinkedHashMap modulesLongMigrationTimeSlack = [:]
List modulesMigrationFailedSlack = []
def resultMap = [:]
def okapiVersion = getOkapiVersion(params.folio_repository, params.folio_branch_src)
def srcInstallJson = new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch_src)
def dstInstallJson = new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch_dst)

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
                if (params.backup_name) {
                    tenant_id = 'fs09000000'
                    admin_username = 'folio'
                    admin_password = 'folio'
                    buildName tenant_id + '-' + params.backup_name + '.' + env.BUILD_ID
                } else {
                    tenant_id = 'diku'
                    admin_username = 'diku'
                    admin_password = 'diku_admin'
                    buildName tenant_id + '.' + 'without-restore' + '.' + env.BUILD_ID
                }


            }

            stage('Destroy data-migration project') {
                def jobParameters = getEnvironmentJobParameters('destroy', rancher_cluster_name,
                    rancher_project_name, params.folio_repository, params.folio_branch_src,
                    okapiVersion, tenant_id, admin_username, admin_password, params.backup_name)

                build job: Constants.JENKINS_JOB_PROJECT, parameters: jobParameters, wait: true, propagate: false
            }

            stage('Restore data-migration project from backup') {
                if (params.backup_name) {
                    def jobParameters = getEnvironmentJobParameters('apply', rancher_cluster_name,
                        rancher_project_name, params.folio_repository, params.folio_branch_src,
                        okapiVersion, tenant_id, admin_username, admin_password, params.backup_name, true)

                    build job: Constants.JENKINS_JOB_PROJECT, parameters: jobParameters, wait: true, propagate: false
                }
            }

            stage('Create data-migration project') {
                if (!params.backup_name) {
                    def jobParameters = getEnvironmentJobParameters('apply', rancher_cluster_name,
                        rancher_project_name, params.folio_repository, params.folio_branch_src,
                        okapiVersion, tenant_id, admin_username, admin_password, params.backup_name, false, true, true, true)

                    build job: Constants.JENKINS_JOB_PROJECT, parameters: jobParameters, wait: true, propagate: false
                }
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
                        string(name: 'admin_username', value: admin_username),
                        string(name: 'admin_password', value: admin_password)
                    ]
            }


            stage('Generate Data Migration Time report') {
                //sleep time: 5, unit: 'MINUTES'

                folioexecuteDataMigrationUtils.getMigrationTime(
                    rancher_cluster_name,
                    rancher_project_name,
                    resultMap,
                    srcInstallJson,
                    dstInstallJson,
                    totalTimeInMs,
                    modulesLongMigrationTimeSlack,
                    modulesMigrationFailedSlack,
                    startMigrationTime
                )
            }



        } catch (exception) {
            currentBuild.result = 'FAILURE'
            error(exception.getMessage())
        } finally {
            stage('Publish HTML Reports') {
                publishHTML([
                    reportDir: 'reportTime',
                    reportFiles: '*.html',
                    reportName: 'Data Migration Time',
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true])
            }

            stage('Send Slack notification') {
                folioexecuteDataMigrationUtils.sendSlackNotification("#${params.slackChannel}")
            }

            stage('Destroy data-migration project') {
                def jobParameters = getEnvironmentJobParameters('destroy', rancher_cluster_name,
                    rancher_project_name, params.folio_repository, params.folio_branch_src,
                    okapiVersion, tenant_id, admin_username, admin_password, params.backup_name)

                build job: Constants.JENKINS_JOB_PROJECT, parameters: jobParameters, wait: true, propagate: false
            }

            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}


private List getEnvironmentJobParameters(String action, String clusterName, String projectName, String folio_repository,
                                         String folio_branch, String okapiVersion, String tenant_id, String admin_username,
                                         String admin_password, String backup_name, boolean restore_from_backup = false,
                                         boolean load_reference = false, boolean load_sample = false, boolean pg_embedded = false,
                                         boolean kafka_shared = true, boolean opensearch_shared = true, boolean s3_embedded = false) {
    [
        string(name: 'action', value: action),
        string(name: 'rancher_cluster_name', value: clusterName),
        string(name: 'rancher_project_name', value: projectName),
        string(name: 'folio_repository', value: folio_repository),
        string(name: 'folio_branch', value: folio_branch),
        string(name: 'okapi_version', value: okapiVersion),
        string(name: 'config_type', value: 'performance'),
        string(name: 'tenant_id', value: tenant_id),
        string(name: 'admin_username', value: admin_username),
        string(name: 'admin_password', value: admin_password),
        string(name: 'backup_type', value: 'rds'),
        string(name: 'backup_name', value: backup_name),
        booleanParam(name: 'restore_from_backup', value: restore_from_backup),
        booleanParam(name: 'load_reference', value: load_reference),
        booleanParam(name: 'load_sample', value: load_sample),
        booleanParam(name: 'pg_embedded', value: pg_embedded),
        booleanParam(name: 'kafka_shared', value: kafka_shared),
        booleanParam(name: 'opensearch_shared', value: opensearch_shared),
        booleanParam(name: 's3_embedded', value: s3_embedded)
    ]
}
