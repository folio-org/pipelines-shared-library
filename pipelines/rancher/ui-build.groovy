#!groovy
import org.folio.Constants

@Library('pipelines-shared-library') _

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.rancherClusters(),
        jobsParameters.projectName(),
        jobsParameters.tenantId(),
        string(name: 'custom_hash', defaultValue: '', description: 'Commit hash for bundle build from specific commit'),
        string(name: 'custom_url', defaultValue: '', description: 'Custom url for bundle build'),
        string(name: 'custom_tag', defaultValue: '', description: 'Custom tag for UI image')
    ])
])

String imageName = Constants.DOCKER_DEV_REPOSITORY + '/platform-complete' //TODO rename to folio-ui
String okapiUrl = params.custom_url.isEmpty() ? "https://${params.rancher_cluster_name}-${params.project_name}-okapi.${Constants.CI_ROOT_DOMAIN}" : params.custom_url //TODO add tenant id to URL
String hash = params.custom_hash.isEmpty() ? common.getLastCommitHash("platform-${params.folio_repository}", params.folio_branch) : params.custom_hash
String tag = params.custom_tag.isEmpty() ? "${params.rancher_cluster_name}-${params.project_name}-${params.tenant_id}-${hash.take(7)}" : params.custom_tag

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        stage('Build and Push') {
            buildName tag + '.' + env.BUILD_ID
            buildDescription "repository: ${params.folio_repository}\n" +
                "branch: ${params.folio_branch}\n" +
                "hash: ${hash}"
            docker.withRegistry('https://' + Constants.DOCKER_DEV_REPOSITORY, Constants.DOCKER_DEV_REPOSITORY_CREDENTIALS_ID) {
                def image = docker.build(
                    imageName,
                    "--build-arg OKAPI_URL=${okapiUrl} " +
                        "--build-arg TENANT_ID=${params.tenant_id} " +
                        "-f docker/Dockerfile  " +
                        "https://github.com/folio-org/platform-complete.git#${hash}"
                )
                image.push(tag)
            }
        }
    }
}
