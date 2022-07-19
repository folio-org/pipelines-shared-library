#!groovy
@Library('pipelines-shared-library@RANCHER-319-v2') _

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import java.time.LocalDateTime

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        jobsParameters.rancherClusters(),
        jobsParameters.projectName(),
        jobsParameters.restorePostgresqlFromBackup(),
        jobsParameters.restorePostgresqlBackupName()
    ])
])

def date_time = LocalDateTime.now().withNano(0).toString()
String started_by_user = params.restore_postgresql_from_backup ? '' : currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')[0]['userId']
String db_backup_name = params.restore_postgresql_from_backup ? params.restore_postgresql_backup_name : "backup_${date_time}-${started_by_user}.psql"

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        try {
            stage('Ini') {
                buildName params.rancher_cluster_name + '.' + params.rancher_project_name + '.' + env.BUILD_ID
                buildDescription "rancher_cluster_name: ${params.rancher_cluster_name}\n" +
                    "rancher_project_name: ${params.rancher_project_name}"
            }
            stage('Checkout') {
                checkout scm
            }
            withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                              credentialsId    : Constants.AWS_CREDENTIALS_ID,
                              accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                              secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                docker.image(Constants.PSQL_DUMP_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    psqlDumpMethods.configureKubectl(Constants.RANCHER_CLUSTERS_DEFAULT_REGION, params.rancher_cluster_name)
                    psqlDumpMethods.configureHelm(Constants.FOLIO_HELM_REPOSITORY_NAME, Constants.FOLIO_HELM_REPOSITORY_URL)
                    try {
                        if (params.restore_postgresql_from_backup == false) {
                            psqlDumpMethods.backupHelmInstall(env.BUILD_ID, Constants.FOLIO_HELM_REPOSITORY_NAME, Constants.PSQL_DUMP_HELM_CHART_NAME, Constants.PSQL_DUMP_HELM_INSTALL_CHART_VERSION, params.rancher_project_name, params.rancher_cluster_name, db_backup_name)
                            psqlDumpMethods.helmDelete(env.BUILD_ID, params.rancher_project_name)
                            println("\n\n\n")
                            println("\033[32m" + "PostgreSQL backup process SUCCESSFULLY COMPLETED\nYou can find your backup in AWS s3 bucket folio-postgresql-backups/" +
                                "${params.rancher_cluster_name}/${params.rancher_project_name}/${db_backup_name}" + "\n\n\n" + "\033[0m")
                        }
                        else {
                            psqlDumpMethods.restoreHelmInstall(env.BUILD_ID, Constants.FOLIO_HELM_REPOSITORY_NAME, Constants.PSQL_DUMP_HELM_CHART_NAME, Constants.PSQL_DUMP_HELM_INSTALL_CHART_VERSION, params.rancher_project_name, db_backup_name)
                            psqlDumpMethods.helmDelete(env.BUILD_ID, params.rancher_project_name)
                            println("\n\n\n")
                            println("\033[32m" + "PostgreSQL restore process SUCCESSFULLY COMPLETED\n" + "\n\n\n" + "\033[0m")
                        }
                    }
                    catch (exception) {
                        psqlDumpMethods.helmDelete(env.BUILD_ID, params.rancher_project_name)
                        println("\n\n\n")
                        println("\033[1;31m" + "PostgreSQL backup process was FAILED!!!\nPlease, check logs and try again.\n\n\n" + "\033[0m")
                        throw exception
                    }
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
