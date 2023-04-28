#!groovy

@Library('pipelines-shared-library@RANCHER-754') _

import org.folio.Constants
import groovy.json.JsonSlurperClassic
import org.jenkinsci.plugins.workflow.libs.Library

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
            if (params.action == 'stop') {
                stage("Downscale namespace replicas") {
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        def deployments_list = kubectl.getKubernetesResourceList('deployment', params.rancher_project_name)
                        def postgresql = kubectl.getKubernetesResourceList('statefulset',params.rancher_project_name).findAll{it.startsWith("postgresql-${params.rancher_project_name}")}
                        deployments_list.each { deployment ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 0)
                        }
                        if (!kubectl.checkKubernetesResourceExist('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name)){
                            kubectl.setKubernetesResourceCount('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name, 0)
                        }
                        else {
                            awscli.stopRdsCluster("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                        }
                    }
                }
            }
            else if (params.action == 'start') {
                stage("Upscale namespace replicas") {
                    helm.k8sClient {
                        awscli.getKubeConfig(Constants.AWS_REGION, params.rancher_cluster_name)
                        List deployments_list = kubectl.getKubernetesResourceList('deployment',params.rancher_project_name)
                        def services_list = deployments_list.findAll {!it.startsWith("mod-") && !it.startsWith("edge-") && !it.startsWith("okapi")}
                        List core_modules_list = "okapi, mod-users, mod-users-bl, mod-login, mod-permissions, mod-authtoken".split(", ")
                        def backend_module_list = deployments_list.findAll{it.startsWith("mod-")}
                        def edge_module_list = deployments_list.findAll{it.startsWith("edge-")}
                        if (!kubectl.checkKubernetesResourceExist('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name)){
                            kubectl.setKubernetesResourceCount('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name, 1)
                            kubectl.waitKubernetesResourceStableState('statefulset', "postgresql-${params.rancher_project_name}", params.rancher_project_name, '1', '600')
                        }
                        else {
                            awscli.startRdsCluster("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                            awscli.waitRdsClusterAvailable("rds-${params.rancher_cluster_name}-${params.rancher_project_name}", Constants.AWS_REGION)
                            sleep 30
                        }
                        services_list.each { deployment ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
                            kubectl.checkDeploymentStatus(deployment, params.rancher_project_name, "600")
                            sleep 15
                        }
                        core_modules_list.each { deployment ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
                            kubectl.checkDeploymentStatus(deployment, params.rancher_project_name, "600")
                            sleep 15
                        }
                        backend_module_list.each { deployment ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
                        }
                        edge_module_list.each { deployment ->
                            kubectl.setKubernetesResourceCount('deployment', deployment.toString(), params.rancher_project_name, 1)
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
