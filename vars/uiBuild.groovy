#!groovy
import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.model.Module
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library') _

def call(params) {
    OkapiTenant tenant = new OkapiTenant(id: params.tenant_id)
    Project project_config = new Project(
        clusterName: params.rancher_cluster_name,
        projectName: params.rancher_project_name,
        domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
                okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
                edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)]
    )
    Module ui_bundle = new Module(
        name: "ui-bundle",
        hash: params.custom_hash?.trim() ? params.custom_hash : common.getLastCommitHash(params.folio_repository, params.folio_branch)
    )
    String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
    ui_bundle.tag = params.custom_tag?.trim() ? params.custom_tag : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
    ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

    //TODO Temporary solution should be revised during refactoring
    stage('Checkout') {
        checkout([$class           : 'GitSCM',
                  branches         : [[name: ui_bundle.hash]],
                  extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                                      [$class: 'RelativeTargetDirectory', relativeTargetDir: 'platform-complete']],
                  userRemoteConfigs: [[url: 'https://github.com/folio-org/platform-complete.git']]])
        if(params.consortia) {
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

//TODO temporary solution should be revised
private String getModuleId(String moduleName) {
    URLConnection registry = new URL("https://folio-registry.dev.folio.org/_/proxy/modules?filter=${moduleName}&preRelease=only&latest=1").openConnection()
    if (registry.getResponseCode().equals(200)) {
        return new JsonSlurperClassic().parseText(registry.getInputStream().getText())*.id.first()
    } else {
        throw new RuntimeException("Unable to get ${moduleName} version. Url: ${registry.getURL()}. Status code: ${registry.getResponseCode()}.")
    }
}
