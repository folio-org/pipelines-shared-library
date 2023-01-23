#!groovy
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-384') _

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
        string(name: 'backup_name', defaultValue: '', description: 'RDS snapshot name', trim: true)])])

def rancher_cluster_name = 'folio-perf'
def rancher_project_name = 'data-migration'
def config_type = 'performance'

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node('jenkins-agent-java11') {
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
