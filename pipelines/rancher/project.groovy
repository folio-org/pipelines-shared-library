#!groovy
@Library('pipelines-shared-library@RANCHER-576') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiUser
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.Logger
import org.folio.utilities.Tools
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.refreshParameters(),
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.okapiVersion(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.uiBundleBuild(),
        jobsParameters.uiBundleTag(),
        jobsParameters.configType(),
        jobsParameters.enableModules(),
        jobsParameters.agents(),
        jobsParameters.tenantId(),
        jobsParameters.tenantName(),
        jobsParameters.tenantDescription(),
        jobsParameters.reindexElasticsearch(),
        jobsParameters.recreateIndexElasticsearch(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.reinstall(),
        jobsParameters.adminUsername(),
        jobsParameters.adminPassword(),
        jobsParameters.pgPassword(),
        jobsParameters.pgAdminPassword(),
        string(name: 'github_teams', defaultValue: '', description: 'Coma separated list of GitHub teams who need access to project'),
        jobsParameters.restoreFromBackup(),
        jobsParameters.backupType(),
        jobsParameters.backupName(),
        booleanParam(name: 'pg_embedded', defaultValue: true, description: 'Embedded PostgreSQL or AWS RDS'),
        booleanParam(name: 'kafka_embedded', defaultValue: true, description: 'Embedded Kafka or AWS MSK'),
        booleanParam(name: 'es_embedded', defaultValue: true, description: 'Embedded ElasticSearch or AWS OpenSearch'),
        booleanParam(name: 's3_embedded', defaultValue: true, description: 'Embedded Minio or AWS S3'),
        booleanParam(name: 'pgadmin4', defaultValue: true, description: 'Deploy pgadmin4'),
        booleanParam(name: 'kafka_ui', defaultValue: true, description: 'Deploy kafka-ui'),
        booleanParam(name: 'opensearch_dashboards', defaultValue: true, description: 'Deploy opensearch-dashboards')])])

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    name: params.tenant_name,
    description: params.tenant_description,
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: params.reinstall],
    okapiVersion: params.okapi_version,
    index: [reindex : params.restore_from_backup ? 'true' : params.reindex_elastic_search,
            recreate: params.restore_from_backup ? 'true' : params.recreate_elastic_search_index])

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
    password: params.admin_password)

OkapiUser superadmin_user = okapiSettings.superadmin_user()

Email email = okapiSettings.email()

Project project_config = new Project(
    hash: common.getLastCommitHash(params.folio_repository, params.folio_branch),
    clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    action: params.action,
    enableModules: params.enable_modules,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    installJson: params.restore_from_backup ? [] : new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch),
    configType: params.config_type,
    restoreFromBackup: params.restore_from_backup,
    backupType: params.backup_type,
    backupName: params.backup_name,
    tenant: tenant)

