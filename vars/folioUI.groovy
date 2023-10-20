#!groovy
import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.rest_v2.Common
import org.folio.utilities.model.Module
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-835') _

void build(params) {
  OkapiTenant tenant = new OkapiTenant(id: params.tenant_id)
  Project project_config = new Project(
    clusterName: params.cluster,
    projectName: params.namespace,
    domains: [ui   : common.generateDomain(params.CLUSTER, params.NAMESPACE, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.CLUSTER, params.NAMESPACE, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.CLUSTER, params.NAMESPACE, 'edge', Constants.CI_ROOT_DOMAIN)]
  )
  Module ui_bundle = new Module(
    name: "ui-bundle",
    hash: params.custom_hash?.trim() ? params.custom_hash : common.getLastCommitHash(params.folio_repository, params.folio_branch)
  )
  String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
  ui_bundle.tag = params.custom_tag?.trim() ? params.custom_tag : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
  ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"
  Common common = new Common(this, 'diku')
  common.logger.warning(okapi_url)
  input "Testing staff..."

  stage('Checkout') {
    dir('platform-complete') {
      cleanWs()
    }
    checkout([$class           : 'GitSCM',
              branches         : [[name: ui_bundle.hash]],
              extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                                  [$class: 'RelativeTargetDirectory', relativeTargetDir: 'platform-complete']],
              userRemoteConfigs: [[url: 'https://github.com/folio-org/platform-complete.git']]])
  }
  stage('Build and Push') {
    dir('platform-complete') {
      docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
        def image = docker.build(
          ui_bundle.getImageName(),
          "--build-arg OKAPI_URL=${okapi_url} " +
            "--build-arg TENANT_ID=${tenant.getId()} " +
            "-f docker/Dockerfile  " +
            "."
        )
        image.push()
      }
    }
  }
  stage('Cleanup') {
    common.removeImage(ui_bundle.getImageName())
  }
}

//void deploy(params) {
//  OkapiTenant tenant = new OkapiTenant(id: params.tenantId)
//  Project project_config = new Project(
//    clusterName: params.cluster,
//    projectName: params.namespace,
//    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
//              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
//              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)]
//  )
//  Module ui_bundle = new Module(
//    name: "ui-bundle",
//    hash: params.custom_hash?.trim() ? params.custom_hash : common.getLastCommitHash(params.repository, params.branch)
//  )
//  String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
//  ui_bundle.tag = params.custom_tag?.trim() ? params.custom_tag : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
//  ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

//  stage("Deploy UI bundle") {
//    folioDeploy.uiBundle(tenant, project_config)
//  }
}


