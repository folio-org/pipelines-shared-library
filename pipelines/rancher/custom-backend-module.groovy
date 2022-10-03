#!groovy
@Library('pipelines-shared-library@RANCHER-296-bugfix') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Module
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        jobsParameters.agents(),
        jobsParameters.backendModule(),
        jobsParameters.branch('backend_module'),
        jobsParameters.mvnOptions('-DskipTests'),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.configType(),
        jobsParameters.enableModules(),
        jobsParameters.tenantId(),
        jobsParameters.adminUsername(),
        jobsParameters.adminPassword(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.reinstall(),
        jobsParameters.reindexElasticsearch(),
        jobsParameters.recreateIndexElasticsearch()])])

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: params.reinstall],
    index: [reindex : params.reindex_elastic_search,
            recreate: params.recreate_elastic_search_index])

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
    password: params.admin_password)

Email email = okapiSettings.email()

Project project_model = new Project(clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    enableModules: params.enable_modules,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    configType: params.config_type)

Module backend_module = new Module(
    name: params.backend_module,
    mvnOptions: params.mvn_options.trim()
)

String DESCRIPTOR_PATH = "target/ModuleDescriptor.json"

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH JOB PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            stage('Ini') {
                buildName "${backend_module.getName()}.${env.BUILD_ID}"
                buildDescription "branch: ${params.folio_branch}\n" +
                    "env: ${project_model.getClusterName()}-${project_model.getProjectName()}\n" +
                    "tenant: ${tenant.getId()}\n" +
                    "config_type: ${project_model.getConfigType()}"
            }

            stage('Checkout') {
                checkout scm
                project_model.modulesConfig = readYaml file: "${Constants.HELM_MODULES_CONFIG_PATH}/${project_model.getConfigType()}.yaml"
            }

            stage('Checkout module') {
                sh "git clone --single-branch --recurse-submodules --branch ${params.folio_branch}  https://github.com/folio-org/${backend_module.getName()}.git ${backend_module.getName()}"
                backend_module.hash = common.getLastCommitHash(backend_module.getName(), params.folio_branch).take(7)
            }

            stage('Maven build') {
                dir(backend_module.getName()) {
                    backend_module.version = readMavenPom().getVersion()
                    backend_module.tag = "${backend_module.getVersion()}.${backend_module.getHash()}"
                    backend_module.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${backend_module.getName()}:${backend_module.getTag()}"
                    withMaven(jdk: "${common.selectJavaBasedOnAgent(params.agent)}",
                        maven: Constants.MAVEN_TOOL_NAME,
                        options: [artifactsPublisher(disabled: true)]) {
                        sh """
                            mvn versions:set -DnewVersion=${backend_module.getTag()}
                            mvn package ${backend_module.getMvnOptions()}
                        """
                    }
                }
            }

            stage('Docker build and push') {
                common.checkEcrRepoExistence(backend_module.getName())
                docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
                    def image
                    dir(backend_module.getName() == 'okapi' ? "${backend_module.getName()}/okapi-core" : backend_module.getName()) {
                        image = docker.build("${backend_module.getImageName()}", '--no-cache=true --pull=true .')
                        image.push()
                    }
                }
            }

            stage('Deploy preparation') {
                project_model.installJson = [
                    [
                        id    : "${backend_module.getName()}-${backend_module.getTag()}",
                        action: 'enable'
                    ]
                ]
                project_model.installMap = new GitHubUtility(this).getModulesVersionsMap(project_model.getInstallJson())
                dir(backend_module.getName()) {
                    if (fileExists(DESCRIPTOR_PATH)) {
                        backend_module.descriptor = [readJSON(file: DESCRIPTOR_PATH)]
                    }
                }
            }

            stage("Deploy backend modules") {
                Map install_backend_map = new GitHubUtility(this).getBackendModulesMap(project_model.getInstallMap())
                if (install_backend_map) {
                    folioDeploy.backend(install_backend_map,
                        project_model.getModulesConfig(),
                        project_model.getClusterName(),
                        project_model.getProjectName(),
                        true)
                }
            }

            stage("Health check") {
                // Checking the health of the Okapi service.
                common.healthCheck("https://${project_model.getDomains().okapi}/_/version")
            }

            stage("Enable backend modules") {
                withCredentials([string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'cypress_api_key_apidvcorp'),]) {
                    tenant.kb_api_key = cypress_api_key_apidvcorp
                    Deployment deployment = new Deployment(
                        this,
                        "https://${project_model.getDomains().okapi}",
                        "https://${project_model.getDomains().ui}",
                        project_model.getInstallJson(),
                        project_model.getInstallMap(),
                        tenant,
                        admin_user,
                        email
                    )
                    deployment.installSingleBackendModule(backend_module.getDescriptor())
                }
            }
        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                common.removeImage(backend_module.getImageName())
                cleanWs notFailBuild: true
            }
        }
    }
}
