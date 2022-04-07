@Library('pipelines-shared-library@RANCHER-248') _

import org.folio.Constants
import org.folio.client.jira.JiraClient
import org.folio.karate.results.KarateTestsResult
import org.folio.karate.teams.TeamAssignment
import org.jenkinsci.plugins.workflow.libs.Library

def okapiUrl, tenant, user, password
def karateTestsJobName = "/Testing/Karate tests"
def karateTestsJob
KarateTestsResult karateTestsResult

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

                    karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Copy downstream job artifacts") {
            steps {
                script {
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${karateTestsJob.number}"), filter: "cucumber.zip")
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${karateTestsJob.number}"), filter: "junit.zip")
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${karateTestsJob.number}"), filter: "karate-summary.zip")
                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${karateTestsJob.number}"), filter: "teams-assignment.json")

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
                    karateTestsResult = karateTestUtils.collectTestsResults("karate-summary/**/target/karate-reports*/karate-summary-json.txt")
                }
            }
        }

        stage("Send slack notifications") {
            steps {
                script {
                    def jsonContents = readJSON file: "teams-assignment.json"
                    def teamAssignment = new TeamAssignment(jsonContents)

                    karateTestUtils.sendSlackNotification(karateTestsResult, teamAssignment)
                }
            }
        }

        stage("Create jira tickets") {
            steps {
                script {
                    println "create jira"
//                    withCredentials([
//                        usernamePassword(credentialsId: Constants.JIRA_CREDENTIALS_ID, usernameVariable: 'jiraUsername', passwordVariable: 'jiraPassword')
//                    ]) {
//                        JiraClient jiraClient = new JiraClient(Constants.FOLIO_JIRA_URL, jiraUsername, jiraPassword)
//
//                        jiraClient.createJiraTicket "KRD",
//                            "Bug",
//                            "Failed test ",
//                            [Description       : "Description long",
//                             Priority          : "P1",
//                             //Labels            : ["reviewed"],
//                             "Development Team": "Team"]
//                    }
                }
            }
        }
    }
}
