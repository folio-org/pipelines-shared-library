#!groovy

@Library('pipelines-shared-library@RANCHER-472') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.GitHubUtility
import org.folio.rest.Okapi
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library


properties([
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        text(name: 'install_list', defaultValue: '', description: '(Optional) If you would like to install custom list of modules - provide it to the field below \nFor example: mod-search, mod-data-export, mod-organizations \nIf you would like to install all modules - ingnore the option'),
        jobsParameters.referenceTenantId(),
        jobsParameters.additionalTenantId(),
        jobsParameters.additionalTenantName(),
        jobsParameters.additionalTenantDescription(),
        jobsParameters.adminUsername(),
        jobsParameters.adminPassword(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample()])
])

OkapiUser superuser = new OkapiUser(username: 'super_admin', password: 'admin')
Okapi okapi = new Okapi(this, "https://${common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)}", superuser)
List installedModulesList = okapi.getInstalledModules(params.reference_tenant_id)
List installedModulesList2 = okapi.buildInstallListFromJson(installedModulesList, 'enable')


OkapiTenant tenant = new OkapiTenant(id: params.additional_tenant_id,
    name: params.additional_tenant_name,
    description: params.additional_tenant_description,
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: 'false'],
    okapiVersion: okapi.getModuleIdFromInstallJson(installedModulesList, okapi.OKAPI_NAME),
    index: [reindex : params.reindex_elastic_search,
            recreate: params.recreate_elastic_search_index],
    additional_tenant_id: params.additional_tenant_id,
    reference_tenant_id: params.reference_tenant_id,
    custom_modules_list: params.install_list)

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
    password: params.admin_password)

Email email = okapiSettings.email()

Project project_model = new Project(
    hash: '',
    clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    action: params.action,
    enableModules: params.enable_modules,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    installJson: installedModulesList2,
    configType: params.config_type,
    restoreFromBackup: params.restore_from_backup,
    backupType: params.backup_type,
    backupName: params.backup_name)

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            stage("Create tenant") {
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
                        email
                    )
                    deployment.createTenant()
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


