#!groovy
import org.folio.Constants

@Library('pipelines-shared-library') _


import org.folio.rest.Okapi
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.jenkinsci.plugins.workflow.libs.Library

properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  disableConcurrentBuilds(),
  parameters([
    jobsParameters.refreshParameters(),
    jobsParameters.clusterName(),
    jobsParameters.projectName(),
    jobsParameters.tenantId('')])
])

OkapiUser superadmin_user = okapiSettings.superadmin_user()
Okapi okapi = new Okapi(this, "https://${common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN)}", superadmin_user)

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
  name: params.tenant_name)

ansiColor('xterm') {
  if (params.refresh_parameters) {
    currentBuild.result = 'ABORTED'
    println('REFRESH PARAMETERS!')
    return
  }
  node('rancher') {
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


