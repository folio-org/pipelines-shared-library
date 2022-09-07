#!groovy
@Library('pipelines-shared-library@RANCHER-433') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiUser
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.Tools

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
        booleanParam(name: 'ui_build', defaultValue: true, description: 'Build UI image for frontend if false choose from dropdown next'),
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
        booleanParam(name: 'pgadmin4', defaultValue: true, description: 'Deploy pgadmin4')])])

String tf_working_dir = 'terraform/rancher/project'
String tf_variables = ''

def tenant_id = params.restore_postgresql_from_backup ? params.restore_tenant_id : params.tenant_id
boolean reindex = params.restore_postgresql_from_backup ? 'true' : params.reindex_elastic_search
boolean recreate_index = params.restore_postgresql_from_backup ? 'true' : params.recreate_index_elastic_search

List install_json = params.restore_postgresql_from_backup ? psqlDumpMethods.getInstallJsonBody(params.restore_postgresql_backup_name) : new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch)
Map install_map = new GitHubUtility(this).getModulesVersionsMap(install_json)
String okapi_version = params.restore_postgresql_from_backup ? install_map.find{ it.key == "okapi" }?.value : params.okapi_version

String okapi_domain = common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)
String ui_domain = common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant_id, Constants.CI_ROOT_DOMAIN)
String edge_domain = common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)
String ui_url = "https://" + ui_domain
String okapi_url = "https://" + okapi_domain

String hash = common.getLastCommitHash(params.folio_repository, params.folio_branch)
String tag = params.ui_build ? "${params.rancher_cluster_name}-${params.rancher_project_name}-${tenant_id}-${hash.take(7)}" : params.frontend_image_tag
String final_tag = params.restore_postgresql_from_backup ? psqlDumpMethods.getPlatformCompleteImageTag(params.restore_postgresql_backup_name).trim() : tag

def modules_config = ''

OkapiTenant tenant = okapiSettings.tenant(tenantId: tenant_id,
    tenantName: params.tenant_name,
    tenantDescription: params.tenant_description,
    loadReference: params.load_reference,
    loadSample: params.load_sample)
OkapiUser admin_user = okapiSettings.adminUser()
Email email = okapiSettings.email()

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node('jenkins-agent-java11') {
        try {
            stage('Ini') {
                buildName params.rancher_cluster_name + '.' + params.rancher_project_name + '.' + env.BUILD_ID
                buildDescription "action: ${params.action}\n" +
                    "repository: ${params.folio_repository}\n" +
                    "branch: ${params.folio_branch}\n" +
                    "tenant: ${tenant_id}\n" +
                    "env_config: ${params.env_config}"
            }

            stage('Checkout') {
                checkout scm
                modules_config = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${params.env_config}.yaml"
            }

            stage('UI Build') {
                //TODO review condition
                if (params.ui_build && params.action == 'apply' && !params.restore_postgresql_from_backup) {
                    build job: 'Rancher/UI-Build-261',
                        parameters: [string(name: 'folio_repository', value: params.folio_repository),
                                     string(name: 'folio_branch', value: params.folio_branch),
                                     string(name: 'rancher_cluster_name', value: params.rancher_cluster_name),
                                     string(name: 'rancher_project_name', value: params.rancher_project_name),
                                     string(name: 'tenant_id', value: tenant_id),
                                     string(name: 'custom_hash', value: hash),
                                     string(name: 'custom_url', value: okapi_url),
                                     string(name: 'custom_tag', value: tag)]
                }
            }

            stage('TF vars') {
                tf_variables += terraform.generateTfVar('rancher_cluster_name', params.rancher_cluster_name)
                tf_variables += terraform.generateTfVar('rancher_project_name', params.rancher_project_name)
                tf_variables += terraform.generateTfVar('tenant_id', tenant_id)
                tf_variables += terraform.generateTfVar('pg_password', params.pg_password)
                tf_variables += terraform.generateTfVar('pgadmin_password', params.pgadmin_password)
                tf_variables += terraform.generateTfVar('pg_embedded', params.pg_embedded)
                tf_variables += terraform.generateTfVar('kafka_embedded', params.kafka_embedded)
                tf_variables += terraform.generateTfVar('es_embedded', params.es_embedded)
                tf_variables += terraform.generateTfVar('s3_embedded', params.s3_embedded)
                tf_variables += terraform.generateTfVar('pgadmin4', params.pgadmin4)
                tf_variables += terraform.generateTfVar('github_team_ids', new Tools(this).getGitHubTeamsIds([] + Constants.ENVS_MEMBERS_LIST[params.rancher_project_name] + params.github_teams - null).collect { '"' + it + '"' })
            }

            stage('Project') {
                folioDeploy.project {
                    action = this.params.action
                    working_dir = tf_working_dir
                    cluster_name = this.params.rancher_cluster_name
                    project_name = this.params.rancher_project_name
                    tf_vars = tf_variables
                    restore_backup = this.params.restore_postgresql_from_backup
                    backup_name = this.params.restore_postgresql_backup_name
                    target_tenant_id = tenant_id
                }

            }

            if (params.action == 'apply') {

                stage("Deploy okapi") {
                    folioDeploy.okapi(modules_config, okapi_version, params.rancher_cluster_name, params.rancher_project_name, okapi_domain)
                }

                stage("Deploy backend modules") {
                    Map install_backend_map = new GitHubUtility(this).getBackendModulesMap(install_map)
                    if(install_backend_map) {
                        folioDeploy.backend(install_backend_map, modules_config, params.rancher_cluster_name, params.rancher_project_name)
                    }
                }

                stage("Pause") {
                    // Wait for dns flush.
                    sleep time: 5, unit: 'MINUTES'
                }

                stage("Health check") {
                    // Checking the health of the Okapi service.
                    common.healthCheck("${okapi_url}/_/version")
                }

                stage("Enable backend modules") {
                    if (params.enable_modules && (params.action == 'apply' || params.action == 'nothing')) {
                        withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
                            Deployment deployment = new Deployment(
                                this,
                                okapi_url,
                                ui_url,
                                install_json,
                                install_map,
                                tenant,
                                admin_user,
                                email,
                                cypress_api_key_apidvcorp,
                                reindex,
                                recreate_index
                            )
                            if (params.restore_postgresql_from_backup) {
                                deployment.update()
                            } else {
                                deployment.main()
                            }
                        }
                    }
                }

                stage("Deploy edge modules") {
                    Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(install_map)
                    if(install_edge_map) {
                        writeFile file: "ephemeral.properties", text: new Edge(this, okapi_url).renderEphemeralProperties(install_edge_map, tenant, admin_user)
                        helm.k8sClient {
                            helm.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                            helm.createSecret("ephemeral-properties", params.rancher_project_name, "./ephemeral.properties")
                        }
                        new Edge(this, okapi_url).createEdgeUsers(tenant, install_edge_map)
                        folioDeploy.edge(install_edge_map, modules_config, params.rancher_cluster_name, params.rancher_project_name, edge_domain)
                    }
                }

                stage("Deploy UI bundle") {
                    folioDeploy.uiBundle(tenant_id, modules_config, final_tag, params.rancher_cluster_name, params.rancher_project_name, ui_domain)
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
