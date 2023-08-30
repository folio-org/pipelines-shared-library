#!groovy
import hudson.util.Secret
import org.jenkinsci.plugins.workflow.libs.Library
import java.time.*
import org.folio.rest.GitHubUtility
import org.folio.Constants
import groovy.json.JsonSlurperClassic

@Library('pipelines-shared-library@RANCHER-838') _

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        folioParameters.repository(),
        folioParameters.branch('folio_branch_src', 'folio_repository'),
        folioParameters.branch('folio_branch_dst', 'folio_repository'),
        string(name: 'backup_name', defaultValue: '', description: '(Optional) RDS snapshot name. If empty create env from scratch', trim: true),
        string(name: 'slackChannel', defaultValue: '', description: 'Slack channel name where send report (without #)', trim: true)])])
        folioParameters.refreshParameters()

def rancher_cluster_name = 'folio-perf'
def rancher_project_name = 'data-migration'
def config_type = 'performance'
def tenant_id
def admin_username
def admin_password
def tenant_id_clean ='clean'
def diff = [:]
def resultMap = [:]
def pgadminURL = "https://${rancher_cluster_name}-${rancher_project_name}-pgadmin.ci.folio.org/"
def okapiVersion = folioCommon.getOkapiVersion(params.folio_repository, params.folio_branch_src)

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

                // Create map with moduleName, source and destination version for this module
                // This map used for schemaDiff reports
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

            stage('Create clean tenant') {
                build job: Constants.JENKINS_JOB_CREATE_TENANT,
                parameters: [
                    string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                    string(name: 'rancher_project_name', value: rancher_project_name),
                    string(name: 'reference_tenant_id', value: tenant_id),
                    string(name: 'tenant_id', value: tenant_id_clean),
                    string(name: 'tenant_name', value: "Clean tenant"),
                    string(name: 'admin_username', value: admin_username),
                    string(name: 'admin_password', value: admin_password),
                    booleanParam(name: 'deploy_ui', value: false),
                    string(name: 'folio_repository', value: params.folio_repository),
                    string(name: 'folio_branch', value: params.folio_branch_dst)
                ]
            }

            stage('Get schemas difference') {
                folioHelm.withK8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, rancher_cluster_name)
                    folioSchemaCompareUtils.getSchemasDifference(
                        rancher_project_name,
                        tenant_id,
                        tenant_id_clean,
                        pgadminURL,
                        resultMap,
                        diff
                        )

                }
            }
        } catch (exception) {
            currentBuild.result = 'FAILURE'
            error(exception.getMessage())
        } finally {
            stage('Publish HTML Reports') {
                publishHTML([
                    reportDir: 'reportSchemas',
                    reportFiles: '*.html',
                    reportName: 'Schemas Diff',
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true ])

            }

            stage('Send Slack notification') {
                folioSchemaCompareUtils.sendSlackNotification("#${params.slackChannel}")
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
