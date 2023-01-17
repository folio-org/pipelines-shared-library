#!groovy

@Library('pipelines-shared-library@RANCHER-332-edge') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.Okapi
import org.folio.rest.Edge
import org.folio.rest.GitHubUtility
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Project
import org.jenkinsci.plugins.workflow.libs.Library


properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        jobsParameters.refreshParameters(),
        jobsParameters.clusterName(),
        jobsParameters.projectName(),
        jobsParameters.edgeModule(),
        jobsParameters.tenantId(),
        jobsParameters.tenantName(''),
        jobsParameters.adminUsername(''),
        jobsParameters.adminPassword('', 'Please, necessarily provide password for admin user'),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.reindexElasticsearch(),
        jobsParameters.recreateIndexElasticsearch(),
        booleanParam(name: 'create_tenant', defaultValue: false, description: 'Do you need to create tenant?'),
        booleanParam(name: 'deploy_ui', defaultValue: true, description: 'Do you need to provide UI access to the new tenant?'),
        jobsParameters.repository(),
        jobsParameters.branch()])
])

OkapiTenant tenant = new OkapiTenant(id: params.tenant_id,
    name: params.tenant_name,
    description: "${params.tenant_name} tenant for ${params.edge_module}",
    tenantParameters: [loadReference: params.load_reference,
                       loadSample   : params.load_sample],
    queryParameters: [reinstall: 'false'],
    index: [reindex : params.reindex_elastic_search,
            recreate: params.recreate_elastic_search_index])

OkapiUser admin_user = okapiSettings.adminUser(username: params.admin_username,
    password: params.admin_password)

Project project_config = new Project(clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    enableModules: params.enable_modules,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)],
    configType: params.config_type)

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH PARAMETERS!')
        return
    }
    node(params.agent) {
        try {
            // if (params.create_tenant) {
            //     stage("Create tenant") {
            //             build job: 'Rancher/Update/create-tenant',
            //                 parameters: [
            //                     string(name: 'rancher_cluster_name', value: params.rancher_cluster_name),
            //                     string(name: 'rancher_project_name', value: params.rancher_project_name),
            //                     string(name: 'install_list', value: params.edge_module),
            //                     string(name: 'tenant_id', value: params.tenant_id),
            //                     string(name: 'tenant_name', value: params.tenant_name),
            //                     string(name: 'tenant_description', value: "${params.tenant_name} tenant for ${params.edge_module}"),
            //                     string(name: 'admin_username', value: params.admin_username),
            //                     password(name: 'admin_password', value: params.admin_password),
            //                     booleanParam(name: 'load_reference', value: params.load_reference),
            //                     booleanParam(name: 'load_sample', value: params.load_sample),
            //                     booleanParam(name: 'reindex_elastic_search', value: params.reindex_elastic_search),
            //                     booleanParam(name: 'recreate_elastic_search_index', value: params.recreate_elastic_search_index),
            //                     string(name: 'folio_repository', value: params.folio_repository),
            //                     string(name: 'folio_branch', value: params.folio_branch),
            //                     string(name: 'deploy_ui', value: params.deploy_ui.toString())]
            //     }
            //     println("Tenant ${params.edge_module} was created successfully")
            // }
            stage("Recreate ephemeral-properties") {
                // Map install_edge_map = new GitHubUtility(this).getEdgeModulesMap(project_config.getInstallMap())
                Map edge = ["${params.edge_module}":"vers"]

                new File('edge/test.properties').readLines().each {
                    def keyValue = it.split("=", 2)
                    if (keyValue[0]=="tenants") {
                        println "${keyValue[1]},${params.tenant_name}"
                    } 
                    if (keyValue[0]=="tenantsMappings") {
                        println "${keyValue[1]},fli01:${params.tenant_name}"
                    } 
                }

                // new Edge(this, "https://${project_config.getDomains().okapi}").renderEphemeralProperties(edge, tenant, admin_user)

                // println install_edge_map
                // if (install_edge_map) {
                //     new Edge(this, "https://${project_config.getDomains().okapi}").renderEphemeralProperties(install_edge_map, tenant, admin_user)
                //     helm.k8sClient {
                //         awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
                //         install_edge_map.each {name, version ->
                //             helm.createConfigMap("${name}-ephemeral-properties", project_config.getProjectName(), "./${name}-ephemeral-properties")
                //         }
                //     }
                //     new Edge(this, "https://${project_config.getDomains().okapi}").createEdgeUsers(tenant, install_edge_map)
                //     folioDeploy.edge(install_edge_map, project_config)
                // }                
            }
            // stage("Rollout Deployment") {
            //     helm.k8sClient {
            //         awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
            //         helm.rolloutDeployment(params.edge_module, project_config.getProjectName())
            //     }
            // }
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





// Map merge(Map... maps) {
//     Map result = [:]
//     maps.each { map ->
//         map.each { k, v ->
//             result[k] = result[k] instanceof Map ? merge(result[k], v) : v
//         }
//     }

//     result
// }


// def config = readYaml text: """
// config: 
//    num_instances: 3
//    instance_size: large
// """

// def configOverrides = readYaml text: """
// config:
//     instance_size: small
// """

// // Showcasing what the above code does:
// println "merge(config, configOverrides): " + merge(config, configOverrides)
// // => [config:[num_instances:3, instance_size:small]]
// println "merge(configOverrides, config): " + merge(configOverrides, config)
// // => [config:[instance_size:large, num_instances:3]]
