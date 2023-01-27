@Library('pipelines-shared-library@RANCHER-608') _

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
        stage("Run cypress tests") {
            steps {
                script {
                    def jobParameters = [
                        branch: params.branch,
                        uiUrl: uiUrl,
                        okapiUrl: okapiUrl,
                        tenant: tenant,
                        user: 'diku_admin',
                        password: 'admin',
                        cypressParameters: "--env grepTags=\"smoke criticalPth\",grepFilterSpecs=true",
                        customBuildName: JOB_BASE_NAME,
                        timeout: '6',
                        testrailProjectID: '14',
                        testrailRunID: '2108'
                    ]
                    cypressStages(jobParameters)
                }
            }
        }

//        stage("Run karate tests") {
//            steps {
//                script {
//                    def jobParameters = [
//                        string(name: 'branch', value: params.branch),
//                        string(name: 'threadsCount', value: "4"),
//                        string(name: 'modules', value: ""),
//                        string(name: 'okapiUrl', value: okapiUrl),
//                        string(name: 'tenant', value: 'supertenant'),
//                        string(name: 'adminUserName', value: 'super_admin'),
//                        password(name: 'adminPassword', value: 'admin'),
//                        string(name: 'prototypeTenant', value: prototypeTenant)
//                    ]
//
//                    karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
//                }
//            }
//        }
//
//        stage("Parallel Karate results") {
//            parallel {
//                stage("Collect test results") {
//                    stages {
//                        stage("Copy downstream job artifacts") {
//                            steps {
//                                script {
//                                    def jobNumber = karateTestsJob.number
//                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "cucumber.zip")
//                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "junit.zip")
//                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "karate-summary.zip")
//                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "teams-assignment.json")
//
//                                    unzip zipFile: "cucumber.zip", dir: "results"
//                                    unzip zipFile: "junit.zip", dir: "results"
//                                    unzip zipFile: "karate-summary.zip", dir: "results"
//                                }
//                            }
//                        }
//
//                        stage('Publish tests report Karate') {
//                            steps {
//                                script {
//                                    cucumber buildStatus: "UNSTABLE",
//                                        fileIncludePattern: "results/**/target/karate-reports*/*.json",
//                                        sortingMethod: "ALPHABETICAL"
//
//                                    junit testResults: 'results/**/target/karate-reports*/*.xml'
//                                }
//                            }
//                        }
//
//                        stage("Collect execution results") {
//                            steps {
//                                script {
//                                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("results/**/target/karate-reports*/karate-summary-json.txt")
//
//                                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }
}
