@Library('pipelines-shared-library') _

import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

def allureVersion = "2.17.2"

def clusterName = "folio-testing"
def projectName = "cypress"
def folio_repository = "platform-complete"
def folio_branch = "snapshot"
def tenant = "diku"
def uiUrl = "https://${clusterName}-${projectName}-${tenant}.ci.folio.org"
def okapiUrl = "https://${clusterName}-${projectName}-okapi.ci.folio.org"
def edgeUrl = "https://${clusterName}-${projectName}-edge.ci.folio.org"

def spinUpEnvironmentJobName = "/Rancher/Project"
def spinUpEnvironmentJob
def tearDownEnvironmentJob

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0] //versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]

pipeline {
    agent { label 'rancher' }

    triggers {
        cron('H 0 * * 1-6')
    }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Cypress tests repository branch to checkout')
    }

    stages {
        stage("Destroy environment") {
            steps {
                script {
                    def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, clusterName,
                        projectName, tenant, folio_repository, folio_branch)

                    tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

       stage("Create environment") {
           steps {
               script {
                   def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, clusterName,
                       projectName, tenant, folio_repository, folio_branch)

                   spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
               }
           }
       }

        stage("Run cypress tests") {
           when {
               expression {
                   spinUpEnvironmentJob.result == 'SUCCESS'
               }
           }
            steps {
                script {
                    def jobParameters = [
                        branch           : params.branch,
                        uiUrl            : uiUrl,
                        okapiUrl         : okapiUrl,
                        tenant           : tenant,
                        user             : 'diku_admin',
                        password         : 'admin',
                        cypressParameters: "--env grepTags=\"smoke criticalPth\",grepFilterSpecs=true",
                        customBuildName  : JOB_BASE_NAME,
                        timeout          : '6',
                        testrailProjectID: '14',
                        testrailRunID    : '2108'
                    ]
                    cypressStages(jobParameters)
                }
            }
        }

    //    stage("Set job execution result") {
    //        when {
    //            expression {
    //                spinUpEnvironmentJob.result != 'SUCCESS'
    //            }
    //        }
    //        steps {
    //            script {
    //                currentBuild.result = 'FAILURE'
    //            }
    //        }
    //    }
    }
}

private List getEnvironmentJobParameters(String action, String okapiVersion, clusterName, projectName, tenant,
                                         folio_repository, folio_branch) {
    [
        string(name: 'action', value: action),
        string(name: 'config_type', value: "testing"),
        string(name: 'rancher_cluster_name', value: clusterName),
        string(name: 'rancher_project_name', value: projectName),
        string(name: 'okapi_version', value: okapiVersion),
        booleanParam(name: 'ui_bundle_build', value: true),
        booleanParam(name: 'enable_modules', value: true),
        string(name: 'folio_repository', value: folio_repository),
        string(name: 'folio_branch', value: folio_branch),
        string(name: 'tenant_id', value: tenant),
        string(name: 'tenant_name', value: "Cypress tenant"),
        string(name: 'tenant_description', value: "Cypress tests main tenant"),
        booleanParam(name: 'load_reference', value: true),
        booleanParam(name: 'load_sample', value: true),
        booleanParam(name: 'pg_embedded', value: true),
        booleanParam(name: 'kafka_embedded', value: true),
        booleanParam(name: 'es_embedded', value: true),
        booleanParam(name: 's3_embedded', value: true)
    ]
}
