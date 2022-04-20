@Library('pipelines-shared-library@RANCHER-248') _


import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.jenkinsci.plugins.workflow.libs.Library

def okapiUrl, tenant, user, password
def karateTestsJobName = "/Testing/Karate tests"
def karateTestsJob
KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'RANCHER-239', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
        stage("Create environment") {
            steps {
                script {
                    okapiUrl = 'https://folio-snapshot-okapi.dev.folio.org:443'
                    tenant = 'supertenant'
                    user = 'testing_admin'
                    password = 'admin'

//                    okapiUrl = 'https://ptf-perf-okapi.ci.folio.org'
//                    tenant = 'fs09000000'
//                    user = 'folio'
//                    password = 'folio'

//                    call job to setup env (https://issues.folio.org/browse/RANCHER-12)
//                    def jobParameters = [
//                        string(name: 'branch', value: params.branch),
//                        string(name: 'threadsCount', value: "4"),
//                        string(name: 'okapiUrl', value: okapiUrl),
//                        string(name: 'tenant', value: tenant),
//                        string(name: 'adminUserName', value: user),
//                        string(name: 'adminPassword', value: password)
//                    ]
//
//                    def karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Run karate tests") {
            steps {
                script {
                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'threadsCount', value: "4"),
                        string(name: 'modules', value: ""),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'adminUserName', value: user),
                        string(name: 'adminPassword', value: password)
                    ]

                    //karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Copy downstream job artifacts") {
            steps {
                script {
                    def jobNumber = 68 // karateTestsJob.number
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "cucumber.zip")
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "junit.zip")
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "karate-summary.zip")
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "teams-assignment.json")

                    unzip zipFile: "cucumber.zip", dir: "cucumber"
                    unzip zipFile: "junit.zip", dir: "junit"
                    unzip zipFile: "karate-summary.zip", dir: "karate-summary"
                }
            }
        }

        stage('Publish tests report') {
            steps {
                script {
                    cucumber buildStatus: "UNSTABLE",
                        fileIncludePattern: "cucumber/**/target/karate-reports*/*.json"

                    junit testResults: 'junit/**/target/karate-reports*/*.xml'
                }
            }
        }

        stage("Collect execution results") {
            steps {
                script {
                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("karate-summary/**/target/karate-reports*/karate-summary-json.txt")

                    node("master") {
                        def targetFolder = "${WORKSPACE}/${env.BUILD_NUMBER}"
                        sh "mkdir -p '${targetFolder}'"

                        def jobFolder = ""
                        env.JOB_NAME.split("/").each { entry ->
                            jobFolder += "/jobs/${entry}"
                        }
                        dir ("${JENKINS_HOME}${jobFolder}/builds/${env.BUILD_NUMBER}") {
                            sh "cp -r cucumber-html-reports '${targetFolder}'"
                        }
                        sh "ls -lah '${targetFolder}/cucumber-html-reports'"
                        stash name: "cucumber-reports", includes: "${targetFolder}/cucumber-html-reports/*"
//
//                        def zipFileName = "cucumber-html-reports2.zip"
//                        sh "ls '${JENKINS_HOME}${jobFolder}/builds/${env.BUILD_NUMBER}/cucumber-html-reports'"
//                        zip zipFile: zipFileName, glob: "${JENKINS_HOME}${jobFolder}/builds/${env.BUILD_NUMBER}/cucumber-html-reports/*.*"
//                        archiveArtifacts allowEmptyArchive: true, artifacts: zipFileName, fingerprint: true, defaultExcludes: false
                    }

                    dir("cucumber-html-reports") {
                        unstash name: "cucumber-reports"

                        sh "ls -lah"
                    }


                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary, "cucumber-html-reports")
                }
            }
        }

        stage("Parse teams assignment") {
            steps {
                script {
                    def jsonContents = readJSON file: "teams-assignment.json"
                    teamAssignment = new TeamAssignment(jsonContents)
                }
            }
        }


        stage("Send slack notifications") {
            steps {
                script {
                    echo "bla"
                    //karateTestUtils.sendSlackNotification(karateTestsExecutionSummary, teamAssignment)
                }
            }
        }

        stage("Create jira tickets") {
            steps {
                script {
                    echo "bla"
                    //karateTestUtils.createJiraTickets(karateTestsExecutionSummary, teamAssignment)
                }
            }
        }
    }
}
