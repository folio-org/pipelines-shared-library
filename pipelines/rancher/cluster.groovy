#!groovy
@Library('pipelines-shared-library@RANCHER-704') _

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        choice(name: 'action', choices: ['apply', 'destroy'], description: 'Choose what should be done with cluster'),
        jobsParameters.clusterName(),
        jobsParameters.agents(),
        string(name: 'custom_cluster_name', defaultValue: '', description: 'Custom cluster name (Will override rancher_cluster_name)', trim: true),
        choice(name: 'eks_nodes_type', choices: ['SPOT', 'ON_DEMAND'], description: 'Select capacity associated with the EKS Node Group'),
        string(name: 'asg_instance_types', defaultValue: 'm5.xlarge,m5a.xlarge,m5d.xlarge,m5ad.xlarge', description: 'List of EC2 shapes to be used in cluster provisioning', trim: true),
        string(name: 'eks_min_size', defaultValue: '4', description: 'Minimum size of node group for eks cluster', trim: true),
        string(name: 'eks_max_size', defaultValue: '8', description: 'Maximum size of node group for eks cluster', trim: true),
        string(name: 'vpc_name', defaultValue: 'folio-rancher-vpc', description: 'Name of the target VPC', trim: true),
        booleanParam(name: 'register_in_rancher', defaultValue: true, description: 'Set to false if eks cluster should not be registered in rancher'),
        booleanParam(name: 'deploy_kubecost', defaultValue: true, description: 'Deploy Kubecost'),
        booleanParam(name: 'deploy_sorry_cypress', defaultValue: false, description: 'Deploy Sorry Cypress')
    ])
])

String tfWorkDir = 'terraform/rancher/cluster'
String tfVars = ''
String cluster_name = params.custom_cluster_name.isEmpty() ? params.rancher_cluster_name : params.custom_cluster_name

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node(params.agent) {
        try {
            stage('Ini') {
                buildName cluster_name + '.' + env.BUILD_ID
                buildDescription "action: ${params.action}\n" +
                    "eks_nodes_type: ${params.eks_nodes_type}\n" +
                    "vpc_name: ${params.vpc_name}"
            }
            stage('TF vars') {
/* Uncomment when GitHub OAuth app will be configured */
//                withCredentials([usernamePassword(credentialsId: "${cluster_name}-grafana-oauth", passwordVariable: 'github_client_secret', usernameVariable: 'github_client_id')]) {
//                    tfVars += terraform.generateTfVar('github_client_id', github_client_id)
//                    tfVars += terraform.generateTfVar('github_client_secret', github_client_secret)
//                }
                tfVars += terraform.generateTfVar('eks_nodes_type', params.eks_nodes_type)
                tfVars += terraform.generateTfVar('register_in_rancher', params.register_in_rancher)
                tfVars += terraform.generateTfVar('admin_users', Constants.AWS_ADMIN_USERS)
                tfVars += terraform.generateTfVar('deploy_kubecost', params.deploy_kubecost)
                tfVars += terraform.generateTfVar('deploy_sorry_cypress', params.deploy_sorry_cypress)
                tfVars += terraform.generateTfVar('projectID', Constants.AWS_PROJECT_ID)
                if (!params.vpc_name.isEmpty()) {
                    tfVars += terraform.generateTfVar('vpc_name', params.vpc_name)
                } else {
                    error('VPC name not specified!!!')
                }
                if (params.eks_min_size.toInteger() <= params.eks_max_size.toInteger()) {
                    tfVars += terraform.generateTfVar('eks_node_group_size', "{ \"min_size\" : ${params.eks_min_size}, \"max_size\" : ${params.eks_max_size}, \"desired_size\" : ${params.eks_min_size} }")
                } else {
                    error('eks_max_size: (' + params.eks_max_size + ') is less or equal then eks_min_size: (' + params.eks_min_size + ')')
                }
                if (!params.asg_instance_types.isEmpty()) {
                    tfVars += terraform.generateTfVar('asg_instance_types', params.asg_instance_types.replaceAll("\\s","").split(',').collect { '"' + it + '"' })
                } else {
                    error('At least one asg_instance_type should be specified')
                }
            }
            stage('Checkout') {
                checkout scm
            }
            withCredentials([
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'TF_VAR_aws_access_key_id',
                 secretKeyVariable: 'TF_VAR_aws_secret_access_key'],
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.KUBECOST_AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'TF_VAR_aws_kubecost_access_key_id',
                 secretKeyVariable: 'TF_VAR_aws_kubecost_secret_access_key'],
                string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key'),
                string(credentialsId: Constants.KUBECOST_LICENSE_KEY, variable: 'TF_VAR_kubecost_licence_key')
            ]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, cluster_name)
                    terraform.tfStatePull(tfWorkDir)
                    if (params.action == 'apply') {
                        terraform.tfPlan(tfWorkDir, tfVars)
                        terraform.tfPlanApprove(tfWorkDir)
                        terraform.tfApply(tfWorkDir)
                    } else if (params.action == 'destroy') {
                        input message: "Are you shure that you want to destroy ${cluster_name} cluster?"
//                        terraform.tfRemoveElastic(tfWorkDir)
                        terraform.tfDestroy(tfWorkDir, tfVars)
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
