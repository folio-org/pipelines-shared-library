#!groovy
import org.folio.Constants

@Library('pipelines-shared-library') _

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        choice(name: 'action', choices: ['apply', 'destroy'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.branch(),
        jobsParameters.okapiVersion(),
        jobsParameters.agents(),
        string(name: 'github_teams', defaultValue: '', description: 'Coma separated list of GitHub teams who need access to project')
    ])
])

def clusterName = "folio-testing"
def projectName = "spring"
def tenantId = "diku"
def okapiUrl = "https://${clusterName}-${projectName}-okapi.ci.folio.org"
def frontendUrl = "https://${clusterName}-${projectName}-${tenantId}.ci.folio.org"

ansiColor("xterm") {
    if (params.refreshParameters) {
        currentBuild.result = "ABORTED"
        error("DRY RUN BUILD, NO STAGE IS ACTIVE!")
    }
    node(params.agent) {
        try {
            stage("Create environment") {
                build job: Constants.JENKINS_JOB_PROJECT,
                    parameters: [
                        booleanParam(name: 'refreshParameters', value: false),
                        string(name: 'action', value: params.action),
                        string(name: 'folio_repository', value: params.folio_repository),
                        string(name: 'folio_branch', value: params.folio_branch),
                        string(name: 'okapi_version', value: params.okapi_version),
                        string(name: 'rancher_cluster_name', value: clusterName),
                        string(name: 'rancher_project_name', value: projectName),
                        booleanParam(name: 'ui_bundle_build', value: true),
                        string(name: 'config_type', value: "testing"),
                        booleanParam(name: 'enable_modules', value: true),
                        string(name: 'tenant_id', value: tenantId),
                        string(name: 'tenant_name', value: "Spring tenant"),
                        string(name: 'tenant_description', value: "Spring tests main tenant"),
                        booleanParam(name: 'reindex_elastic_search', value: true),
                        booleanParam(name: 'recreate_elastic_search_index', value: false),
                        booleanParam(name: 'load_reference', value: true),
                        booleanParam(name: 'load_sample', value: true),
                        string(name: 'github_teams', value: params.github_teams),
                        booleanParam(name: 'pg_embedded', value: true),
                        booleanParam(name: 'kafka_shared', value: true),
                        booleanParam(name: 'opensearch_shared', value: true),
                        booleanParam(name: 's3_embedded', value: true),
                        booleanParam(name: 'pgadmin4', value: true),
                        booleanParam(name: 'greenmail_server', value: true)
                    ]
            }
        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage("Cleanup") {
                println(okapiUrl)
                println(frontendUrl)
                cleanWs notFailBuild: true
            }
        }
    }
}
