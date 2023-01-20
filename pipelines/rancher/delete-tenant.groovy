#!groovy

@Library('pipelines-shared-library@RANCHER-332-edge') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Okapi
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library


properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        text(name: 'install_list', defaultValue: '', description: '(Optional) If you would like to install custom list of modules - provide it to the field below \nFor example: mod-search, mod-data-export, mod-organizations \nIf you would like to install all modules - ingnore the option'),
        jobsParameters.referenceTenantId(),
        jobsParameters.tenantId(''),
        jobsParameters.tenantName(''),
        jobsParameters.tenantDescription('')])
])

OkapiUser superadmin_user = okapiSettings.superadmin_user()
Okapi okapi = new Okapi(this, "https://${common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)}", superadmin_user)
List installed_modules = okapi.getInstalledModules(params.reference_tenant_id).collect { [id: it.id, action: "enable"] }
List modules_to_install = []
String core_modules = "mod-permissions, mod-users, mod-users-bl, mod-authtoken"

if (params.install_list && !params.refresh_parameters){
    core_modules = core_modules + "," + params.install_list.toString()
    println(core_modules)
    core_modules.split(',').each {module->
        modules_to_install.add(okapi.getModuleIdFromInstallJson(installed_modules, module.toString().trim()))}
    modules_to_install = okapi.buildInstallList(modules_to_install, 'enable')
} else {
    modules_to_install = installed_modules
}

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

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            stage("Delete tenant") {
                okapi.deleteTenant(tenant)
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


