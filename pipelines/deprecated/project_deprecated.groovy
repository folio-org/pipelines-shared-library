package deprecated

import org.folio.Constants

/*
!!!DEPRECATED!!!
Please avoid any significant logic changes. Only fixes allowed.
Instead use new approach for pipelines creation/modification
 */
#!groovy
@Library('pipelines-shared-library') _


import org.folio.rest.Deployment
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.LdpConfig
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.Logger
import org.folio.utilities.Tools
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  disableConcurrentBuilds(),
  parameters([
    choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
    jobsParameters.clusterName(),
    jobsParameters.projectName(),
    booleanParam(name: 'namespace_only', defaultValue: false, description: '(Optional) Deploy only namespace and default resources'),
    jobsParameters.repository(),
    jobsParameters.branch(),
    jobsParameters.okapiVersion(),
    jobsParameters.uiBundleBuild(),
    jobsParameters.uiBundleTag(),
    jobsParameters.enableModules(),
    jobsParameters.configType(),
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
    string(name: 'github_teams', defaultValue: '', description: '(Optional) Coma separated list of GitHub teams who need access to project'),
    jobsParameters.restoreFromBackup(),
    jobsParameters.backupType(),
    jobsParameters.backupName(),
    booleanParam(name: 'pg_embedded', defaultValue: true, description: '(Optional) Embedded PostgreSQL or AWS RDS'),
    booleanParam(name: 'kafka_shared', defaultValue: false, description: '(Optional) Use shared AWS MSK service or embedded Kafka'),
    booleanParam(name: 'opensearch_shared', defaultValue: true, description: '(Optional) Use shared AWS OpenSearch service or embedded OpenSearch'),
    booleanParam(name: 's3_embedded', defaultValue: true, description: '(Optional) Use embedded Minio or AWS S3 service'),
    booleanParam(name: 'pgadmin4', defaultValue: true, description: '(Optional) Deploy pgAdmin4 service'),
    booleanParam(name: 'greenmail_server', defaultValue: false, description: '(Optional) Deploy greenmail server'),
    booleanParam(name: 'enable_rw_split', defaultValue: false, description: '(Optional) Enable Read/Write split'),
    jobsParameters.agents(),
    jobsParameters.refreshParameters()])])

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
  name: params.tenant_name,
  description: params.tenant_description,
  tenantParameters: [loadReference: params.load_reference,
                     loadSample   : params.load_sample],
  queryParameters: [reinstall: params.reinstall],
  okapiVersion: params.okapi_version,
  index: [reindex : params.reindex_elastic_search,
          recreate: params.recreate_elastic_search_index])

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
  password: params.admin_password)

OkapiUser superadmin_user = okapiSettings.superadmin_user()

Email email = okapiSettings.email()

LdpConfig ldpConfig = ldpSettings.ldpConfig(ldp_db_user_password: "${jobsParameters.pgLdpUserDefaultPassword()}", ldp_queries_gh_token: ldpSettings.get_ldp_queries_gh_token())

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

