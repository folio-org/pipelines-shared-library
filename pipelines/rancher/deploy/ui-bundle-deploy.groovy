import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@DEPRECATED-master') _

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
        jobsParameters.tenantId(),
        booleanParam(name: 'consortia_enabled', defaultValue: false, description: '(Optional) Include consortia module in UI bundle'),
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
    node("rancher") {
        try {

            stage('Ini') {
                buildName "${project_config.getClusterName()}.${project_config.getProjectName()}.${env.BUILD_ID}"
                buildDescription "tenant: ${tenant.getId()}\n"
            }

            stage("Checkout") {
                checkout scm
            }
            stage("Read configs") {
                project_config.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_config.getConfigType()}.yaml"
            }

            if (params.ui_bundle_build) {
                stage("Build UI bundle") {
                    def jobParameters = [
                        folio_repository    : params.folio_repository,
                        folio_branch        : params.folio_branch,
                        rancher_cluster_name: project_config.getClusterName(),
                        rancher_project_name: project_config.getProjectName(),
                        tenant_id           : tenant.getId(),
                        custom_hash         : project_config.getHash(),
                        custom_tag          : project_config.getUiBundleTag(),
                        consortia           : params.consortia_enabled
                    ]
                    uiBuild(jobParameters)
                }
            }

            stage("Deploy UI bundle") {
                folioDeploy.uiBundle(tenant.getId(),
                    project_config)
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

