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


def spinUpEnvironmentJobName = "/Rancher/Project"
def spinUpEnvironmentJob
def tearDownEnvironmentJob
def cypressTestsJobName = "/Testing/Cypress tests"
def cypressTestsJob

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0] //versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]

pipeline {
    agent { label 'jenkins-agent-java11' }

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
                        string(name: 'branch', value: params.branch),
                        string(name: 'uiUrl', value: uiUrl),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'user', value: 'diku_admin'),
                        password(name: 'password', value: 'admin'),
                        string(name: 'cypressParameters', value: '--env grepTags="smoke criticalPth",grepFilterSpecs=true'),
                        string(name: 'customBuildName', value: JOB_BASE_NAME),
                        string(name: 'timeout', value: "6"),
                        string(name: 'testrailProjectID', value: "14"),
                        string(name: 'testrailRunID', value: "2108")
                    ]

                    cypressTestsJob = build job: cypressTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel") {
            parallel {
                stage("Collect test results") {
                   when {
                       expression {
                           spinUpEnvironmentJob.result == 'SUCCESS'
                       }
                   }
                    stages {
                        stage("Copy downstream job artifacts") {
                            steps {
                                script {
                                    def jobNumber = cypressTestsJob.number
                                    copyArtifacts(projectName: cypressTestsJobName, selector: specific("${jobNumber}"), filter: "allure-results.zip")

                                    unzip zipFile: "allure-results.zip", dir: "."
                                }
                            }
                        }

                        stage('Publish tests report') {
                            steps {
                                allure([
                                    includeProperties: false,
                                    jdk              : '',
                                    commandline      : allureVersion,
                                    properties       : [],
                                    reportBuildPolicy: 'ALWAYS',
                                    results          : [[path: 'allure-results']]
                                ])
                            }
                        }
                    }
                }
            }
        }

       stage("Set job execution result") {
           when {
               expression {
                   spinUpEnvironmentJob.result != 'SUCCESS'
               }
           }
           steps {
               script {
                   currentBuild.result = 'FAILURE'
               }
           }
       }
    }
}

private List getEnvironmentJobParameters(String action, String okapiVersion, clusterName, projectName, tenant,
                                         folio_repository, folio_branch) {
    [
        string(name: 'action', value: action),
        string(name: 'config_type', value: "development"),
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
