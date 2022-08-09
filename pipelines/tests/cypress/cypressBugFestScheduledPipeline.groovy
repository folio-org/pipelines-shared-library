@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

def allureVersion = "2.17.2"

def uiUrl = "https://bugfest-mg-aqa.int.aws.folio.org"
def okapiUrl = "https://okapi-bugfest-mg-aqa.int.aws.folio.org"
def tenant = "fs09000003"

def cypressTestsJobName = "/Testing/Cypress tests"
def cypressTestsJob

pipeline {
    agent { label 'jenkins-agent-java11' }

    triggers {
        cron('H 3 * * 1-5')
    }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'branch', defaultValue: 'mg-tests-run', description: 'Cypress tests repository branch to checkout')
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
                        string(name: 'cypressParameters', value: "--env grepTags=smoke,grepFilterSpecs=true")
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
