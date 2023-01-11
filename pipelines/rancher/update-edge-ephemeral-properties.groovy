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
        jobsParameters.edgeModule(),
        jobsParameters.tenantName(''),
        jobsParameters.adminUsername(''),
        jobsParameters.adminPassword('', 'Please, necessarily provide password for admin user'),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        booleanParam(name: 'createTenant', defaultValue: true, description: 'Do you need to create tenant?'),
        booleanParam(name: 'deploy_ui', defaultValue: true, description: 'Do you need to provide UI access to the new tenant?'),
        jobsParameters.repository(),
        jobsParameters.branch()])
])

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            if (params.createTenant) {
                stage("Create tenant") {
                        build job: 'Rancher/Update/create-tenant',
                            parameters: [
                                string(name: 'rancher_cluster_name', value: params.rancher_cluster_name),
                                string(name: 'rancher_project_name', value: params.rancher_project_name),
                                string(name: 'install_list', value: params.edge_module),
                                string(name: 'tenant_id', value: params.tenant_name),
                                string(name: 'tenant_name', value: params.tenant_name),
                                string(name: 'tenant_description', value: "${params.tenant_name} tenant for ${params.edge_module}"),
                                string(name: 'admin_username', value: params.admin_username),
                                string(name: 'admin_password', value: params.admin_password),
                                booleanParam(name: 'load_reference', value: params.load_reference),
                                booleanParam(name: 'load_sample', value: params.load_sample),
                                string(name: 'folio_repository', value: params.folio_repository),
                                string(name: 'folio_branch', value: params.folio_branch),
                                string(name: 'deploy_ui', value: params.deploy_ui.toString())]
                }
                println("Tenant ${params.edge_module} was created successfully")
            }
            stage("Create tenant") {

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


