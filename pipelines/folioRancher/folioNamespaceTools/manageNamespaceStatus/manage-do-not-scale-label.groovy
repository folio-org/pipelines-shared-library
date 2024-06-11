#!groovy
import org.folio.Constants

@Library('pipelines-shared-library') _


import org.jenkinsci.plugins.workflow.libs.Library

def labelKeyTonight = "do_not_scale_down_tonight"
def labelKeyUpToNextMonday = "do_not_scale_down_up_to_next_monday"

properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  parameters([
    choice(name: 'timeline', choices: ['tonight', 'up_to_next_monday'], description: '(Required) Choose what time period your environment should be UP. tonight - this night only(from Monday to Thursday), up_to_next_monday - up to next Monday(you could use it from any day of week)'),
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
      stage('Init') {
        buildName "${params.rancher_cluster_name}-${params.rancher_project_name}"
        buildDescription "action: ${params.action} label\n"
      }
      switch (params.timeline) {
        case 'tonight':
          switch (params.action) {
            case 'get':
              stage("Get list of label in project") {
                folioHelm.withK8sClient {
                  awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                  String namespaceLabels = kubectl.getLabelsFromNamespace(params.rancher_project_name)

                  // Parse the JSON string
                  def labels = new groovy.json.JsonSlurperClassic().parseText(namespaceLabels)

                  // Format the labels
                  StringBuilder formattedLabels = new StringBuilder()
                  labels.each { key, value ->
                    formattedLabels.append("${key}:${value}\n")
                    // Check if the label key is "do_not_scale_down"
                    if (key == labelKeyTonight) {
                      currentBuild.description += "${key}: ${value}\n"
                    }
                  }

                  println "\u001B[32mLabels already set to project:\u001B[0m"
                  println formattedLabels.toString()

                }
              }
              break
            case 'add':
              stage("Add do_not_scale_down label to project") {
                folioHelm.withK8sClient {
                  awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                  kubectl.addLabelToNamespace(params.rancher_project_name, labelKeyTonight, "true")
                }
              }
              break
            case 'delete':
              stage("Delete do_not_scale_down label to project") {
                folioHelm.withK8sClient {
                  awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                  kubectl.deleteLabelFromNamespace(params.rancher_project_name, labelKeyTonight)
                }
              }
              break
            default:
              throw new Exception("Action ${params.action} is unknown")
              break
          }
          break
        case 'up_to_next_monday':
          switch (params.action) {
            case 'get':
              stage("Get list of label in project") {
                folioHelm.withK8sClient {
                  awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                  String namespaceLabels = kubectl.getLabelsFromNamespace(params.rancher_project_name)

                  // Parse the JSON string
                  def labels = new groovy.json.JsonSlurperClassic().parseText(namespaceLabels)

                  // Format the labels
                  StringBuilder formattedLabels = new StringBuilder()
                  labels.each { key, value ->
                    formattedLabels.append("${key}:${value}\n")
                    // Check if the label key is "do_not_scale_down"
                    if (key == labelKeyUpToNextMonday) {
                      currentBuild.description += "${key}: ${value}\n"
                    }
                  }

                  println "\u001B[32mLabels already set to project:\u001B[0m"
                  println formattedLabels.toString()

                }
              }
              break
            case 'add':
              stage("Add do_not_scale_down label to project") {
                folioHelm.withK8sClient {
                  awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                  kubectl.addLabelToNamespace(params.rancher_project_name, labelKeyUpToNextMonday, "true")
                }
              }
              break
            case 'delete':
              stage("Delete do_not_scale_down label to project") {
                folioHelm.withK8sClient {
                  awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                  kubectl.deleteLabelFromNamespace(params.rancher_project_name, labelKeyUpToNextMonday)
                }
              }
              break
            default:
              throw new Exception("Action ${params.action} is unknown")
              break
          }
          break
        default:
          throw new Exception("Timeline ${params.timeline} is unknown")
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
