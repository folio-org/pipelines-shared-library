#!groovy
import groovy.json.JsonSlurperClassic
import org.folio.Constants
//import org.folio.rest.model.OkapiTenant
//import org.folio.utilities.model.Module
//import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-835') _

void build(params) {
//  OkapiTenant tenant = new OkapiTenant(id: params.TENANT_ID)
//  Project project_config = new Project(
//    clusterName: params.CLUSTER,
//    projectName: params.NAMESPACE,
//    domains: [ui   : common.generateDomain(params.CLUSTER, params.NAMESPACE, tenant.getId(), Constants.CI_ROOT_DOMAIN),
//              okapi: common.generateDomain(params.CLUSTER, params.NAMESPACE, 'okapi', Constants.CI_ROOT_DOMAIN),
//              edge : common.generateDomain(params.CLUSTER, params.NAMESPACE, 'edge', Constants.CI_ROOT_DOMAIN)]
//  )
//  Module ui_bundle = new Module(
//    name: "ui-bundle",
//    hash: params.CUSTOM_HASH?.trim() ? params.CUSTOM_HASH : common.getLastCommitHash(params.FOLIO_REPOSITORY, params.FOLIO_BRANCH)
//  )
//  String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
//  ui_bundle.tag = params.UI_BUNDLE_TAG?.trim() ? params.UI_BUNDLE_TAG : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
//  ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

  stage('Checkout') {
    dir('platform-complete') {
      cleanWs()
    }
    checkout([$class           : 'GitSCM',
              branches         : [[name: params.CUSTOM_HASH]],
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
          params.IMAGE_NAME,
          "--build-arg OKAPI_URL=${params.OKAPI_URL} " +
            "--build-arg TENANT_ID=${params.TENANT_ID} " +
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
//  OkapiTenant tenant = new OkapiTenant(id: params.TENANT_ID)
//  Project project_config = new Project(
//    hash: params.UI_BUNDLE_BUILD ? common.getLastCommitHash(params.FOLIO_REPOSITORY, params.FOLIO_BRANCH) : '',
//    clusterName: params.CLUSTER,
//    projectName: params.NAMESPACE,
//    configType: 'development',
//    domains: [ui   : common.generateDomain(params.CLUSTER, params.NAMESPACE, tenant.getId(), Constants.CI_ROOT_DOMAIN),
//              okapi: common.generateDomain(params.CLUSTER, params.NAMESPACE, 'okapi', Constants.CI_ROOT_DOMAIN),
//              edge : common.generateDomain(params.CLUSTER, params.NAMESPACE, 'edge', Constants.CI_ROOT_DOMAIN)]
//  )
//  project_config.uiBundleTag = params.UI_BUNDLE_BUILD ? "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${project_config.getHash().take(7)}" : params.UI_BUNDLE_TAG

  stage("Deploy UI bundle") {
    folioDeploy.uiBundle(tenant.getId(), project_config)
  }
}

static String getModuleId(String moduleName) {
  URLConnection registry = new URL("http://folio-registry.aws.indexdata.com/_/proxy/modules?filter=${moduleName}&preRelease=only&latest=1").openConnection()
  if (registry.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(registry.getInputStream().getText())*.id.first()
  } else {
    throw new RuntimeException("Unable to get ${moduleName} version. Url: ${registry.getURL()}. Status code: ${registry.getResponseCode()}.")
  }
}
