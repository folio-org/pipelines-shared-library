import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-768-adapt-for-kube') _

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.uiBundleTag(),
        jobsParameters.uiBundleBuild(),
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.tenantId()
    ])
])

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id)

Project project_config = new Project(
    hash: params.ui_bundle_build ? common.getLastCommitHash(params.folio_repository, params.folio_branch) : '',
    clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    configType: 'development',
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
)

project_config.uiBundleTag = params.ui_bundle_build ? "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${project_config.getHash().take(7)}" : params.ui_bundle_tag

ansiColor("xterm") {
    common.refreshBuidParameters(params.refresh_parameters)
    podTemplate(inheritFrom: 'rancher-kube', containers: [
        containerTemplate(name: 'k8sclient', image: Constants.DOCKER_K8S_CLIENT_IMAGE, command: "sleep", args: "99999999")]
    ) {
        node(POD_LABEL) {
            try {
                stage("Checkout") {
                    checkout scm
                }
                stage("Read configs") {
                    project_config.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_config.getConfigType()}.yaml"
                }

                if (params.ui_bundle_build) {
                    stage("Build UI bundle") {
                        def jobParameters = [
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'rancher_cluster_name', value: project_config.getClusterName()),
                            string(name: 'rancher_project_name', value: project_config.getProjectName()),
                            string(name: 'tenant_id', value: tenant.getId()),
                            string(name: 'custom_hash', value: project_config.getHash()),
                            string(name: 'custom_tag', value: project_config.getUiBundleTag())
                        ]
                        build job: 'Rancher/UI-Build', parameters: jobParameters
                    }
                }

                stage("Deploy UI bundle") {
                    container('k8sclient') {
                        withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                                          credentialsId    : Constants.AWS_CREDENTIALS_ID,
                                          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            folioDeploy.uiBundle(tenant.getId(), project_config)
                        }
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
}

