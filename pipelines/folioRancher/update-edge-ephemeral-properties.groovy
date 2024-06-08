#!groovy

@Library('pipelines-shared-library') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Okapi
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.utilities.Tools

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.edgeModule(),
        jobsParameters.referenceTenantId(),
        jobsParameters.tenantId(),
        jobsParameters.tenantName(''),
        jobsParameters.adminUsername(''),
        jobsParameters.adminPassword('', 'Please, necessarily provide password for admin user'),
        booleanParam(name: 'create_tenant', defaultValue: false, description: 'Do you need to create tenant?')])
])

OkapiUser superadmin_user = okapiSettings.superadmin_user()
Okapi okapi = new Okapi(this, "https://${common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)}", superadmin_user)

List installed_modules = okapi.getInstalledModules(params.reference_tenant_id).collect { [id: it.id, action: "enable"] }

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    name: params.tenant_name,
    description: "${params.tenant_name} tenant for ${params.edge_module}",
    tenantParameters: [loadReference: true,
                       loadSample   : true],
    queryParameters: [reinstall: 'false'],
    index: [reindex : true,
            recreate: params.recreate_elastic_search_index])

Project project_config = new Project(clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    enableModules: params.enable_modules,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    installJson: installed_modules,
    configType: params.config_type)

Email email = okapiSettings.email()
Tools tools = new Tools(this)

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node('rancher') {
        try {
            if (params.create_tenant) {
                stage("Create tenant") {
                    def file_path = tools.copyResourceFileToWorkspace('edge/config.yaml')
                    def config = steps.readYaml file: file_path

                    retry(2) {
                        OkapiUser edge_user = okapiSettings.edgeUser(username: params.admin_username,
                            password: params.admin_password, permissions: config[(params.edge_module)].permissions + "perms.all")
                        withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
                            tenant.kb_api_key = cypress_api_key_apidvcorp
                            Deployment deployment = new Deployment(
                                this,
                                "https://${project_config.getDomains().okapi}",
                                "https://${project_config.getDomains().ui}",
                                project_config.getInstallJson(),
                                project_config.getInstallMap(),
                                tenant,
                                edge_user,
                                superadmin_user,
                                email
                            )
                            deployment.createTenant()
                        }
                    }
                    println("Tenant ${params.tenant_name} for ${params.edge_module} was created successfully")
                }
            }
            stage("Recreate ephemeral-properties") {
                String configMapName = "${params.edge_module}-ephemeral-properties"
                String contentOfNewConfigMap = ""
                boolean existsTenant

                folioHelm.withK8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
                    // Get data from existing ConfigMap
                    def existingConfigMap = kubectl.getConfigMap(configMapName, params.rancher_project_name, configMapName)

                    existingConfigMap.readLines().each {
                        if(it.split("=").size() == 2) {
                            def keyValue = it.split("=")
                            if (keyValue[0] == "tenants" && !keyValue[1].contains(params.tenant_name)) {
                                keyValue[1] += ",${params.tenant_name}"
                            } else if (keyValue[0] == params.tenant_name && keyValue[1].contains(params.admin_username)) {
                                existsTenant = true
                            }
                            contentOfNewConfigMap += "${keyValue[0]}=${keyValue[1]}\n"
                        } else {
                            contentOfNewConfigMap += "$it\n"
                        }
                    }

                    if (!existsTenant) {
                        contentOfNewConfigMap += "${params.tenant_name}=${params.admin_username},${params.admin_password}\n"
                    }
                    // Recreate existing ConfigMap with a new values
                    writeFile file: configMapName, text: contentOfNewConfigMap
                    kubectl.recreateConfigMap(configMapName, project_config.getProjectName(), "./${configMapName}")
                }
            }
            stage("Rollout Deployment") {
                folioHelm.withK8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
                    kubectl.rolloutDeployment(params.edge_module, project_config.getProjectName())
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
