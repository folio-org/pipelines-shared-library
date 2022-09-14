package tests.karate

@Library('pipelines-shared-library@RANCHER-437') _

import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

def clusterName = "folio-testing"
def projectName = "karate"
def folio_repository = "platform-complete"
def folio_branch = "snapshot"
def okapiUrl = "https://${clusterName}-${projectName}-okapi.ci.folio.org"
def prototypeTenant = "diku"

def spinUpEnvironmentJobName = "/Rancher/Project"
def spinUpEnvironmentJob
def tearDownEnvironmentJob
def karateTestsJobName = "/Testing/Karate tests"
def karateTestsJob

KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0] //versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]

pipeline {
    agent { label 'jenkins-agent-java11' }

    // triggers {
    //     cron('H 3 * * *')
    // }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
        // stage("Create environment") {
        //     steps {
        //         script {
        //             def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, clusterName,
        //                 projectName, prototypeTenant, folio_repository, folio_branch)

        //             spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
        //         }
        //     }
        // }

        // stage("Run karate tests") {
        //     when {
        //         expression {
        //             spinUpEnvironmentJob.result == 'SUCCESS'
        //         }
        //     }
        //     steps {
        //         script {
        //             def jobParameters = [
        //                 string(name: 'branch', value: params.branch),
        //                 string(name: 'threadsCount', value: "4"),
        //                 string(name: 'modules', value: ""),
        //                 string(name: 'okapiUrl', value: okapiUrl),
        //                 string(name: 'tenant', value: 'supertenant'),
        //                 string(name: 'adminUserName', value: 'super_admin'),
        //                 password(name: 'adminPassword', value: 'admin'),
        //                 string(name: 'prototypeTenant', value: prototypeTenant)
        //             ]

        //             sleep time: 60, unit: 'MINUTES'
        //             karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
        //         }
        //     }
        // }

        stage("Parallel") {
            parallel {
                // stage("Destroy environment") {
                //     steps {
                //         script {
                //             def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, clusterName,
                //                 projectName, prototypeTenant, folio_repository, folio_branch)

                //             tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                //         }
                //     }
                // }

                stage("Collect test results") {
                    // when {
                    //     expression {
                    //         spinUpEnvironmentJob.result == 'SUCCESS'
                    //     }
                    // }
                    stages {
                        stage("Copy downstream job artifacts") {
                            steps {
                                script {
                                    def jobNumber = "346"
                                    // def jobNumber = "359"
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

                        stage("Collect execution results") {
                            steps {
                                script {
                                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("results/**/target/karate-reports*/karate-summary-json.txt")

                                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
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

                        stage("Sync jira tickets") {
                            steps {
                                script {
                                    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
                                }
                            }
                        }

                        stage("Send slack notifications") {
                            steps {
                                script {
                                    karateTestUtils.getExistingJiraIssuesByTeam()
                                    karateTestUtils.sendSlackNotification(karateTestsExecutionSummary, teamAssignment)
                                }
                            }
                        }
                    }
                }
            }
        }

        // stage("Set job execution result") {
        //     when {
        //         expression {
        //             spinUpEnvironmentJob.result != 'SUCCESS'
        //         }
        //     }
        //     steps {
        //         script {
        //             currentBuild.result = 'FAILURE'
        //         }
        //     }
        // }
    }
}

private List getEnvironmentJobParameters(String action, String okapiVersion, clusterName, projectName, tenant,
                                         folio_repository, folio_branch) {
    [
        string(name: 'action', value: action),
        string(name: 'env_config', value: "development"),
        string(name: 'rancher_cluster_name', value: clusterName),
        string(name: 'rancher_project_name', value: projectName),
        string(name: 'okapi_version', value: okapiVersion),
        booleanParam(name: 'build_ui', value: true),
        booleanParam(name: 'enable_modules', value: true),
        string(name: 'folio_repository', value: folio_repository),
        string(name: 'folio_branch', value: folio_branch),
        string(name: 'tenant_id', value: tenant),
        string(name: 'tenant_name', value: "Karate tenant"),
        string(name: 'tenant_description', value: "Karate tests main tenant"),
        booleanParam(name: 'load_reference', value: true),
        booleanParam(name: 'load_sample', value: true),
        booleanParam(name: 'pg_embedded', value: true),
        booleanParam(name: 'kafka_embedded', value: true),
        booleanParam(name: 'es_embedded', value: true),
        booleanParam(name: 's3_embedded', value: true)
    ]
}
