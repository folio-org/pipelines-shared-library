@Library('pipelines-shared-library') _

import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment

def allureVersion = "2.17.2"

def clusterName = "folio-testing"
def projectName = "spring"
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

def prototypeTenant = "diku"

def karateTestsJobName = "/Testing/Karate tests"
def karateTestsJob

KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0]

pipeline {
    agent { label 'jenkins-agent-java11' }

    triggers {
        cron('H 20 * * *')
    }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Cypress tests repository branch to checkout')
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
//        stage("Destroy environment before Cypress") {
//            steps {
//                script {
//                    def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, clusterName,
//                        projectName, tenant, folio_repository, folio_branch)
//
//                    tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
//                }
//            }
//        }
//
//        stage("Create environment before Karate") {
//            steps {
//                script {
//                    def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, clusterName,
//                        projectName, tenant, folio_repository, folio_branch)
//
//                    spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
//                }
//            }
//        }

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
                        password(name: 'password', value: 'admin'),
                        string(name: 'cypressParameters', value: "--env grepTags=\"smoke criticalPth\",grepFilterSpecs=true"),
                        string(name: 'customBuildName', value: JOB_BASE_NAME),
                        string(name: 'timeout', value: '6'),
                        string(name: 'testrailProjectID', value: '14'),
                        string(name: 'testrailRunID', value: '2108')
                    ]

                    cypressTestsJob = build job: cypressTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel Cypress results") {
            parallel {
                stage("Collect test results") {
//                    when {
//                        expression {
//                            spinUpEnvironmentJob.result == 'SUCCESS'
//                        }
//                    }
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

                        stage('Publish tests report Cypress') {
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

//        stage("Destroy environment before Karate") {
//            steps {
//                script {
//                    def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, clusterName,
//                        projectName, tenant, folio_repository, folio_branch)
//
//                    tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
//                }
//            }
//        }
//
//        stage("Create environment before Karate") {
//            steps {
//                script {
//                    def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, clusterName,
//                        projectName, tenant, folio_repository, folio_branch)
//
//                    spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
//                }
//            }
//        }

        stage("Run karate tests") {
//            when {
//                expression {
//                    spinUpEnvironmentJob.result == 'SUCCESS'
//                }
//            }
            steps {
                script {
                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'threadsCount', value: "4"),
                        string(name: 'modules', value: ""),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: 'supertenant'),
                        string(name: 'adminUserName', value: 'super_admin'),
                        password(name: 'adminPassword', value: 'admin'),
                        string(name: 'prototypeTenant', value: prototypeTenant)
                    ]

                    karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel Karate results") {
            parallel {
                stage("Collect test results") {
//                    when {
//                        expression {
//                            spinUpEnvironmentJob.result == 'SUCCESS'
//                        }
//                    }
                    stages {
                        stage("Copy downstream job artifacts") {
                            steps {
                                script {
                                    def jobNumber = karateTestsJob.number
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "cucumber.zip")
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "junit.zip")
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "karate-summary.zip")
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "teams-assignment.json")

                                    unzip zipFile: "cucumber.zip", dir: "results"
                                    unzip zipFile: "junit.zip", dir: "results"
                                    unzip zipFile: "karate-summary.zip", dir: "results"
                                }
                            }
                        }

                        stage('Publish tests report Karate') {
                            steps {
                                script {
                                    cucumber buildStatus: "UNSTABLE",
                                        fileIncludePattern: "results/**/target/karate-reports*/*.json",
                                        sortingMethod: "ALPHABETICAL"

                                    junit testResults: 'results/**/target/karate-reports*/*.xml'
                                }
                            }
                        }

                        stage("Collect execution results") {
                            steps {
                                script {
                                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("results/**/target/karate-reports*/karate-summary-json.txt")

                                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
                                }
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
