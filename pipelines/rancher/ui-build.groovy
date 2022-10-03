#!groovy
import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.model.Module
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-296-bugfix') _

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.tenantId(),
        string(name: 'custom_hash', defaultValue: '', description: 'Commit hash for bundle build from specific commit'),
        string(name: 'custom_url', defaultValue: '', description: 'Custom url for okapi'),
        string(name: 'custom_tag', defaultValue: '', description: 'Custom tag for UI bundle image')
    ])
])

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id)

Project project_model = new Project(
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

ui_bundle.tag = params.custom_tag?.trim() ? params.custom_tag : "${project_model.getClusterName()}-${project_model.getProjectName()}-${tenant.getId()}-${ui_bundle.getHash().take(7)}"
ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_model.getDomains().okapi

ansiColor('xterm') {
    common.refreshBuidParameters(params.refresh_parameters)
    node("jenkins-agent-java11") {
        try {
            stage('Build and Push') {
                buildName ui_bundle.getTag() + '.' + env.BUILD_ID
                buildDescription "repository: ${params.folio_repository}\n" +
                    "branch: ${params.folio_branch}\n" +
                    "hash: ${ui_bundle.getHash()}"
                if (common.checkEcrImageExistence(Constants.AWS_REGION, "ui-bundle", ui_bundle.getTag())) {
                    docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
                        def image = docker.build(
                            ui_bundle.getImageName(),
                            "--build-arg OKAPI_URL=${okapi_url} " +
                                "--build-arg TENANT_ID=${tenant.getId()} " +
                                "-f docker/Dockerfile  " +
                                "https://github.com/folio-org/platform-complete.git#${ui_bundle.getHash()}"
                        )
                        image.push()
                    }
                }
                else {
                    println("\033[32m" + "Image with tag ${ui_bundle.getTag()} already exist in ECR \
                            ${Constants.AWS_REGION} 'ui-bundle' repo, there is no need to build it." + "\033[0m")
                }
            }
        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                common.removeImage(ui_bundle.getImageName())
                cleanWs notFailBuild: true
            }
        }
    }
}
