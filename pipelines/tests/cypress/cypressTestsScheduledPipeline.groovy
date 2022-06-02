package tests.karate

@Library('pipelines-shared-library@RANCHER-252') _


import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

def allureVersion = "2.17.2"

def clusterName = "folio-testing"
def projectName = "karate"
def folio_repository = "complete"
def folio_branch = "snapshot"
def uiUrl = "https://${clusterName}-${projectName}.ci.folio.org"
def okapiUrl = "https://${clusterName}-${projectName}-okapi.ci.folio.org"
def tenant = "diku"

def spinUpEnvironmentJobName = "/Rancher/Project"
def spinUpEnvironmentJob
def tearDownEnvironmentJob
def cypressTestsJobName = "/Testing/Cypress tests"
def cypressTestsJob

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0] //versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]
String uiImageVersion = tools.eval(jobsParameters.getUIImagesList(), ["project_name": "cypress"])[0]

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Cypress tests repository branch to checkout')
    }

    options {
        disableConcurrentBuilds()
    }

    stages {
        stage("Create environment") {
            steps {
                script {
                    def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, uiImageVersion, clusterName,
                        projectName, tenant, folio_repository, folio_branch)

                    //spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Run cypress tests") {
//            when {
//                expression {
//                    spinUpEnvironmentJob.result == 'SUCCESS'
//                }
//            }
            steps {
                script {
                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'uiUrl', value: uiUrl),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'user', value: 'diku_admin'),
                        string(name: 'password', value: 'admin'),
                        string(name: 'cypressParameters', value: "--spec cypress/integration/finance/funds/funds.search.spec.js")
                    ]

                    cypressTestsJob = build job: cypressTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel") {
            parallel {
                stage("Destroy environment") {
                    steps {
                        script {
                            def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, uiImageVersion, clusterName,
                                projectName, tenant, folio_repository, folio_branch)

                            //tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                        }
                    }
                }
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

                                    unzip zipFile: "allure-results.zip", dir: "allure-results"
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

//        stage("Set job execution result") {
//            when {
//                expression {
//                    spinUpEnvironmentJob.result != 'SUCCESS'
//                }
//            }
//            steps {
//                script {
//                    currentBuild.result = 'FAILURE'
//                }
//            }
//        }
    }
}

private List getEnvironmentJobParameters(String action, String okapiVersion, String uiImageVersion, clusterName, projectName, tenant,
                                         folio_repository, folio_branch) {
    [
        string(name: 'action', value: action),
        string(name: 'rancher_cluster_name', value: clusterName),
        string(name: 'project_name', value: projectName),
        string(name: 'okapi_version', value: okapiVersion),
        string(name: 'folio_repository', value: folio_repository),
        string(name: 'folio_branch', value: folio_branch),
        string(name: 'stripes_image_tag', value: uiImageVersion),
        string(name: 'tenant_id', value: tenant),
        string(name: 'tenant_name', value: "Cypress tenant"),
        string(name: 'tenant_description', value: "Cypress tests main tenant"),
        booleanParam(name: 'load_reference', value: true),
        booleanParam(name: 'load_sample', value: true)
    ]
}
