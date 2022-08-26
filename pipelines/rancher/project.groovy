#!groovy
@Library('pipelines-shared-library@RANCHER-199') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiUser
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.okapiVersion(),
        jobsParameters.rancherClusters(),
        jobsParameters.projectName(),
        booleanParam(name: 'build_ui', defaultValue: true, description: 'Build UI image for frontend if false choose from dropdown next'),
        jobsParameters.frontendImageTag(),
        jobsParameters.envType(),
        jobsParameters.enableModules(),
        jobsParameters.tenantId(),
        jobsParameters.tenantName(),
        jobsParameters.tenantDescription(),
        jobsParameters.reindexElasticsearch(),
        jobsParameters.recreateindexElasticsearch(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.pgPassword(),
        jobsParameters.pgAdminPassword(),
        string(name: 'github_teams', defaultValue: '', description: 'Coma separated list of GitHub teams who need access to project'),
        jobsParameters.restorePostgresqlFromBackup(),
        jobsParameters.tenantIdToRestoreModulesVersions(),
        jobsParameters.restorePostgresqlBackupName(),
        booleanParam(name: 'pg_embedded', defaultValue: true, description: 'Embedded PostgreSQL or AWS RDS'),
        booleanParam(name: 'kafka_embedded', defaultValue: true, description: 'Embedded Kafka or AWS MSK'),
        booleanParam(name: 'es_embedded', defaultValue: true, description: 'Embedded ElasticSearch or AWS OpenSearch'),
        booleanParam(name: 's3_embedded', defaultValue: true, description: 'Embedded Minio or AWS S3'),
        booleanParam(name: 'pgadmin4', defaultValue: true, description: 'Deploy pgadmin4' )
    ])
])

String tfWorkDir = 'terraform/rancher/project'
String tfVars = ''

def saved_to_s3_install_json
def saved_to_s3_okapi_install_json
def tenant_id = params.restore_postgresql_from_backup ? params.tenant_id_to_restore_modules_versions : params.tenant_id

String frontendUrl = "https://${params.rancher_cluster_name}-${params.rancher_project_name}.${Constants.CI_ROOT_DOMAIN}"
String okapiUrl = "https://${params.rancher_cluster_name}-${params.rancher_project_name}-okapi.${Constants.CI_ROOT_DOMAIN}"

