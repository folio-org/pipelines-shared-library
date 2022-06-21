#!groovy
@Library('pipelines-shared-library@RANCHER-313') _

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        jobsParameters.rancherClusters(),
        jobsParameters.projectName()
    ])
])

String tfWorkDir = 'terraform/rancher/psql-dump'
String tfVars = ''

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        try {
            stage('Ini') {
                buildName params.rancher_cluster_name + '.' + params.project_name + '.' + env.BUILD_ID
                buildDescription "rancher_cluster_name: ${params.rancher_cluster_name}\n" +
                    "rancher_project_name: ${params.project_name}"
            }
            stage('Checkout') {
                checkout scm
            }
            stage('TF vars') {
                tfVars += terraform.generateTfVar('rancher_cluster_name', params.rancher_cluster_name)
                tfVars += terraform.generateTfVar('rancher_project_name', params.project_name)
                tfVars += terraform.generateTfVar('pg_password', params.pg_password)
                tfVars += terraform.generateTfVar('pgadmin_password', params.pgadmin_password)
            }
            withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                              credentialsId    : Constants.AWS_CREDENTIALS_ID,
                              accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                              secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                             [$class           : 'AmazonWebServicesCredentialsBinding',
                              credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                              accessKeyVariable: 'TF_VAR_s3_access_key',
                              secretKeyVariable: 'TF_VAR_s3_secret_key'],
                             [$class           : 'AmazonWebServicesCredentialsBinding',
                              credentialsId    : Constants.AWS_S3_DATA_EXPORT_ID,
                              accessKeyVariable: 'TF_VAR_s3_data_export_access_key',
                              secretKeyVariable: 'TF_VAR_s3_data_export_secret_key'],
                             [$class           : 'AmazonWebServicesCredentialsBinding',
                              credentialsId    : Constants.AWS_S3_POSTGRES_BACKUPS,
                              accessKeyVariable: 'TF_VAR_s3_postgres_backups_access_key',
                              secretKeyVariable: 'TF_VAR_s3_postgres_backups_secret_key'],
                             string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, params.project_name)
                    terraform.tfStatePull(tfWorkDir)
                    terraform.tfPlan(tfWorkDir, tfVars)
                    terraform.tfApply(tfWorkDir)
                    terraform.tfDestroy(tfWorkDir, tfVars)
                }
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
