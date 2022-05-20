@Library('pipelines-shared-library@RANCHER-252') _

import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.folio.version.VersionConstants
import org.folio.version.semantic.Order
import org.folio.version.semantic.SemanticVersionComparator
import org.jenkinsci.plugins.workflow.libs.Library

def projectName = "test"
def okapiUrl = "https://${projectName}-okapi.ci.folio.org"
def tenant = "supertenant"
def spinUpEnvironmentJobName = "/Rancher/Project_300"
def spinUpEnvironmentJob
def tearDownEnvironmentJob
def karateTestsJobName = "/Testing/Karate tests"
def karateTestsJob
KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

List<String> versions = Eval.me(jobsParameters.getOkapiVersions())
String okapiVersion = versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]
String uiImageVersion = Eval.me("project_name", "karate", jobsParameters.getUIImagesList())[0]

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'RANCHER-252', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
        stage("Create environment") {
            steps {
                script {
                    def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, uiImageVersion, projectName, tenant)

                    spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Run karate tests") {
            when {
                expression {
                    spinUpEnvironmentJob.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'threadsCount', value: "4"),
                        string(name: 'modules', value: ""),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'adminUserName', value: 'super_admin'),
                        password(name: 'adminPassword', value: 'admin'),
                        string(name: 'prototypeTenant', value: 'diku')
                    ]

                    karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel") {
            parallel {
                stage("Destroy environment") {
                    steps {
                        script {
                            input "Destroy?"
                            def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, uiImageVersion, projectName, tenant)

                            tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
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
                                    def jobNumber = 91 //karateTestsJob.number
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

                        stage('Publish tests report') {
                            steps {
                                script {
                                    cucumber buildStatus: "UNSTABLE",
                                        fileIncludePattern: "results/**/target/karate-reports*/*.json",
                                        sortingMethod: "ALPHABETICAL"

                                    junit testResults: 'results/**/target/karate-reports*/*.xml'
                                }
                            }
                        }

//                        stage("Collect execution results") {
//                            steps {
//                                script {
//                                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("results/**/target/karate-reports*/karate-summary-json.txt")
//
//                                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
//                                }
//                            }
//                        }
//
//                        stage("Parse teams assignment") {
//                            steps {
//                                script {
//                                    def jsonContents = readJSON file: "teams-assignment.json"
//                                    teamAssignment = new TeamAssignment(jsonContents)
//                                }
//                            }
//                        }
//
//
//                        stage("Send slack notifications") {
//                            steps {
//                                script {
//                                    karateTestUtils.sendSlackNotification(karateTestsExecutionSummary, teamAssignment)
//                                }
//                            }
//                        }
//
//                        stage("Sync jira tickets") {
//                            steps {
//                                script {
//                                    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
//                                }
//                            }
//                        }
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

private List getEnvironmentJobParameters(String action, String okapiVersion, String uiImageVersion, projectName, tenant) {
    [
        string(name: 'action', value: action),
        string(name: 'rancher_cluster_name', value: "folio-test"),
        string(name: 'project_name', value: projectName),
        string(name: 'okapi_version', value: okapiVersion),
        string(name: 'folio_repository', value: "complete"),
        string(name: 'folio_branch', value: "snapshot"),
        string(name: 'stripes_image_tag', value: uiImageVersion),
        string(name: 'tenant_id', value: tenant),
        string(name: 'tenant_name', value: "Karate tenant"),
        string(name: 'tenant_description', value: "Karate tests main tenant"),
        booleanParam(name: 'load_reference', value: true),
        booleanParam(name: 'load_sample', value: true)
    ]
}
