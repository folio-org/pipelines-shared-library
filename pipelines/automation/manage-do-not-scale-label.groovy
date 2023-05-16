#!groovy

@Library('pipelines-shared-library@RANCHER-769-kd') _

import org.folio.Constants
import groovy.json.JsonSlurperClassic
import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.json.JsonBuilder

def cluster_project_map = new JsonSlurperClassic().parseText(jobsParameters.generateProjectNamesMap())
assert cluster_project_map instanceof Map

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    parameters([
        choice(name: 'action', choices: ['get', 'add', 'delete'], description: '(Required) Choose what should be done with do_not_scale_down label in project'),
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
    node('rancher') {
        try {
            switch (params.action) {
                case 'get':
                    stage("Get list of label in project") {
                        helm.k8sClient {
                            awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                            String namespaceLabels = kubectl.getLabelsFromNamespace(params.rancher_project_name)

                            // Parse the JSON string
                            def labels = new groovy.json.JsonSlurperClassic().parseText(namespaceLabels)
                            
                            // Format the labels
                            StringBuilder formattedLabels = new StringBuilder()
                            labels.each { key, value ->
                                formattedLabels.append("${key}: ${value}\n")
                            }
                            
                            println formattedLabels.toString()
                        }
                    }
                case 'add':
                    stage("Add do_not_scale_down label to project") {
                        helm.k8sClient {
                            awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                    
                            println "ADD"
                        }
                    }
                case 'delete':
                    stage("Delete do_not_scale_down label to project") {
                        helm.k8sClient {
                            awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                    
                            println "Delete"
                        }
                    }
                default:
                    throw new Exception("Action ${params.action} is unknown")
                    break
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
