#!groovy
import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.Okapi
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.json.JsonSlurperClassic


@Library('pipelines-shared-library@DEPRECATED-master') _

import org.folio.utilities.Tools
import org.folio.utilities.model.Project

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.configType(),
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.uiBundleBuild(),
        jobsParameters.enableModules(),
        jobsParameters.tenantId(),
        jobsParameters.adminUsername(),
        jobsParameters.adminPassword(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.reinstall(),
        jobsParameters.reindexElasticsearch(),
        jobsParameters.recreateIndexElasticsearch(),
        booleanParam(name: 'enable_rw_split', defaultValue: false, description: '(Optional) Enable Read/Write split'),
        jobsParameters.agents()])])

OkapiUser superadmin_user = okapiSettings.superadmin_user()
Okapi okapi = new Okapi(this, "https://${common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)}", superadmin_user)
List installed_modules = okapi.getInstalledModules(params.tenant_id).collect { [id: it.id, action: "enable"] }
Map installed_modules_map = new GitHubUtility(this).getModulesVersionsMap(installed_modules)

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: params.reinstall],
    index: [reindex : params.reindex_elastic_search,
            recreate: params.recreate_elastic_search_index])

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
    password: params.admin_password)

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
    installJson: new GitHubUtility(this).getEnableList(params.folio_repository, params.folio_branch),
    configType: params.config_type,
    tenant: tenant)

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            stage('Init') {
                buildName "${project_config.getClusterName()}.${project_config.getProjectName()}.${env.BUILD_ID}"
                buildDescription "tenant: ${tenant.getId()}\n" +
                    "config_type: ${project_config.getConfigType()}"
                project_config.uiBundleTag = "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${project_config.getHash().take(7)}"
            }

            stage('Checkout') {
                checkout scm
                tenant.okapiVersion = common.getOkapiVersion(project_config.getInstallJson())
                if(!tenant.getOkapiVersion()?.trim()) {
                    String repository = params.folio_branch.contains("snapshot") ? "folioci" : "folioorg"
                    def dockerHub = new URL('https://hub.docker.com/v2/repositories/' + repository + '/okapi/tags?page_size=100&ordering=last_updated').openConnection()
                    if (dockerHub.getResponseCode().equals(200)) {
                        tenant.okapiVersion = (new JsonSlurperClassic().parseText(dockerHub.getInputStream().getText()).results*.name - 'latest')[0]
                    }
                }
                project_config.installMap = new GitHubUtility(this).getModulesVersionsMap(project_config.getInstallJson())
                project_config.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_config.getConfigType()}.yaml"
            }

            stage('UI Build') {
                if (params.ui_bundle_build) {
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

            if(tenant.getOkapiVersion()?.trim()) {
                stage("Deploy okapi") {
                    folioDeploy.okapi(project_config)
                }
            }

            stage("Deploy backend modules") {
                Map github_backend_map = new GitHubUtility(this).getBackendModulesMap(project_config.getInstallMap())
                Map backend_installed_modules_map = new GitHubUtility(this).getBackendModulesMap(installed_modules_map)
                Map update_modules = compare.createActionMaps(backend_installed_modules_map, github_backend_map)

                if (update_modules.updateMap) {
                    folioDeploy.backend(update_modules.updateMap,
                        project_config,
                        false,
                        params.enable_rw_split)
                }
            }

            stage("Pause") {
                // Wait for dns flush.
                sleep time: 3, unit: 'MINUTES'
            }

            stage("Health check") {
                // Checking the health of the Okapi service.
                common.healthCheck("https://${project_config.getDomains().okapi}/_/version", tenant)
            }

            stage("Enable backend modules") {
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
                        email
                    )
                    try {
                        deployment.update()
                    } catch (Exception e) {
                        if (e.getMessage().contains("504 Gateway Time-out")) {
                            println("Deployment update failed with a 504 Gateway Time-out. Check modules status with GET request..")
                            okapi.checkInstalledModules(params.tenant_id, 5)
                        } else {
                            throw e
                        }
                    }
                }
            }

            stage("Deploy edge modules") {
                Map github_edge_map = new GitHubUtility(this).getEdgeModulesMap(project_config.getInstallMap())
                Map edge_installed_modules_map = new GitHubUtility(this).getEdgeModulesMap(installed_modules_map)
                Map update_modules = compare.createActionMaps(edge_installed_modules_map, github_edge_map)

                if (update_modules.updateMap) {
                    new Edge(this, "https://${project_config.getDomains().okapi}").renderEphemeralProperties(update_modules.updateMap, tenant, admin_user)
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
                        update_modules.updateMap.each {name, version ->
                            kubectl.createConfigMap("${name}-ephemeral-properties", project_config.getProjectName(), "./${name}-ephemeral-properties")
                        }
                    }
                    new Edge(this, "https://${project_config.getDomains().okapi}").createEdgeUsers(tenant, update_modules.updateMap)
                    folioDeploy.edge(update_modules.updateMap,
                        project_config)
                }
            }

            stage("Deploy UI bundle") {
                if (params.ui_bundle_build) {
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
