#!groovy
import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
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
        text(defaultValue: '''[ {
    "id" : "folio_users-x.x.x",
    "action" : "enable"
}, {
    "id" : "mod-users-x.x.x",
    "action" : "enable"
} ]''', description: '(Required) Install json list with modules to update.', name: 'install_json'),
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

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: params.reinstall],
    index: [reindex : params.reindex_elastic_search,
            recreate: params.recreate_elastic_search_index])

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
    password: params.admin_password)

OkapiUser superadmin_user = okapiSettings.superadmin_user()

Email email = okapiSettings.email()

Project project_config = new Project(clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    action: params.action,
    enableModules: params.enable_modules,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    installJson: new Tools(this).jsonParse(params.install_json),
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
            stage('Ini') {
                buildName "${project_config.getClusterName()}.${project_config.getProjectName()}.${env.BUILD_ID}"
                buildDescription "tenant: ${tenant.getId()}\n" +
                    "config_type: ${project_config.getConfigType()}"
            }

            stage('Checkout') {
                checkout scm
                tenant.okapiVersion = common.getOkapiVersion(project_config.getInstallJson())
                project_config.installMap = new GitHubUtility(this).getModulesVersionsMap(project_config.getInstallJson())
                project_config.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_config.getConfigType()}.yaml"
            }

            if(tenant.getOkapiVersion()?.trim()) {
                stage("Deploy okapi") {
                    folioDeploy.okapi(project_config)
                }
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
                    deployment.update()
                }
            }

            stage("Deploy edge modules") {
                Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(project_config.getInstallMap())
                if (install_edge_map) {
                    new Edge(this, "https://${project_config.getDomains().okapi}").renderEphemeralProperties(install_edge_map, tenant, admin_user)
                    folioHelm.withK8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
                        install_edge_map.each {name, version ->
                            kubectl.createConfigMap("${name}-ephemeral-properties", project_config.getProjectName(), "./${name}-ephemeral-properties")
                        }
                    }
                    new Edge(this, "https://${project_config.getDomains().okapi}").createEdgeUsers(tenant, install_edge_map)
                    folioDeploy.edge(install_edge_map,
                        project_config)
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
