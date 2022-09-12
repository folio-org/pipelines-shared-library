#!groovy
import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

@Library('pipelines-shared-library') _

import org.folio.utilities.Tools

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        jobsParameters.rancherClusters(),
        jobsParameters.projectName(),
        jobsParameters.envType(),
        text(defaultValue: '''[ {
    "id" : "folio_users-x.x.x",
    "action" : "enable"
}, {
    "id" : "mod-users-x.x.x",
    "action" : "enable"
} ]''', description: '(Required) Install json list with modules to update.', name: 'install_json'),
        jobsParameters.enableModules(),
        jobsParameters.tenantId(),
        jobsParameters.reindexElasticsearch(),
        jobsParameters.recreateindexElasticsearch(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample()])])

List install_json = new Tools(this).jsonParse(params.install_json)
Map install_map = new GitHubUtility(this).getModulesVersionsMap(install_json)

String okapi_domain = common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)
String edge_domain = common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)
String okapi_url = "https://" + okapi_domain

def modules_config = ''

OkapiTenant tenant = okapiSettings.tenant(tenantId: params.tenant_id,
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
                println(params.install_json)
                println(install_json)

                buildName params.rancher_cluster_name + '.' + params.rancher_project_name + '.' + env.BUILD_ID
                buildDescription "tenant: ${params.tenant_id}\n" +
                    "env_config: ${params.env_config}"
            }

            stage('Checkout') {
                checkout scm
                modules_config = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${params.env_config}.yaml"
            }

            stage("Deploy backend modules") {
                Map install_backend_map = new GitHubUtility(this).getBackendModulesMap(install_map)
                if (install_backend_map) {
                    folioDeploy.backend(install_backend_map, modules_config, params.rancher_cluster_name, params.rancher_project_name)
                }
            }

            stage("Health check") {
                // Checking the health of the Okapi service.
                common.healthCheck("${okapi_url}/_/version")
            }

            stage("Enable backend modules") {
                withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
                    Deployment deployment = new Deployment(
                        this,
                        okapi_url,
                        '',
                        install_json,
                        install_map,
                        tenant,
                        admin_user,
                        email,
                        cypress_api_key_apidvcorp,
                        params.reindex_elastic_search,
                        params.recreate_index_elastic_search
                    )
                    deployment.update()
                }
            }

            stage("Deploy edge modules") {
                Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(install_map)
                if (install_edge_map) {
                    writeFile file: "ephemeral.properties", text: new Edge(this, okapi_url).renderEphemeralProperties(install_edge_map, tenant, admin_user)
                    helm.k8sClient {
                        helm.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        helm.createSecret("ephemeral-properties", params.rancher_project_name, "./ephemeral.properties")
                    }
                    new Edge(this, okapi_url).createEdgeUsers(tenant, install_edge_map)
                    folioDeploy.edge(install_edge_map, modules_config, params.rancher_cluster_name, params.rancher_project_name, edge_domain)
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
