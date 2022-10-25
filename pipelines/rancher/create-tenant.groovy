#!groovy

@Library('pipelines-shared-library@RANCHER-472') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Okapi
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library


properties([
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        text(name: 'install_list', defaultValue: '', description: '(Optional) If you would like to install custom list of modules - provide it to the field below \nFor example: mod-search, mod-data-export, mod-organizations \nIf you would like to install all modules - ingnore the option'),
        jobsParameters.referenceTenantId(),
        jobsParameters.tenantId(''),
        jobsParameters.tenantName(''),
        jobsParameters.tenantDescription(''),
        jobsParameters.adminUsername(''),
        jobsParameters.adminPassword('', 'Please, necessarily provide password for admin user'),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        booleanParam(name: 'deploy_ui', defaultValue: true, description: 'Do you need to provide UI access to the new tenant?'),
        jobsParameters.repository(),
        jobsParameters.branch()])
])

OkapiUser superuser = new OkapiUser(username: 'super_admin', password: 'admin')
Okapi okapi = new Okapi(this, "https://${common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)}", superuser)
List installed_modules = okapi.buildInstallListFromJson(okapi.getInstalledModules(params.reference_tenant_id), 'enable')
List modules_to_install = []
String core_modules = "mod-permissions, mod-users, mod-authtoken"

if (params.install_list && !params.refresh_parameters){
    println(params.install_list.toString())
    println(core_modules)
    core_modules = core_modules + params.install_list.toString()
    println(core_modules)
    core_modules.split(',').each {module->
        modules_to_install.add(okapi.getModuleIdFromInstallJson(installed_modules, module.toString().trim()))}
    modules_to_install = okapi.buildInstallList(modules_to_install, 'enable')
}
else {
    modules_to_install = installed_modules
}

println(modules_to_install)

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    name: params.tenant_name,
    description: params.tenant_description,
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: 'false'],
    okapiVersion: okapi.getModuleIdFromInstallJson(modules_to_install, okapi.OKAPI_NAME),
    index: [reindex : params.reindex_elastic_search,
            recreate: params.recreate_elastic_search_index])


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
    installJson: modules_to_install,
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
            /*if (params.deploy_ui) {
                stage("Build UI bundle") {
                        build job: 'Rancher/volodymyr-workflow/main/ui-bundle-deploy',
                            parameters: [
                                string(name: 'rancher_cluster_name', value: project_model.getClusterName()),
                                string(name: 'rancher_project_name', value: project_model.getProjectName()),
                                string(name: 'tenant_id', value: tenant.getId()),
                                string(name: 'folio_repository', value: params.folio_repository),
                                string(name: 'folio_branch', value: params.folio_branch),
                                string(name: 'ui_bundle_build', value: params.deploy_ui.toString())]
                }
            }*/
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


