#!groovy

@Library('pipelines-shared-library@RANCHER-472') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.GitHubUtility
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
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.okapiVersion(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.configType(),
        jobsParameters.tenantId(),
        jobsParameters.tenantName(),
        jobsParameters.tenantDescription(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.reinstall(),
        jobsParameters.adminUsername(),
        jobsParameters.adminPassword(),
        jobsParameters.restoreFromBackup(),])
])

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


