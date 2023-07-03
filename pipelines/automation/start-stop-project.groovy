#!groovy

@Library('pipelines-shared-library@RANCHER-876') _

import org.folio.Constants
import groovy.json.JsonSlurperClassic
import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.json.JsonBuilder
import java.util.Calendar

def cluster_project_map = new JsonSlurperClassic().parseText(jobsParameters.generateProjectNamesMap())
assert cluster_project_map instanceof Map
def labelKeyTonight = "do_not_scale_down_tonight"
def labelKeyUpToNextMonday = "do_not_scale_down_up_to_next_monday"
def tonightLabelKeyExists = false
def weekendsLabelKeyExists = false
def postgresqlResources

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    parameters([
        choice(name: 'action', choices: ['start', 'stop'], description: '(Required) Choose what should be done with project'),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.refreshParameters()]),
    pipelineTriggers([
        parameterizedCron('''
        0 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=bama
        30 22 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=bama


    ''')
    ])
])

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node('rancher') {
        try {
            Calendar calendar = Calendar.getInstance()
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            stage('Init') {
                buildName "${params.rancher_cluster_name}-${params.rancher_project_name}.${params.action}"
                buildDescription "action: ${params.action} project\n"
                helm.k8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        String namespaceLabels = kubectl.getLabelsFromNamespace(params.rancher_project_name)
                        def labels = new groovy.json.JsonSlurperClassic().parseText(namespaceLabels)
                        labels.each { key, value ->
                            // Check if the label key is "do_not_scale_down"
                            if (key == labelKeyTonight) {
                                tonightLabelKeyExists = true
                            }
                            if (key == labelKeyUpToNextMonday) {
                                weekendsLabelKeyExists = true
                            }
                        }
                        postgresqlResources = kubectl.getKubernetesResourceList('statefulset',params.rancher_project_name).findAll{it.startsWith("postgresql-${params.rancher_project_name}")}
                }
            }
            if (params.action == 'stop') {
                stage("Downscale namespace replicas") {
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)

                        if (weekendsLabelKeyExists == true || (tonightLabelKeyExists == true && (dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.TUESDAY || dayOfWeek == Calendar.WEDNESDAY || dayOfWeek == Calendar.THURSDAY))) {
                            println "\u001B[32mProject ${params.rancher_project_name} has label ${labelKeyTonight}/${labelKeyUpToNextMonday} and will not be disabled this time.\u001B[0m"
                            currentBuild.description += "Skip stop action"
                        } else {
                            println "Shutting down the project..."
                            def deployments_list = kubectl.getKubernetesResourceList('deployment', params.rancher_project_name)
                            kubectl.deleteConfigMap('deployments-replica-count-json', params.rancher_project_name)
                            def deployments_replica_count_table = [:]
                            deployments_list.each { deployment ->
                                deployments_replica_count_table.put(deployment, kubectl.getKubernetesResourceCount('deployment', deployment, params.rancher_project_name))
                            }
                            def jsonString = new JsonBuilder(deployments_replica_count_table).toString()
                            new Tools(this).createFileFromString('deployments_replica_count_table_json',jsonString)
                            kubectl.createConfigMap('deployments-replica-count-json', params.rancher_project_name, './deployments_replica_count_table_json')
                            deployments_list.each { deployment ->
                                kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, '0')
                            }
                            // Stop postgresql service
                            if (postgresqlResources) {
                                postgresqlResources.each { postgresqlName ->
                                    kubectl.setKubernetesResourceCount('statefulset', postgresqlName, params.rancher_project_name, '0')
                                }
                            } else {
                                awscli.stopRdsCluster("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                            }
                        }
                    }
                }
            }
            else if (params.action == 'start') {
                stage("Upscale namespace replicas") {
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        String configMap = kubectl.getConfigMap('deployments-replica-count-json', params.rancher_project_name, 'deployments_replica_count_table_json')
                        def deployments_list = new groovy.json.JsonSlurperClassic().parseText(configMap)
                        def services_list = deployments_list.findAll { key, value -> !["mod-", "edge-", "okapi", "ldp-server", "ui-bundle"].any { prefix -> key.startsWith(prefix) } }
                        def core_modules_list = ["okapi", "mod-users", "mod-users-bl", "mod-login", "mod-permissions", "mod-authtoken"]
                        def core_modules_list_map = deployments_list.findAll { key, value -> core_modules_list.any { prefix -> key.startsWith(prefix) } }
                        def backend_module_list = deployments_list.findAll { key, value -> ["mod-"].any { prefix -> key.startsWith(prefix) } }
                        def edge_module_list = deployments_list.findAll { key, value -> ["edge-"].any { prefix -> key.startsWith(prefix) } }
                        def ui_bundle_list = deployments_list.findAll { key, value -> ["ui-bundle"].any { prefix -> key.contains(prefix) } }
                        // Start postgresql service
                        if (postgresqlResources) {
                            postgresqlResources.each { postgresqlName ->
                                kubectl.setKubernetesResourceCount('statefulset', postgresqlName, params.rancher_project_name, '1')
                                kubectl.waitKubernetesResourceStableState('statefulset', postgresqlName, params.rancher_project_name, '1', '600')
                            }
                        } else {
                            awscli.startRdsCluster("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                            awscli.waitRdsClusterAvailable("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                            sleep 20
                        }
                        println(services_list)

                        services_list.each { deployment, replica_count ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, replica_count.toString())
                            kubectl.checkDeploymentStatus(deployment, params.rancher_project_name, "600")
                            sleep 10
                        }
                        core_modules_list_map.each { deployment, replica_count ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, replica_count.toString())
                            kubectl.checkDeploymentStatus(deployment, params.rancher_project_name, "600")
                            sleep 10
                        }
                        backend_module_list.each { deployment, replica_count ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, replica_count.toString())
                        }
                        edge_module_list.each { deployment, replica_count ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, replica_count.toString())
                        }
                        ui_bundle_list.each { deployment, replica_count ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, replica_count.toString())
                        }

                        // Delete tag if Monday or Sunday
                        if ((dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.SUNDAY) && weekendsLabelKeyExists) {
                            println "Deleting ${labelKeyUpToNextMonday} label from project ${params.rancher_project_name}"
                            kubectl.deleteLabelFromNamespace(params.rancher_project_name, labelKeyUpToNextMonday)
                        }
                        // Everyday delete tonight tag
                        if (tonightLabelKeyExists) {
                            println "Deleting ${labelKeyTonight} label from project ${params.rancher_project_name}"
                            kubectl.deleteLabelFromNamespace(params.rancher_project_name, labelKeyTonight)
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
