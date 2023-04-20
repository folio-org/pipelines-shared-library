#!groovy

@Library('pipelines-shared-library@RANCHER-750') _

import org.folio.Constants
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic
import org.jenkinsci.plugins.workflow.libs.Library

def cluster_project_map = new JsonSlurperClassic().parseText(jobsParameters.generateProjectNamesMap())
assert cluster_project_map instanceof Map

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        choice(name: 'action', choices: ['start', 'stop'], description: '(Required) Choose what should be done with project'),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.refreshParameters()])
])



ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node('rancher||jenkins-agent-java11') {
        try {
            stage("Downscale namespace replicas") {
                helm.k8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                    def deployments_list = awscli.getDeploymentsList(params.rancher_project_name) as ArrayList
                    println("x")
                    println(deployments_list)
                    println("y")
                    deployments_list.forEach {deployment ->
                        awscli.setDeploymentCount(deployment.toString(), params.rancher_project_name, 0)
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
