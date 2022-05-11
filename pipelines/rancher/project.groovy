#!groovy
@Library('pipelines-shared-library') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.model.OkapiUser
import org.folio.rest.model.OkapiTenant

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        string(name: 'branch', defaultValue: 'master', description: 'Terraform scripts branch checkout'),
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.okapiVersion(),
        jobsParameters.rancherClusters(),
        jobsParameters.projectName(),
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.stripesImageTag(),
        jobsParameters.enableModules(),
        jobsParameters.tenantId(),
        jobsParameters.tenantName(),
        jobsParameters.tenantDescription(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.pgPassword(),
        jobsParameters.pgAdminPassword()
    ])
])

String okapiUrl = ''
String stripesUrl = ''
String tfWorkDir = 'rancher/terraform/rancher_project'
String tfVars = ''

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        try {
            stage('Ini') {
                buildName params.rancher_cluster_name + '.' + params.project_name + '.' + env.BUILD_ID
                buildDescription "action: ${params.action}\n" +
                    "repository: ${params.folio_repository}\n" +
                    "branch: ${params.folio_branch}\n" +
                    "tenant: ${params.tenant_id}"
            }
            stage('Checkout') {
                checkout scm: [
                    $class           : 'GitSCM',
                    branches         : [[name: params.branch]],
                    extensions       : [
                        [$class: 'CleanBeforeCheckout'],
                        [$class             : 'SubmoduleOption',
                         disableSubmodules  : false,
                         parentCredentials  : false,
                         recursiveSubmodules: true,
                         reference          : '',
                         trackingSubmodules : true]],
                    userRemoteConfigs: [
                        [credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                         url          : Constants.FOLIO_GITHUB_URL + '/folio-infrastructure.git']]],
                    changelog: false,
                    poll: false
            }
            stage('TF vars') {
                tfVars += terraform.generateTfVar('okapi_version', params.okapi_version)
                tfVars += terraform.generateTfVar('tenant_id', params.tenant_id)
                tfVars += terraform.generateTfVar('rancher_cluster_name', params.rancher_cluster_name)
                tfVars += terraform.generateTfVar('folio_repository', params.folio_repository)
                tfVars += terraform.generateTfVar('folio_release', params.folio_branch)
                tfVars += terraform.generateTfVar('stripes_image_tag', params.stripes_image_tag)
                tfVars += terraform.generateTfVar('pg_password', params.pg_password)
                tfVars += terraform.generateTfVar('pgadmin_password', params.pgadmin_password)
                withCredentials([usernamePassword(credentialsId: 'folio-docker-dev', passwordVariable: 'folio_docker_registry_password', usernameVariable: 'folio_docker_registry_username')]) {
                    tfVars += terraform.generateTfVar('folio_docker_registry_username', folio_docker_registry_username)
                    tfVars += terraform.generateTfVar('folio_docker_registry_password', folio_docker_registry_password)
                }
            }
            withCredentials([
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                 accessKeyVariable: 'TF_VAR_s3_access_key',
                 secretKeyVariable: 'TF_VAR_s3_secret_key'],
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_S3_DATA_EXPORT_ID,
                 accessKeyVariable: 'TF_VAR_s3_data_export_access_key',
                 secretKeyVariable: 'TF_VAR_s3_data_export_secret_key'],
                string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')
            ]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, params.project_name)
                    terraform.tfStatePull(tfWorkDir)
                    if (params.action == 'apply') {
                        terraform.tfPlan(tfWorkDir, tfVars)
                        terraform.tfApply(tfWorkDir)
                        okapiUrl = terraform.tfOutput(tfWorkDir, 'okapi_url')
                        stripesUrl = terraform.tfOutput(tfWorkDir, 'stripes_url')
                        /**Wait for dns */
                        def health = okapiUrl + '/_/version'
                        timeout(30) {
                            waitUntil(quiet: true) {
                                try {
                                    httpRequest ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', timeout: 900, url: health, validResponseCodes: '200,403'
                                    return true
                                } catch (exception) {
                                    println(exception.getMessage())
                                    return false
                                }
                            }
                        }
                    } else if (params.action == 'destroy') {
                        terraform.tfDestroy(tfWorkDir, tfVars)
                    }
                }
            }
            if (params.enable_modules && (params.action == 'apply' || params.action == 'nothing')) {
                OkapiUser admin_user = new OkapiUser()
                OkapiTenant tenant = new OkapiTenant()
                stage('Tenant configuration') {
                    tenant.setId(params.tenant_id)
                    tenant.setName(params.tenant_name)
                    tenant.setDescription(params.tenant_description)
                    tenant.setParameters([loadReference: params.load_reference, loadSample: params.load_sample])
                }
                stage('Admin user configuration') {
                    //TODO Think about how it could be handled without additional parameters spawning
                    admin_user.setUsername('diku_admin')
                    admin_user.setPassword('admin')
                    admin_user.setFirstName('DIKU')
                    admin_user.setLastName('ADMINISTRATOR')
                    admin_user.setEmail('admin@diku.example.org')
                    admin_user.setGroupName('staff')
                    admin_user.setPermissions(["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"])
                }
                stage('Okapi deployment') {
                    Deployment deployment = new Deployment(this, okapiUrl, "platform-${params.folio_repository}", params.folio_branch, tenant, admin_user)
                    deployment.main()
                }
            }
        } catch (exception) {
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}
