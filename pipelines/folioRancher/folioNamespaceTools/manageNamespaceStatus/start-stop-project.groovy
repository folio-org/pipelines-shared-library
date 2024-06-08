#!groovy
import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic
import org.folio.Constants

@Library('pipelines-shared-library') _


import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

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

        0 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=consair
        30 22 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=consair

        0 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=firebird
        30 22 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=firebird

        5 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=folijet
        45 22 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=folijet

        5 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=nla
        45 22 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=nla

        10 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spanish
        0 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spanish

        10 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spitfire
        0 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spitfire

        10 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spitfire-2nd
        0 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spitfire-2nd

        15 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=thunderjet
        15 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=thunderjet

        15 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=vega
        15 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=vega

        20 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=volaris
        30 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=volaris

        20 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=volaris-2nd
        30 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=volaris-2nd



        55 21 * * 5 %action=stop;rancher_cluster_name=folio-perf;rancher_project_name=vega
        35 23 * * 0 %action=start;rancher_cluster_name=folio-perf;rancher_project_name=vega

        55 21 * * 5 %action=stop;rancher_cluster_name=folio-perf;rancher_project_name=folijet
        35 23 * * 0 %action=start;rancher_cluster_name=folio-perf;rancher_project_name=folijet



        25 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=consortia
        40 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=consortia

        25 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=tamu
        40 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=tamu

        25 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=task-force
        45 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=task-force

        25 22 * * 5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=task-force-2nd
        45 23 * * 0 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=task-force-2nd

        0 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=bama
        0 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=bama

        0 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=consair
        0 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=consair

        0 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=firebird
        0 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=firebird

        0 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=folijet
        0 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=folijet

        30 07 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=nla
        30 22 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=nla

        10 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spanish
        15 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spanish

        10 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=spitfire
        15 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=spitfire

        15 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=thunderjet
        30 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=thunderjet

        15 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=vega
        30 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=vega

        15 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=volaris
        30 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=volaris

        20 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=volaris-2nd
        45 05 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=volaris-2nd



        55 23 * * 1-4 %action=stop;rancher_cluster_name=folio-perf;rancher_project_name=vega
        45 05 * * 2-5 %action=start;rancher_cluster_name=folio-perf;rancher_project_name=vega

        55 23 * * 1-4 %action=stop;rancher_cluster_name=folio-perf;rancher_project_name=folijet
        45 05 * * 2-5 %action=start;rancher_cluster_name=folio-perf;rancher_project_name=folijet



        20 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=consortia
        00 06 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=consortia

        20 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=tamu
        00 06 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=tamu

        25 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=task-force
        00 06 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=task-force

        25 00 * * 2-5 %action=stop;rancher_cluster_name=folio-dev;rancher_project_name=task-force-2nd
        00 06 * * 2-5 %action=start;rancher_cluster_name=folio-dev;rancher_project_name=task-force-2nd
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
        folioHelm.withK8sClient {
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
          postgresqlResources = kubectl.getKubernetesResourceList('statefulset', params.rancher_project_name).findAll { it.startsWith("postgresql-${params.rancher_project_name}") }
        }
      }
      if (params.action == 'stop') {
        stage("Downscale namespace replicas") {
          folioHelm.withK8sClient {
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
              new Tools(this).createFileFromString('deployments_replica_count_table_json', jsonString)
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
      } else if (params.action == 'start') {
        stage("Upscale namespace replicas") {
          folioHelm.withK8sClient {
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
