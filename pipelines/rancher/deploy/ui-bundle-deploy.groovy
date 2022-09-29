import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.model.Project

@Library('pipelines-shared-library') _

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

Project project_model = new Project(
    hash: params.ui_bundle_build ? common.getLastCommitHash(params.folio_repository, params.folio_branch) : '',
    clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    configType: 'development',
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
)

project_model.uiBundleTag = params.ui_bundle_build ? "${project_model.getClusterName()}-${project_model.getProjectName()}-${tenant.getId()}-${project_model.getHash().take(7)}" : params.ui_bundle_tag

ansiColor("xterm") {
    common.refreshBuidParameters(params.refresh_parameters)
    node("jenkins-agent-java11") {
        try {
            stage("Checkout") {
                checkout scm
            }
            stage("Read configs") {
                project_model.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_model.getConfigType()}.yaml"
            }

            if (params.ui_bundle_build) {
                stage("Build UI bundle") {
                    build job: 'Rancher/UI-Build',
                        parameters: [
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'rancher_cluster_name', value: project_model.getClusterName()),
                            string(name: 'rancher_project_name', value: project_model.getProjectName()),
                            string(name: 'tenant_id', value: tenant.getId()),
                            string(name: 'custom_hash', value: project_model.getHash()),
                            string(name: 'custom_tag', value: project_model.getUiBundleTag())]
                }
            }

            stage("Deploy UI bundle") {
                folioDeploy.uiBundle(tenant.getId(),
                    project_model.getModulesConfig(),
                    project_model.getUiBundleTag(),
                    project_model.getClusterName(),
                    project_model.getProjectName(),
                    project_model.getDomains().ui)
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

