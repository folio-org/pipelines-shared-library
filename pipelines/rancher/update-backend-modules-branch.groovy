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


@Library('pipelines-shared-library') _

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
        jobsParameters.recreateIndexElasticsearch()])])

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
    node('jenkins-agent-java11') {
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
                project_config.installMap = new GitHubUtility(this).getModulesVersionsMap(project_config.getInstallJson())
                project_config.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_config.getConfigType()}.yaml"
            }

            stage('UI Build') {
                if (params.ui_bundle_build) {
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
                        project_config)
                }
            }

            stage("Pause") {
                // Wait for dns flush.
                sleep time: 3, unit: 'MINUTES'
            }

            stage("Health check") {
                // Checking the health of the Okapi service.
                common.healthCheck("https://${project_config.getDomains().okapi}/_/version")
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
                    deployment.update()
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
