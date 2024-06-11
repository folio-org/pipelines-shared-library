#!groovy
@Library('pipelines-shared-library') _

import org.folio.Constants

properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  disableConcurrentBuilds(),
  parameters([
    choice(name: 'action', choices: ['apply', 'destroy'], description: 'Choose what should be done with cluster'),
    jobsParameters.clusterName(),
    string(name: 'custom_cluster_name', defaultValue: '', description: 'Custom cluster name (Will override rancher_cluster_name)', trim: true),
    choice(name: 'eks_nodes_type', choices: ['ON_DEMAND', 'SPOT'], description: 'Select capacity associated with the EKS Node Group. (SPOT-for testing purposes only)'),
    string(name: 'asg_instance_types', defaultValue: 'r5a.xlarge', description: 'List of EC2 shapes to be used in cluster provisioning', trim: true),
    string(name: 'eks_min_size', defaultValue: '2', description: 'Minimum size of node group for eks cluster', trim: true),
    string(name: 'eks_max_size', defaultValue: '3', description: 'Maximum size of node group for eks cluster', trim: true),
    string(name: 'vpc_name', defaultValue: 'folio-rancher-vpc', description: 'Name of the target VPC', trim: true),
    booleanParam(name: 'register_in_rancher', defaultValue: true, description: 'Register in Rancher'),
    booleanParam(name: 'deploy_kubecost', defaultValue: true, description: 'Deploy Kubecost'),
    booleanParam(name: 'deploy_sorry_cypress', defaultValue: false, description: 'Deploy Sorry Cypress'),
    jobsParameters.agents(),
    booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration')
  ])
])

Map context = [
  tf_work_dir : 'terraform/rancher/cluster',
  tf_vars     : '',
  cluster_name: params.custom_cluster_name.isEmpty() ? params.rancher_cluster_name : params.custom_cluster_name
]
context.putAll(params)

ansiColor('xterm') {
  if (params.refreshParameters) {
    currentBuild.result = 'ABORTED'
    error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
  }
  node(params.agent) {
    try {
      stage('Ini') {
        buildName context.cluster_name + '.' + env.BUILD_ID
        buildDescription "action: ${context.action}\n" +
          "eks_nodes_type: ${context.eks_nodes_type}\n" +
          "vpc_name: ${context.vpc_name}"
      }
      stage('TF vars') {
        common.throwErrorIfStringIsEmpty(context.vpc_name, 'VPC name not specified')
        common.throwErrorIfStringIsEmpty(context.asg_instance_types, 'At least one asg_instance_type should be specified')
        if (context.eks_min_size.toInteger() >= context.eks_max_size.toInteger()) {
          error('eks_max_size: (' + context.eks_max_size + ') is less or equal then eks_min_size: (' + context.eks_min_size + ')')
        }
        String eks_nodes_group_size = writeJSON returnText: true, json: [min_size: context.eks_min_size, max_size: context.eks_max_size]
        Map tf_vars_map = [
          admin_users         : Constants.AWS_ADMIN_USERS,
          register_in_rancher : context.register_in_rancher,
          vpc_name            : context.vpc_name,
          eks_nodes_type      : context.eks_nodes_type,
          eks_nodes_group_size: eks_nodes_group_size,
          asg_instance_types  : context.asg_instance_types.trim().split(',').collect { "\"$it\"" },
          deploy_kubecost     : context.deploy_kubecost,
          deploy_sorry_cypress: context.deploy_sorry_cypress,
        ]
        context.tf_vars = folioTerraform.generateTfVars(tf_vars_map)
      }
      stage('Checkout') {
        checkout scm
      }
      withCredentials([
        [$class           : 'AmazonWebServicesCredentialsBinding',
         credentialsId    : Constants.AWS_CREDENTIALS_ID,
         accessKeyVariable: 'TF_VAR_aws_access_key_id',
         secretKeyVariable: 'TF_VAR_aws_secret_access_key'],
        string(credentialsId: Constants.KUBECOST_LICENSE_KEY, variable: 'TF_VAR_kubecost_licence_key'),
        string(credentialsId: Constants.SLACK_WEBHOOK_URL, variable: 'TF_VAR_slack_webhook_url')
      ])
        {
          folioTerraform.withTerraformClient {
            folioTerraform.tfInit(context.tf_work_dir, '')
            folioTerraform.tfWorkspaceSelect(context.tf_work_dir, context.cluster_name)
            folioTerraform.tfStatePull(context.tf_work_dir)
            if (context.action == 'apply') {
              folioTerraform.tfPlan(context.tf_work_dir, context.tf_vars)
              folioTerraform.tfPlanApprove(context.tf_work_dir)
              folioTerraform.tfApply(context.tf_work_dir)
            } else if (context.action == 'destroy') {
              input message: "Are you shure that you want to destroy ${context.cluster_name} cluster?"
              try {
                folioTerraform.tfRemoveElastic(context.tf_work_dir)
              }
              catch (exception) {
                println(exception)
              }
              folioTerraform.tfDestroy(context.tf_work_dir, context.tf_vars)
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