Map tf = [working_dir: 'terraform/rancher/project',
          variables  : '']

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            stage('Checkout') {
                checkout scm
            }

            stage('Ini') {
                buildName "${project_config.getClusterName()}.${project_config.getProjectName()}.${env.BUILD_ID}"
                buildDescription "action: ${project_config.getAction()}\n" + "tenant: ${tenant.getId()}\n" + "config_type: ${project_config.getConfigType()}"
                project_config.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_config.getConfigType()}.yaml"
                project_config.uiBundleTag = params.ui_bundle_build ? "${project_config.getClusterName()}-${project_config.getProjectName()}-${tenant.getId()}-${project_config.getHash().take(7)}" : params.ui_bundle_tag
            }

            stage('Restore preparation') {
                if (project_config.getRestoreFromBackup()) {
                    helm.k8sClient {
                        project_config.backupFilesPath = "${Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME}/${project_config.getBackupType()}/${project_config.getBackupName()}/${project_config.getBackupName()}"
                        project_config.installJson = new Tools(this).jsonParse(awscli.getS3FileContent("${project_config.getBackupFilesPath()}-install.json"))
                        project_config.uiBundleTag = awscli.getS3FileContent("${project_config.getBackupFilesPath()}-image-tag.txt")
                        tenant.okapiVersion = common.getOkapiVersion(project_config.getInstallJson())
                    }
                }
            }

            stage('UI Build') {
                if (params.ui_bundle_build && project_config.getAction() == 'apply' && !project_config.getRestoreFromBackup()) {
                    build job: 'Rancher/UI-Build',
                        parameters: [
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'rancher_cluster_name', value: project_config.getClusterName()),
                            string(name: 'rancher_project_name', value: project_config.getProjectName()),
                            string(name: 'tenant_id', value: tenant.getId()),
                            string(name: 'custom_hash', value: project_config.getHash()),
                            string(name: 'custom_url', value: "https://${project_config.getDomains().okapi}"),
                            string(name: 'custom_tag', value: project_config.getUiBundleTag())]
                }
            }

            stage('TF vars') {
                tf.variables += terraform.generateTfVar('rancher_cluster_name', project_config.getClusterName())
                tf.variables += terraform.generateTfVar('rancher_project_name', project_config.getProjectName())
                tf.variables += terraform.generateTfVar('tenant_id', tenant.getId())
                tf.variables += terraform.generateTfVar('pg_password', params.pg_password)
                tf.variables += terraform.generateTfVar('pgadmin_password', params.pgadmin_password)
                tf.variables += terraform.generateTfVar('pg_embedded', params.pg_embedded)
                tf.variables += terraform.generateTfVar('kafka_embedded', params.kafka_embedded)
                tf.variables += terraform.generateTfVar('es_embedded', params.es_embedded)
                tf.variables += terraform.generateTfVar('s3_embedded', params.s3_embedded)
                tf.variables += terraform.generateTfVar('pgadmin4', params.pgadmin4)
                tf.variables += terraform.generateTfVar('kafka_ui', params.kafka_ui)
                tf.variables += terraform.generateTfVar('opensearch_dashboards', params.opensearch_dashboards)


                tf.variables += terraform.generateTfVar('github_team_ids', new Tools(this).getGitHubTeamsIds([] + Constants.ENVS_MEMBERS_LIST[params.rancher_project_name] + params.github_teams - null).collect { '"' + it + '"' })
                if (!params.pg_embedded && project_config.getRestoreFromBackup() && project_config.getBackupType() == 'rds') {
                    if (project_config.getBackupName()?.trim()) {
                        helm.k8sClient {
                            project_config.backupEngineVersion = awscli.getRdsClusterSnapshotEngineVersion("us-west-2", project_config.getBackupName())
                            project_config.backupMasterUsername = awscli.getRdsClusterSnapshotMasterUsername("us-west-2", project_config.getBackupName())
                        }
                        tf.variables += terraform.generateTfVar('pg_rds_snapshot_name', project_config.getBackupName())
                        tf.variables += terraform.generateTfVar('pg_version', project_config.getBackupEngineVersion())
                        tf.variables += terraform.generateTfVar('pg_dbname', project_config.getBackupMasterUsername())
                        tf.variables += terraform.generateTfVar('pg_username', project_config.getBackupMasterUsername())
                    } else {
                        new Logger(this, 'Project').error("backup_name parameter should not be empty if restore_from_backup parameter set to true")
                    }
                }
            }

            stage('Project') {
                folioDeploy.project(project_config, tenant, tf)
            }

            if (project_config.getAction() == 'apply') {
                stage("Generate install map") {
                    project_config.installMap = new GitHubUtility(this).getModulesVersionsMap(project_config.getInstallJson())
                }

                stage("Deploy okapi") {
                    folioDeploy.okapi(project_config)
                }

                stage("Deploy backend modules") {
                    Map install_backend_map = new GitHubUtility(this).getBackendModulesMap(project_config.getInstallMap())
                    if (install_backend_map) {
                        folioDeploy.backend(install_backend_map, project_config)
                    }
                }

                stage("Pause") {
                    // Wait for dns flush.
                    sleep time: 5, unit: 'MINUTES'
                }

                stage("Health check") {
                    // Checking the health of the Okapi service.
                    common.healthCheck("https://${project_config.getDomains().okapi}/_/version")
                }

//                stage("Enable backend modules") {
//                    if (project_config.getEnableModules() && (project_config.getAction() == 'apply' || project_config.getAction() == 'nothing')) {
//                        withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
//                            tenant.kb_api_key = cypress_api_key_apidvcorp
//                            Deployment deployment = new Deployment(
//                                this,
//                                "https://${project_config.getDomains().okapi}",
//                                "https://${project_config.getDomains().ui}",
//                                project_config.getInstallJson(),
//                                project_config.getInstallMap(),
//                                tenant,
//                                admin_user,
//                                superadmin_user,
//                                email
//                            )
//                            if (project_config.getRestoreFromBackup()) {
//                                deployment.cleanup()
//                                deployment.update()
//                            } else {
//                                deployment.main()
//                            }
//                        }
//                    }
//                }

//                stage("Deploy edge modules") {
//                    Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(project_config.getInstallMap())
//                    if (install_edge_map) {
//                        writeFile file: "ephemeral.properties", text: new Edge(this, "https://${project_config.getDomains().okapi}").renderEphemeralProperties(install_edge_map, tenant, admin_user)
//                        helm.k8sClient {
//                            awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
//                            helm.createSecret("ephemeral-properties", project_config.getProjectName(), "./ephemeral.properties")
//                        }
//                        new Edge(this, "https://${project_config.getDomains().okapi}").createEdgeUsers(tenant, install_edge_map)
//                        folioDeploy.edge(install_edge_map, project_config)
//                    }
//                }

//                stage("Deploy UI bundle") {
//                    folioDeploy.uiBundle(tenant.getId(), project_config)
//                }
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
