#!groovy
@Library('pipelines-shared-library') _

//import org.folio.Constants
//import org.folio.rest.Deployment
//import org.folio.rest.model.Email
//import org.folio.rest.model.OkapiUser
//import org.folio.rest.model.OkapiTenant
//import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library


properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.okapiVersion(),
        jobsParameters.projectName(),
        jobsParameters.loadReference(),
        jobsParameters.loadSample()
    ])
])
//def rancherClusters = "folio-testing"
//def frontendImageTag = "folio-testing-karate-diku-e025d02"
//def tenantId = "diku"
//def tenantName = "Datalogisk Institut"
//def tenantDescription = "Danish Library Technology Institute"

//def pgPassword = ""
//def pgAdminPassword = ""
//def github_teams = ''

ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node('jenkins-agent-java11') {
        stage('Test') {
            echo "params.folio_repository"
        }


//        try {
//            stage('Build project Job') {
//                if (params.build_ui && params.action == 'apply') {
//                    build job: 'Rancher/Project',
//                        parameters: [
//                            booleanParam(name: 'refreshParameters', value: false),
//                            string(name: 'folio_repository', value: params.folio_repository),
//                            string(name: 'folio_branch', value: params.folio_branch),
//                            string(name: 'rancher_cluster_name', value: rancherClusters),
//                            string(name: 'rancher_project_name', value: params.projectName),
//                            booleanParam(name: 'build_ui', value: true),
//                            booleanParam(name: 'envType', value: true),
//                            booleanParam(name: 'enable_modules', value: true),
//                            string(name: 'tenant_id', value: tenantId),
//                            string(name: 'tenant_name', value: tenantName),
//                            string(name: 'tenant_description', value: tenantDescription),
//                            booleanParam(name: 'reindex_elastic_search', value: true),
//                            booleanParam(name: 'recreate_index_elastic_search', value: false),
//                            booleanParam(name: 'load_reference', value: params.loadReference),
//                            booleanParam(name: 'load_sample', value: params.loadSample),
//                            string(name: 'github_teams', defaultValue: ''),
//                            booleanParam(name: 'restore_postgresql_from_backup', value: false),
//                            string(name: 'restore_tenant_id', value: tenantId),
//                            booleanParam(name: 'restore_postgresql_backup_name', value: false),
//                            booleanParam(name: 'pg_embedded', value: true),
//                            booleanParam(name: 'kafka_embedded', value: true),
//                            booleanParam(name: 'es_embedded', value: true),
//                            booleanParam(name: 's3_embedded', value: true),
//                            booleanParam(name: 'pgadmin4', value: true)
//
//
////                                    string(name: 'custom_hash', value: hash),
////                                    string(name: 'custom_url', value: okapiUrl),
////                                    string(name: 'custom_tag', value: tag)
//                        ]
//                }
//            }
//        } catch (exception) {
//            println(exception)
//            error(exception.getMessage())
//        } finally {
//            stage('Cleanup') {
//                cleanWs notFailBuild: true
//            }
//        }
    }
}

