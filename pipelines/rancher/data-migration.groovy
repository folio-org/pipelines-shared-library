#!groovy
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-384') _

import org.folio.Constants
import org.folio.utilities.Logger
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
        jobsParameters.branch(parameter_name = 'folio_branch_src'),
        jobsParameters.branch(parameter_name = 'folio_branch_dst'),
        jobsParameters.backupName()])])

def rancher_cluster_name = 'folio-tmp'
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
                        string(name: 'rancher_cluster_name', value: rancher_cluster_name),
                        string(name: 'rancher_project_name', value: rancher_project_name)
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
                        booleanParam(name: 'pg_embedded', value: false),
                        booleanParam(name: 'kafka_embedded', value: false),
                        booleanParam(name: 'es_embedded', value: false),
                        booleanParam(name: 's3_embedded', value: false),
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
