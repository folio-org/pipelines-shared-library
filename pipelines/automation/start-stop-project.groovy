#!groovy

@Library('pipelines-shared-library@RANCHER-750') _

import org.folio.Constants
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

String core_modules = "okapi, mod-permissions, mod-users, mod-users-bl, mod-authtoken"
List core_modules_list = core_modules.split(", ")

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node('rancher||jenkins-agent-java11') {
        try {
            if (params.action == 'stop') {
                stage("Downscale namespace replicas") {
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        def deployments_list = awscli.getKubernetesResourceList('deployment', params.rancher_project_name)
                        def statefulset_list = awscli.getKubernetesResourceList('statefulset', params.rancher_project_name)
                        deployments_list.each { deployment ->
                            awscli.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 0)
                        }
                        statefulset_list.each { deployment ->
                            awscli.setKubernetesResourceCount('statefulset', deployment.toString(), params.rancher_project_name, 0)
                        }
                    }
                }
            }
            else if (params.action == 'start') {
                stage("Upscale namespace replicas") {
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        def deployments_list = awscli.getKubernetesResourceList('deployment',params.rancher_project_name)
                        def statefulset_list = awscli.getKubernetesResourceList('statefulset',params.rancher_project_name)
                        statefulset_list.each { statefulset ->
                            awscli.setKubernetesResourceCount('statefulset', statefulset.toString(), params.rancher_project_name, 1)
                            common.waitKubernetesResourceStableState('statefulset', statefulset.toString(), params.rancher_project_name, '1', '600')
                        }
                        core_modules_list.each { deployment ->
                            awscli.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
                            //common.waitKubernetesResourceStableState('deployment', deployment.toString(), params.rancher_project_name, '1', '600')
                            sleep 60
                        }
                        deployments_list.each { deployment ->
                            awscli.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
                        }
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
