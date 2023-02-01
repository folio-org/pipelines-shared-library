#!groovy
@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.okapiVersion(),
        jobsParameters.projectDevName(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample(),
        jobsParameters.reindexElasticsearch()
    ])
])
def rancher_clusters = "folio-dev"

node('rancher') {
        try {
            stage('Build project Job') {
                if (params.action == 'apply' || params.action == 'destroy') {
                    build job: 'Rancher/Project',
                        parameters: [
                            string(name: 'action', value: params.action),
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'okapi_version', value: params.okapi_version),
                            string(name: 'rancher_cluster_name', value: rancher_clusters),
                            string(name: 'rancher_project_name', value: params.rancher_project_name),
                            booleanParam(name: 'load_reference', value: params.load_reference),
                            booleanParam(name: 'load_sample', value: params.load_sample),
                            booleanParam(name: 'reindex_elastic_search', value: params.reindex_elastic_search),
                            booleanParam(name: 'ui_bundle_build', value: true)
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

