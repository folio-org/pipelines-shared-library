#!groovy
import org.folio.Constants
import org.folio.rest.model.OkapiTenant
//import org.folio.rest_v2.Common
import org.folio.utilities.model.Module
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-835') _

void build(params) {
  OkapiTenant tenant = new OkapiTenant(id: params.tenant_id)
  Project project_config = new Project(
    clusterName: params.CLUSTER,
    projectName: params.NAMESPACE,
    domains: [ui   : common.generateDomain(params.cluster, params.NAMESPACE, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.cluster, params.NAMESPACE, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.cluster, params.NAMESPACE, 'edge', Constants.CI_ROOT_DOMAIN)]
  )
  Module ui_bundle = new Module(
    name: "ui-bundle",
    hash: params.CUSTOM_HASH?.trim() ? params.CUSTOM_HASH : common.getLastCommitHash(params.FOLIO_REPOSITORY, params.FOLIO_BRANCH)
  )
  String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
  ui_bundle.tag = params.uiBundleTag?.trim() ? params.uiBundleTag : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
  ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

  stage('Checkout') {
    dir('platform-complete') {
      cleanWs()
    }
    checkout([$class           : 'GitSCM',
              branches         : [[name: ui_bundle.hash]],
              extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                                  [$class: 'RelativeTargetDirectory', relativeTargetDir: 'platform-complete']],
              userRemoteConfigs: [[url: 'https://github.com/folio-org/platform-complete.git']]])
    if(params.CONSORTIA) {
      dir('platform-complete') {
        def packageJson = readJSON file: 'package.json'
        String moduleId = getModuleId('folio_consortia-settings')
        String moduleVersion = moduleId - 'folio_consortia-settings-'
        packageJson.dependencies.put('@folio/consortia-settings', moduleVersion)
        writeJSON file: 'package.json', json: packageJson, pretty: 2
        sh 'sed -i "/modules: {/a \\    \'@folio/consortia-settings\' : {}," stripes.config.js'
      }
    }
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

void deploy(params) {
  OkapiTenant tenant = new OkapiTenant(id: params.tenantId)
  Project project_config = new Project(
    clusterName: params.cluster,
    projectName: params.namespace,
    configType: 'development',
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)]
  )
  Module ui_bundle = new Module(
    name: "ui-bundle",
    hash: params.custom_hash?.trim() ? params.custom_hash : common.getLastCommitHash(params.repository, params.branch)
  )
  String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
  ui_bundle.tag = params.custom_tag?.trim() ? params.custom_tag : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
  ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

  stage("Deploy UI bundle") {
    folioDeploy.uiBundle(tenant, project_config)
  }
}