Map context = [
  tf_work_dir: 'terraform/rancher/project',
  tf_vars    : ''
]
context.putAll(params)

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
        project_config.uiBundleTag = params.ui_bundle_build ? "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${project_config.getHash().take(7)}" : params.ui_bundle_tag
      }

      stage('Restore preparation') {
        if (project_config.getRestoreFromBackup()) {
          folioHelm.withK8sClient {
            project_config.backupFilesPath = "${Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME}/${project_config.getBackupType()}/${project_config.getBackupName()}/${project_config.getBackupName()}"
            project_config.installJson = new Tools(this).jsonParse(awscli.getS3FileContent("${project_config.getBackupFilesPath()}-install.json"))
            project_config.uiBundleTag = awscli.getS3FileContent("${project_config.getBackupFilesPath()}-image-tag.txt")
            tenant.okapiVersion = common.getOkapiVersion(project_config.getInstallJson())
          }
        }
      }

      stage('UI Build') {
        if (params.ui_bundle_build && project_config.getAction() == 'apply' && !project_config.getRestoreFromBackup() && !params.namespace_only) {
          def jobParameters = [
            folio_repository    : params.folio_repository,
            folio_branch        : params.folio_branch,
            rancher_cluster_name: project_config.getClusterName(),
            rancher_project_name: project_config.getProjectName(),
            tenant_id           : tenant.getId(),
            custom_hash         : project_config.getHash(),
            custom_url          : "https://${project_config.getDomains().okapi}",
            custom_tag          : project_config.getUiBundleTag()
          ]
          uiBuild(jobParameters)
        }
      }

      stage('TF vars') {
        Map tf_vars_map = [
          rancher_cluster_name: project_config.getClusterName(),
          rancher_project_name: project_config.getProjectName(),
          tenant_id           : tenant.getId(),
          pg_password         : params.pg_password,
          pgadmin_password    : params.pgadmin_password,
          pg_embedded         : params.pg_embedded,
          enable_rw_split     : params.enable_rw_split,
          kafka_shared        : params.kafka_shared,
          opensearch_shared   : params.opensearch_shared,
          s3_embedded         : params.s3_embedded,
          pgadmin4            : params.pgadmin4,
          pg_ldp_user_password: "${jobsParameters.pgLdpUserDefaultPassword()}",
          github_team_ids     : new Tools(this).getGitHubTeamsIds([] + Constants.ENVS_MEMBERS_LIST[params.rancher_project_name] + params.github_teams - null).collect { '"' + it + '"' },
        ]

        if (!params.pg_embedded && project_config.getRestoreFromBackup() && project_config.getBackupType() == 'rds') {
          if (project_config.getBackupName()?.trim()) {
            folioHelm.withK8sClient {
              project_config.backupEngineVersion = awscli.getRdsClusterSnapshotEngineVersion("us-west-2", project_config.getBackupName())
              project_config.backupMasterUsername = awscli.getRdsClusterSnapshotMasterUsername("us-west-2", project_config.getBackupName())
            }
            tf_vars_map.put("pg_rds_snapshot_name", project_config.getBackupName())
            tf_vars_map.put("pg_version", project_config.getBackupEngineVersion())
            //It works only with bugfests snapshots.
            tf_vars_map.put("pg_dbname", Constants.BUGFEST_SNAPSHOT_DBNAME)
            tf_vars_map.put("pg_username", project_config.getBackupMasterUsername())
          } else {
            new Logger(this, 'Project').error("backup_name parameter should not be empty if restore_from_backup parameter set to true")
          }
        }

        context.tf_vars = folioTerraform.generateTfVars(tf_vars_map)
      }

      stage('Project') {
        folioDeploy.project(project_config, tenant, context.tf_work_dir, context.tf_vars)
      }

      if (project_config.getAction() == 'apply' && !params.namespace_only) {
        stage("Generate install map") {
          project_config.installMap = new GitHubUtility(this).getModulesVersionsMap(project_config.getInstallJson())
          if (project_config.installMap?.okapi?.contains('SNAPSHOT')) {
            tenant.okapiVersion = common.getOkapiLatestSnapshotVersion(project_config.installMap.okapi)
          }
        }

        stage("Deploy okapi") {
          folioDeploy.okapi(project_config)
        }

        stage("Deploy backend modules") {
          Map install_backend_map = new GitHubUtility(this).getBackendModulesMap(project_config.getInstallMap())
          if (install_backend_map) {
            folioDeploy.backend(install_backend_map,
              project_config,
              false,
              params.enable_rw_split)
          }
        }

        if (params.greenmail_server) {
          stage("Deploy greenmail") {
            folioDeploy.greenmail(project_config)
          }
        }

        stage("Pause") {
          // Wait for dns flush.
          sleep time: 10, unit: 'MINUTES'
        }

//                stage("Health check") {
//                    // Checking the health of the Okapi service.
//                    common.healthCheck("https://${project_config.getDomains().okapi}/_/version", tenant)
//                }

        stage("Enable backend modules") {
          if (project_config.getEnableModules() && (project_config.getAction() == 'apply' || project_config.getAction() == 'nothing')) {
            withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
              tenant.kb_api_key = cypress_api_key_apidvcorp
              Deployment deployment = new Deployment(
                this,
                "https://${project_config.getDomains().okapi}",
                "https://${project_config.getDomains().ui}",
                project_config.getInstallJson(),
                project_config.getInstallMap(),
                tenant,
                admin_user,
                superadmin_user,
                email,
                project_config.getRestoreFromBackup()
              )
              if (project_config.getRestoreFromBackup()) {
                deployment.cleanup()
                deployment.update()
              } else {
                deployment.main()
                if (params.rancher_project_name == "data-migration") {
                  deployment.unsecure()
                }
              }
            }
          }
        }

        stage("Deploy edge modules") {
          Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(project_config.getInstallMap())
          if (install_edge_map) {
            new Edge(this, "https://${project_config.getDomains().okapi}").renderEphemeralProperties(install_edge_map, tenant, admin_user)
            folioHelm.withK8sClient {
              awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
              install_edge_map.each { name, version ->
                kubectl.createConfigMap("${name}-ephemeral-properties", project_config.getProjectName(), "./${name}-ephemeral-properties")
              }
            }
            new Edge(this, "https://${project_config.getDomains().okapi}").createEdgeUsers(tenant, install_edge_map)
            folioDeploy.edge(install_edge_map, project_config)
          }
        }

        if (!project_config.getRestoreFromBackup()) {
          stage("Post deploy stage") {
            folioDeploy.ldp_server(tenant, project_config, admin_user, superadmin_user, ldpConfig,
              "postgresql-${project_config.getProjectName()}", params.pg_password)
          }
        }

        stage("Deploy UI bundle") {
          folioDeploy.uiBundle(tenant.getId(), project_config)
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