String hash = common.getLastCommitHash("platform-${params.folio_repository}", params.folio_branch)
String tag = params.build_ui ? "${params.rancher_cluster_name}-${params.rancher_project_name}-${params.tenant_id}-${hash.take(7)}" : params.frontend_image_tag

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        try {
            stage('Ini') {
                buildName params.rancher_cluster_name + '.' + params.rancher_project_name + '.' + env.BUILD_ID
                buildDescription "action: ${params.action}\n" +
                    "repository: ${params.folio_repository}\n" +
                    "branch: ${params.folio_branch}\n" +
                    "tenant: ${params.tenant_id}\n" +
                    "env_config: ${params.env_config}"
            }
            stage('Checkout') {
                checkout scm
            }
            stage('Build UI') {
                if (params.build_ui && params.action == 'apply') {
                    build job: 'Rancher/UI-Build',
                        parameters: [
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'rancher_cluster_name', value: params.rancher_cluster_name),
                            string(name: 'rancher_project_name', value: params.rancher_project_name),
                            string(name: 'tenant_id', value: params.tenant_id),
                            string(name: 'custom_hash', value: hash),
                            string(name: 'custom_url', value: okapiUrl),
                            string(name: 'custom_tag', value: tag)
                        ]
                }
            }
            stage('TF vars') {
                tfVars += terraform.generateTfVar('repository', "platform-${params.folio_repository}")
                tfVars += terraform.generateTfVar('branch', params.folio_branch)
                tfVars += terraform.generateTfVar('okapi_version', params.okapi_version)
                tfVars += terraform.generateTfVar('rancher_cluster_name', params.rancher_cluster_name)
                tfVars += terraform.generateTfVar('rancher_project_name', params.rancher_project_name)
                tfVars += terraform.generateTfVar('frontend_image_tag', tag)
                tfVars += terraform.generateTfVar('tenant_id', params.tenant_id)
                tfVars += terraform.generateTfVar('env_config', params.env_config)
                tfVars += terraform.generateTfVar('pg_password', params.pg_password)
                tfVars += terraform.generateTfVar('pgadmin_password', params.pgadmin_password)
                tfVars += terraform.generateTfVar('pg_embedded', params.pg_embedded)
                tfVars += terraform.generateTfVar('kafka_embedded', params.kafka_embedded)
                tfVars += terraform.generateTfVar('es_embedded', params.es_embedded)
                tfVars += terraform.generateTfVar('s3_embedded', params.s3_embedded)
                tfVars += terraform.generateTfVar('pgadmin4', params.pgadmin4)
                tfVars += terraform.generateTfVar('restore_from_saved_s3_install_json', params.restore_postgresql_from_backup)
                tfVars += terraform.generateTfVar('path_of_postgresql_backup', params.restore_postgresql_backup_name)
                tfVars += terraform.generateTfVar('github_team_ids', new Tools(this).getGitHubTeamsIds([] + Constants.ENVS_MEMBERS_LIST[params.rancher_project_name] + params.github_teams - null).collect { '"' + it + '"' })
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
                              credentialsId    : Constants.AWS_S3_POSTGRES_BACKUPS,
                              accessKeyVariable: 'TF_VAR_s3_postgres_backups_access_key',
                              secretKeyVariable: 'TF_VAR_s3_postgres_backups_secret_key'],
                             string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key'),
                             usernamePassword(credentialsId: 'folio-docker-dev',
                                 passwordVariable: 'TF_VAR_folio_docker_registry_password',
                                 usernameVariable: 'TF_VAR_folio_docker_registry_username')]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, "${params.rancher_cluster_name}-${params.rancher_project_name}")
                    terraform.tfStatePull(tfWorkDir)
                    if (params.action == 'apply') {
                        if (params.restore_postgresql_from_backup == true) {
                            if (!params.restore_postgresql_backup_name?.trim()) {
                                throw new Exception("\n\n\n" + "\033[1;31m" + "You've tried to restore DB state from backup but didn't provide path/name of it.\nPlease, provide correct DB backup path/name and try again.\n\n\n" + "\033[0m")
                            }
                            terraform.tfPostgreSQLPlan(tfWorkDir, tfVars)
                            terraform.tfPostgreSQLApply(tfWorkDir)
                            stage('Restore DB') {
                                build job: 'Rancher/Create-Restore-PosgreSQL-DB-backup',
                                    parameters: [
                                        string(name: 'rancher_cluster_name', value: params.rancher_cluster_name),
                                        string(name: 'rancher_project_name', value: params.rancher_project_name),
                                        string(name: 'tenant_id_to_backup_modules_versions', value: params.tenant_id_to_restore_modules_versions),
                                        booleanParam(name: 'restore_postgresql_from_backup', value: params.restore_postgresql_from_backup),
                                        string(name: 'restore_postgresql_backup_name', value: params.restore_postgresql_backup_name)
                                    ]
                            }
                        }
                        terraform.tfPlan(tfWorkDir, tfVars)
                        terraform.tfApply(tfWorkDir)
                        saved_to_s3_install_json = terraform.tfOutput(tfWorkDir, "saved_to_s3_install_json")
                        saved_to_s3_okapi_install_json = terraform.tfOutput(tfWorkDir, "saved_to_s3_okapi_install_json")
                        /**Wait for dns flush...*/
                        sleep time: 5, unit: 'MINUTES'
                        /**Check for dns */
                        def health = okapiUrl + '/_/version'
                        timeout(60) {
                            waitUntil(initialRecurrencePeriod: 20000, quiet: true) {
                                try {
                                    httpRequest ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', timeout: 1000, url: health, validResponseCodes: '200,403'
                                    return true
                                } catch (exception) {
                                    println(exception.getMessage())
                                    return false
                                }
                            }
                        }
                    } else if (params.action == 'destroy') {
                        terraform.tfDestroy(tfWorkDir, tfVars)
                    }
                }
            }
            if (params.enable_modules && (params.action == 'apply' || params.action == 'nothing')) {
                stage('Okapi deployment') {
                    OkapiTenant tenant = okapiSettings.tenant(
                        tenantId: tenant_id,
                        tenantName: params.tenant_name,
                        tenantDescription: params.tenant_description,
                        loadReference: params.load_reference,
                        loadSample: params.load_sample
                    )
                    OkapiUser admin_user = okapiSettings.adminUser()
                    Email email = okapiSettings.email()
                    withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
                        Deployment deployment = new Deployment(
                            this,
                            okapiUrl,
                            frontendUrl,
                            "platform-${params.folio_repository}",
                            params.folio_branch,
                            tenant,
                            admin_user,
                            email,
                            cypress_api_key_apidvcorp,
                            params.reindex_elastic_search,
                            params.recreate_index_elastic_search,
                            saved_to_s3_install_json,
                            saved_to_s3_okapi_install_json
                        )
                        if (params.restore_postgresql_from_backup == true) {
                            deployment.restoreFromBackup()
                        }
                        else {
                            deployment.main()
                        }
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
