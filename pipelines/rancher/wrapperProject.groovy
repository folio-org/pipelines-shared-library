#!groovy
@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.okapiVersion(),
//        jobsParameters.projectName(),
        // Question 1: How should i get projectName list dynamically ?
        choice(name: 'projectName', choices: ["bama","concorde","core-platform","ebsco-core","falcon","firebird","folijet",
                                              "metadata","prokopovych","scout","spanish","spitfire","sprint-testing",
                                              "stripes-force","thor","thunderjet","unam","vega","volaris","volaris-2nd"],
                                              description: "(Required) Select project to operate"),
        jobsParameters.loadReference(),
        jobsParameters.loadSample()
    ])
])
def rancherClusters = "folio-dev"
def tenantId = "diku"
def tenantName = "Datalogisk Institut"
def tenantDescription = "Danish Library Technology Institute"
//def pgPassword = "**********"
//def pgAdminPassword    = "********"
//Question 2: Should I write passwd here in open mode ? Or reused this parameters from Rancher/Project ?
def envConfig = "development"
def agents = "jenkins-agent-java11"

node('jenkins-agent-java11') {
        try {
            stage('Build project Job') {
                if (params.action == 'apply' || params.action == 'destroy') {
                    build job: 'Rancher/Project',
                        parameters: [
                            // Question 3 : Should I put here all parmeters or only parameters from properties.params ?
                            string(name: 'action', value: params.action),
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'okapi_version', value: params.okapi_version),
                            string(name: 'rancher_cluster_name', value: rancherClusters),
                            string(name: 'rancher_project_name', value: params.projectName),
                            string(name: 'env_config', value: envConfig),
                            booleanParam(name: 'enable_modules', value: true),
                            booleanParam(name: 'reindex_elastic_search', value: true),
                            booleanParam(name: 'recreate_index_elastic_search', value: false),
                            booleanParam(name: 'load_reference', value: params.load_reference),
                            booleanParam(name: 'load_sample', value: params.load_sample),
                            booleanParam(name: 'pg_embedded', value: true),
                            booleanParam(name: 'kafka_embedded', value: true),
                            booleanParam(name: 'es_embedded', value: true),
                            booleanParam(name: 's3_embedded', value: true),
                            booleanParam(name: 'pgadmin4', value: true)
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

