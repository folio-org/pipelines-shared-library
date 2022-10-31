#!groovy
@Library('pipelines-shared-library') _

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
        booleanParam(name: 'kafka_ui', defaultValue: true, description: 'Deploy kafka-ui')])])

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

Project project_model = new Project(
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
    backupName: params.backup_name)

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
                buildName "${project_model.getClusterName()}.${project_model.getProjectName()}.${env.BUILD_ID}"
                buildDescription "action: ${project_model.getAction()}\n" + "tenant: ${tenant.getId()}\n" + "config_type: ${project_model.getConfigType()}"
                project_model.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_model.getConfigType()}.yaml"
                project_model.uiBundleTag = params.ui_bundle_build ? "${project_model.getClusterName()}-${project_model.getProjectName()}-${tenant.getId()}-${project_model.getHash().take(7)}" : params.ui_bundle_tag
            }

            stage('Restore preparation') {
                if (project_model.getRestoreFromBackup()) {
                    helm.k8sClient {
                        project_model.backupFilesPath = "${Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME}/${project_model.getBackupType()}/${project_model.getBackupName()}/${project_model.getBackupName()}"
                        project_model.installJson = new Tools(this).jsonParse(awscli.getS3FileContent("${project_model.getBackupFilesPath()}-install.json"))
                        project_model.uiBundleTag = awscli.getS3FileContent("${project_model.getBackupFilesPath()}-image-tag.txt")
                        tenant.okapiVersion = common.getOkapiVersion(project_model.getInstallJson())
                    }
                }
            }

            stage('UI Build') {
                if (params.ui_bundle_build && project_model.getAction() == 'apply' && !project_model.getRestoreFromBackup()) {
                    build job: 'Rancher/UI-Build',
                        parameters: [
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'rancher_cluster_name', value: project_model.getClusterName()),
                            string(name: 'rancher_project_name', value: project_model.getProjectName()),
                            string(name: 'tenant_id', value: tenant.getId()),
                            string(name: 'custom_hash', value: project_model.getHash()),
                            string(name: 'custom_url', value: "https://${project_model.getDomains().okapi}"),
                            string(name: 'custom_tag', value: project_model.getUiBundleTag())]
                }
            }

            stage('TF vars') {
                tf.variables += terraform.generateTfVar('rancher_cluster_name', project_model.getClusterName())
                tf.variables += terraform.generateTfVar('rancher_project_name', project_model.getProjectName())
                tf.variables += terraform.generateTfVar('tenant_id', tenant.getId())
                tf.variables += terraform.generateTfVar('pg_password', params.pg_password)
                tf.variables += terraform.generateTfVar('pgadmin_password', params.pgadmin_password)
                tf.variables += terraform.generateTfVar('pg_embedded', params.pg_embedded)
                tf.variables += terraform.generateTfVar('kafka_embedded', params.kafka_embedded)
                tf.variables += terraform.generateTfVar('es_embedded', params.es_embedded)
                tf.variables += terraform.generateTfVar('s3_embedded', params.s3_embedded)
                tf.variables += terraform.generateTfVar('pgadmin4', params.pgadmin4)
                tf.variables += terraform.generateTfVar('kafka_ui', params.kafka_ui)


                tf.variables += terraform.generateTfVar('github_team_ids', new Tools(this).getGitHubTeamsIds([] + Constants.ENVS_MEMBERS_LIST[params.rancher_project_name] + params.github_teams - null).collect { '"' + it + '"' })
                if (!params.pg_embedded && project_model.getRestoreFromBackup() && project_model.getBackupType() == 'rds') {
                    if (project_model.getBackupName()?.trim()) {
                        helm.k8sClient {
                            project_model.backupEngineVersion = awscli.getRdsClusterSnapshotEngineVersion("us-west-2", project_model.getBackupName())
                            project_model.backupMasterUsername = awscli.getRdsClusterSnapshotMasterUsername("us-west-2", project_model.getBackupName())
                        }
                        tf.variables += terraform.generateTfVar('pg_rds_snapshot_name', project_model.getBackupName())
                        tf.variables += terraform.generateTfVar('pg_version', project_model.getBackupEngineVersion())
                        tf.variables += terraform.generateTfVar('pg_dbname', project_model.getBackupMasterUsername())
                        tf.variables += terraform.generateTfVar('pg_username', project_model.getBackupMasterUsername())
                    } else {
                        new Logger(this, 'Project').error("backup_name parameter should not be empty if restore_from_backup parameter set to true")
                    }
                }
            }

            stage('Project') {
                folioDeploy.project(project_model, tenant, tf)
            }

            if (project_model.getAction() == 'apply') {
                stage("Generate install map") {
                    project_model.installMap = new GitHubUtility(this).getModulesVersionsMap(project_model.getInstallJson())
                }

                stage("Deploy okapi") {
                    folioDeploy.okapi(project_model.getModulesConfig(),
                        tenant.getOkapiVersion(),
                        project_model.getClusterName(),
                        project_model.getProjectName(),
                        project_model.getDomains().okapi)
                }

                stage("Deploy backend modules") {
                    Map install_backend_map = new GitHubUtility(this).getBackendModulesMap(project_model.getInstallMap())
                    if (install_backend_map) {
                        folioDeploy.backend(install_backend_map,
                            project_model.getModulesConfig(),
                            project_model.getClusterName(),
                            project_model.getProjectName())
                    }
                }

                stage("Pause") {
                    // Wait for dns flush.
                    sleep time: 5, unit: 'MINUTES'
                }

                stage("Health check") {
                    // Checking the health of the Okapi service.
                    common.healthCheck("https://${project_model.getDomains().okapi}/_/version")
                }

                stage("Enable backend modules") {
                    if (project_model.getEnableModules() && (project_model.getAction() == 'apply' || project_model.getAction() == 'nothing')) {
                        withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
                            tenant.kb_api_key = cypress_api_key_apidvcorp
                            Deployment deployment = new Deployment(
                                this,
                                "https://${project_model.getDomains().okapi}",
                                "https://${project_model.getDomains().ui}",
                                project_model.getInstallJson(),
                                project_model.getInstallMap(),
                                tenant,
                                admin_user,
                                superadmin_user,
                                email
                            )
                            if (project_model.getRestoreFromBackup()) {
                                deployment.cleanup()
                                deployment.update()
                            } else {
                                deployment.main()
                            }
                        }
                    }
                }

                stage("Deploy edge modules") {
                    Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(project_model.getInstallMap())
                    if (install_edge_map) {
                        writeFile file: "ephemeral.properties", text: new Edge(this, "https://${project_model.getDomains().okapi}").renderEphemeralProperties(install_edge_map, tenant, admin_user)
                        helm.k8sClient {
                            awscli.getKubeConfig(Constants.AWS_REGION, project_model.getClusterName())
                            helm.createSecret("ephemeral-properties", project_model.getProjectName(), "./ephemeral.properties")
                        }
                        new Edge(this, "https://${project_model.getDomains().okapi}").createEdgeUsers(tenant, install_edge_map)
                        folioDeploy.edge(install_edge_map,
                            project_model.getModulesConfig(),
                            project_model.getClusterName(),
                            project_model.getProjectName(),
                            project_model.getDomains().edge)
                    }
                }

                stage("Deploy UI bundle") {
                    folioDeploy.uiBundle(tenant.getId(),
                        project_model.getModulesConfig(),
                        project_model.getUiBundleTag(),
                        project_model.getClusterName(),
                        project_model.getProjectName(),
                        project_model.getDomains().ui)
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
