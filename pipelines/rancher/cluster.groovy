#!groovy
@Library('pipelines-shared-library@RANCHER-12') _ //TODO change to actual version before merge

import org.folio.Constants

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        string(name: 'branch', defaultValue: 'master', description: 'Terraform scripts branch checkout'),
        choice(name: 'action', choices: ['apply', 'destroy'], description: 'Choose what should be done with cluster'),
        jobsParameters.rancherClusters(),
        choice(name: 'eks_nodes_type', choices: ['SPOT', 'ON_DEMAND'], description: 'Select capacity associated with the EKS Node Group'),
        string(name: 'asg_instance_types', defaultValue: '"m5.xlarge", "m5a.xlarge", "m5d.xlarge", "m5ad.xlarge"', description: 'List of EC2 shapes to be used in cluster provisioning', trim: true),
        string(name: 'eks_min_size', defaultValue: '3', description: 'Minimum size of node group for eks cluster', trim: true),
        string(name: 'eks_max_size', defaultValue: '6', description: 'Maximum size of node group for eks cluster', trim: true),
        booleanParam(name: 'vpc_create', defaultValue: true, description: 'True if VPC should be created with cluster'),
        string(name: 'vpc_id', defaultValue: '', description: 'Should be specified if vpc_create=false', trim: true)
    ])
])

def tfWorkDir = 'rancher/terraform/rancher_cluster'
def tfVars = ''

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        try {
            stage('Ini') {
                buildName params.rancher_cluster_name + '.' + env.BUILD_ID
                buildDescription "action: ${params.action}\n" +
                    "eks_nodes_type: ${params.eks_nodes_type}\n" +
                    "vpc_create: ${params.vpc_create}\n" +
                    "vpc_id: ${params.vpc_id}"
            }
            stage('TF vars') {
                tfVars += terraform.generateTfVar('vpc_create', params.vpc_create.toString())
                if (!params.vpc_create && !params.vpc_id.isEmpty()) {
                    tfVars += terraform.generateTfVar('vpc_id', params.vpc_id)
                } else if (!params.vpc_create && params.vpc_id.isEmpty()) {
                    error('VPC Id not specified!!!')
                }
                if (params.eks_min_size.toInteger() < params.eks_max_size.toInteger()) {
                    tfVars += terraform.generateTfVar('eks_node_group_size', "{ \"min_size\" : ${params.eks_min_size}, \"max_size\" : ${params.eks_max_size}, \"desired_size\" : ${params.eks_min_size} }")
                } else {
                    error('eks_max_size: (' + params.eks_max_size + ') is less then eks_min_size: (' + params.eks_min_size + ')')
                }
                if (!params.asg_instance_types.isEmpty()) {
                    tfVars += terraform.generateTfVar('asg_instance_types', "[${params.asg_instance_types}]")
                } else {
                    error('At least one asg_instance_type should be specified')
                }
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
                         trackingSubmodules : true],
                        [$class             : 'SparseCheckoutPaths',
                         sparseCheckoutPaths: [[path: tfWorkDir]]]],
                    userRemoteConfigs: [
                        [credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                         url          : Constants.FOLIO_GITHUB_URL + '/folio-infrastructure.git']]],
                    changelog: false,
                    poll: false
            }
            withCredentials([
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')
            ]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, params.rancher_cluster_name)
                    terraform.tfStatePull(tfWorkDir)
                    if (params.action == 'apply') {
                        terraform.tfPlan(tfWorkDir, tfVars)
                        terraform.tfPlanApprove(tfWorkDir)
                        terraform.tfApply(tfWorkDir)
                    } else if (params.action == 'destroy') {
                        terraform.tfDestroy(tfWorkDir, tfVars)
                    }
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
