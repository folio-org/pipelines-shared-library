#!groovy

@Library('pipelines-shared-library@RANCHER-751_RANCHER-753_RANCHER-754-adapt-for-kube') _

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
        choice(name: 'action', choices: ['start', 'stop'], description: '(Required) Choose what should be done with project'),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.refreshParameters()]),
    pipelineTriggers([
        parameterizedCron('''
        55 22 * * 5 %action=stop;rancher_cluster_name=folio-perf;rancher_project_name=vega
        55 22 * * 5 %action=stop;rancher_cluster_name=folio-perf;rancher_project_name=folijet
        0 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=bama
        0 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=firebird
        5 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=folijet
        5 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=nla
        10 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spanish
        10 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spitfire
        15 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=thunderjet
        15 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=vega
        20 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=volaris
        20 23 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=volaris-2nd
        30 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=bama
        30 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=firebird
        45 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=folijet
        45 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=nla
        0 00 * * 1 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spanish
        0 00 * * 1 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spitfire
        15 00 * * 1 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=thunderjet
        15 00 * * 1 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=vega
        30 00 * * 1 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=volaris
        30 00 * * 1 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=volaris-2nd
        35 00 * * 1 %action=start;rancher_cluster_name=folio-perf;rancher_project_name=vega
        35 00 * * 1 %action=start;rancher_cluster_name=folio-perf;rancher_project_name=folijet
    ''')
    ])
])

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    podTemplate(inheritFrom: 'rancher-kube', containers: [
        containerTemplate(name: 'k8sclient', image: Constants.DOCKER_K8S_CLIENT_IMAGE, command: "sleep", args: "99999999")]
    ) {
        node(POD_LABEL) {
            try {
                container('k8sclient') {
                    withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                                      credentialsId    : Constants.AWS_CREDENTIALS_ID,
                                      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                        if (params.action == 'stop') {
                            stage("Downscale namespace replicas") {
                                awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                                def deployments_list = kubectl.getKubernetesResourceList('deployment', params.rancher_project_name)
                                def postgresql = kubectl.getKubernetesResourceList('statefulset', params.rancher_project_name).findAll { it.startsWith("postgresql-${params.rancher_project_name}") }
                                kubectl.deleteConfigMap('deployments-replica-count-json', params.rancher_project_name)
                                def deployments_replica_count_table = [:]
                                deployments_list.each { deployment ->
                                    deployments_replica_count_table.put(deployment, kubectl.getKubernetesResourceCount('deployment', deployment, params.rancher_project_name))
                                }
                                def jsonString = new JsonBuilder(deployments_replica_count_table).toString()
                                new Tools(this).createFileFromString('deployments_replica_count_table_json', jsonString)
                                kubectl.createConfigMap('deployments-replica-count-json', params.rancher_project_name, './deployments_replica_count_table_json')
                                deployments_list.each { deployment ->
                                    kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, '0')
                                }
                                if (!kubectl.checkKubernetesResourceExist('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name)) {
                                    kubectl.setKubernetesResourceCount('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name, '0')
                                } else {
                                    awscli.stopRdsCluster("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                                }
                            }
                        } else if (params.action == 'start') {
                            stage("Upscale namespace replicas") {
                                awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                                String configMap = kubectl.getConfigMap('deployments-replica-count-json', params.rancher_project_name, 'deployments_replica_count_table_json')
                                def deployments_list = new groovy.json.JsonSlurperClassic().parseText(configMap)
                                def services_list = deployments_list.findAll { key, value -> !["mod-", "edge-", "okapi", "ldp-server", "ui-bundle"].any { prefix -> key.startsWith(prefix) } }
                                def core_modules_list = ["okapi", "mod-users", "mod-users-bl", "mod-login", "mod-permissions", "mod-authtoken"]
                                def core_modules_list_map = deployments_list.findAll { key, value -> core_modules_list.any { prefix -> key.startsWith(prefix) } }
                                def backend_module_list = deployments_list.findAll { key, value -> ["mod-"].any { prefix -> key.startsWith(prefix) } }
                                def edge_module_list = deployments_list.findAll { key, value -> ["edge-"].any { prefix -> key.startsWith(prefix) } }
                                def ui_bundle_list = deployments_list.findAll { key, value -> ["ui-bundle"].any { prefix -> key.contains(prefix) } }
                                if (!kubectl.checkKubernetesResourceExist('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name)) {
                                    kubectl.setKubernetesResourceCount('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name, '1')
                                    kubectl.waitKubernetesResourceStableState('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name, '1', '600')
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
                                ui_bundle_list.each { deployment ->
                                    kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
                                }
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
}
