#!groovy
import org.folio.Constants

@Library('pipelines-shared-library') _


import org.folio.rest.model.OkapiUser
import org.jenkinsci.plugins.workflow.libs.Library

import java.time.LocalDateTime

properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  parameters([
    booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
    jobsParameters.clusterName(),
    jobsParameters.projectName(),
    jobsParameters.agents(),
    jobsParameters.tenantIdToBackupModulesVersions(),
    jobsParameters.adminUsername(),
    jobsParameters.adminPassword(),
    jobsParameters.restoreFromBackup(),
    jobsParameters.backupName()
  ])
])

def date_time = LocalDateTime.now().withNano(0).toString()
String db_backup_name = params.restore_from_backup ? params.backup_name : "${params.rancher_cluster_name}-${params.rancher_project_name}-${params.tenant_id_to_backup_modules_versions}-${date_time}"
OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username, password: params.admin_password)
String postgresql_backups_directory = "postgresql"

ansiColor('xterm') {
  if (params.refreshParameters) {
    currentBuild.result = 'ABORTED'
    error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
  }
  node(params.agent) {
    try {
      stage('Ini') {
        buildName params.rancher_cluster_name + '.' + params.rancher_project_name + '.' + env.BUILD_ID
        buildDescription "rancher_cluster_name: ${params.rancher_cluster_name}\n" +
          "rancher_project_name: ${params.rancher_project_name}"
      }
      stage('Checkout') {
        checkout scm
      }

      folioHelm.withK8sClient {
        psqlDumpMethods.configureKubectl(Constants.AWS_REGION, params.rancher_cluster_name)
        psqlDumpMethods.configureHelm(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL)
        try {
          if (params.restore_from_backup == false) {
            psqlDumpMethods.backupHelmInstall(env.BUILD_ID, Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.PSQL_DUMP_HELM_CHART_NAME, Constants.PSQL_DUMP_HELM_INSTALL_CHART_VERSION, params.rancher_project_name, params.rancher_cluster_name, db_backup_name, params.tenant_id_to_backup_modules_versions, admin_user.username, admin_user.password, "s3://" + Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME, postgresql_backups_directory)
            psqlDumpMethods.savePlatformCompleteImageTag(params.rancher_project_name, db_backup_name, "s3://" + Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME, postgresql_backups_directory, params.tenant_id_to_backup_modules_versions)
            psqlDumpMethods.helmDelete(env.BUILD_ID, params.rancher_project_name)
            println("\n\n\n" + "\033[32m" + "PostgreSQL backup process SUCCESSFULLY COMPLETED\nYou can find your backup in AWS s3 bucket folio-postgresql-backups/" +
              "${params.rancher_cluster_name}/${params.rancher_project_name}/${db_backup_name}" + "\n\n\n" + "\033[0m")
          } else {
            psqlDumpMethods.restoreHelmInstall(env.BUILD_ID, Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.PSQL_DUMP_HELM_CHART_NAME, Constants.PSQL_DUMP_HELM_INSTALL_CHART_VERSION, params.rancher_project_name, db_backup_name, "s3://" + Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME, postgresql_backups_directory)
            psqlDumpMethods.helmDelete(env.BUILD_ID, params.rancher_project_name)
            println("\n\n\n" + "\033[32m" + "PostgreSQL restore process SUCCESSFULLY COMPLETED\n" + "\n\n\n" + "\033[0m")
          }
        }
        catch (exception) {
          psqlDumpMethods.helmDelete(env.BUILD_ID, params.rancher_project_name)
          println("\n\n\n" + "\033[1;31m" + "PostgreSQL backup/restore process was FAILED!!!\nPlease, check logs and try again.\n\n\n" + "\033[0m")
          throw exception
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
