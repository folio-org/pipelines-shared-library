#!groovy
import org.folio.Constants

@Library('pipelines-shared-library') _

import org.folio.rest.Deployment
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([

        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.tenantId(),
        jobsParameters.adminUsername(),
        jobsParameters.adminPassword(),

    ])])

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

OkapiUser superadmin_user = okapiSettings.superadmin_user()

Email email = okapiSettings.email()

Project project_config = new Project(
    clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    installJson: [],
    tenant: tenant)

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node('rancher') {
        try {
            stage("Update user permissions") {
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
                deployment.updatePermissionsAll()
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





