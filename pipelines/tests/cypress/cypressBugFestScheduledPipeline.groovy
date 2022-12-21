@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

def allureVersion = "2.17.2"

def uiUrl = "https://bugfest-nolana-aqa.int.aws.folio.org"
def okapiUrl = "https://okapi-bugfest-nolana-aqa.int.aws.folio.org"
def tenant = "fs09000003"

def cypressTestsJobName = "/Testing/Cypress tests"
def cypressTestsJob

pipeline {
    agent { label 'jenkins-agent-java11' }

    // Stopped by https://issues.folio.org/browse/RANCHER-591
    // triggers {
    //     cron('H 1 * * 1-6')
    // }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'branch', defaultValue: 'nolana', description: 'Cypress tests repository branch to checkout')
    }

    stages {
        stage("Run cypress tests") {
            steps {
                script {
                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'uiUrl', value: uiUrl),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'user', value: 'folio-aqa'),
                        password(name: 'password', value: 'Folio-aqa1'),
                        string(name: 'cypressParameters', value: "--env grepTags=\"smoke criticalPth\",grepFilterSpecs=true"),
                        string(name: 'customBuildName', value: JOB_BASE_NAME),
                        string(name: 'timeout', value: '8'),
                        string(name: 'testrailProjectID', value: '14'),
                        string(name: 'testrailRunID', value: '2099')
                    ]

                    cypressTestsJob = build job: cypressTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel") {
            parallel {
                stage("Collect test results") {
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
    }
}
