#!groovy
@Library('pipelines-shared-library@RANCHER-444') _

import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.okapiVersion(),
        jobsParameters.projectDevName(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample()
    ])
])
//def rancherClusters = "folio-dev"
//def tenantId = "diku"
//def tenantName = "Datalogisk Institut"
//def tenantDescription = "Danish Library Technology Institute"
//def envConfig = "development"
//def agents = "jenkins-agent-java11"

node('jenkins-agent-java11') {
        try {
            stage('Build project Job') {
                if (params.action == 'apply' || params.action == 'destroy') {
                    build job: 'Rancher/Project',
                        parameters: [
                            string(name: 'action', value: params.action),
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'okapi_version', value: params.okapi_version),
//                            string(name: 'rancher_cluster_name', value: rancherClusters),
                            string(name: 'rancher_project_name', value: params.projectName),
//                            string(name: 'env_config', value: envConfig),
//                            booleanParam(name: 'enable_modules', value: true),
//                            booleanParam(name: 'reindex_elastic_search', value: true),
//                            booleanParam(name: 'recreate_index_elastic_search', value: false),
                            booleanParam(name: 'load_reference', value: params.load_reference),
                            booleanParam(name: 'load_sample', value: params.load_sample),
//                            booleanParam(name: 'pg_embedded', value: true),
//                            booleanParam(name: 'kafka_embedded', value: true),
//                            booleanParam(name: 'es_embedded', value: true),
//                            booleanParam(name: 's3_embedded', value: true),
//                            booleanParam(name: 'pgadmin4', value: true)
                        ]
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

