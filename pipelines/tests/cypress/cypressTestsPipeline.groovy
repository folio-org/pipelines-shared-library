package tests.cypress

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-291') _

def cypressRepoitoryUrl = "${Constants.FOLIO_GITHUB_URL}/stripes-testing.git"

def code = """
def gettags = ("git ls-remote -t -h ${cypressRepoitoryUrl}").execute()
return gettags.text.readLines().collect {
  it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\\\^\\\\{\\\\}", '')
}"""

// Variables
def cypressImageVersion = "9.7.0"
def browserName = "chrome"
def allureVersion = "2.17.2"
def currentUID
def currentGID

properties([
    parameters([
        [
            $class      : 'CascadeChoiceParameter',
            choiceType  : 'PT_SINGLE_SELECT',
            description : 'Cypress tests repository branch to checkout',
            filterLength: 1,
            filterable  : false,
            name        : 'branch',
            script      : [
                $class        : 'GroovyScript',
                fallbackScript: [
                    classpath: [],
                    sandbox  : false,
                    script   : 'return ["error"]'
                ],
                script        : [classpath: [],
                                 sandbox  : false,
                                 script   : code
                ]
            ]
        ],
        string(name: 'uiUrl', defaultValue: "https://folio-testing-karate.ci.folio.org", description: 'Target environment UI URL'),
        string(name: 'okapiUrl', defaultValue: "https://folio-testing-karate-okapi.ci.folio.org", description: 'Target environment OKAPI URL'),
        string(name: 'tenant', defaultValue: "diku", description: 'Tenant name'),
        string(name: 'user', defaultValue: "diku_admin", description: 'User name'),
        password(name: 'password', defaultValue: "admin", description: 'User password'),
        string(name: 'cypressParameters', defaultValue: "--env grepTags=smoke,grepFilterSpecs=true", description: 'Cypress execution parameters'),
    ])
])

pipeline {
    agent { label 'jenkins-agent-java11' }

    stages {
        stage('Checkout') {
            steps {
                script {
                    sshagent(credentials: [Constants.GITHUB_CREDENTIALS_ID]) {
                        checkout([
                            $class           : 'GitSCM',
                            branches         : [[name: "*/${params.branch}"]],
                            extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                                  disableSubmodules  : false,
                                                                  parentCredentials  : false,
                                                                  recursiveSubmodules: true,
                                                                  reference          : '',
                                                                  trackingSubmodules : false]],
                            userRemoteConfigs: [[url: cypressRepoitoryUrl]]
                        ])
                    }
                }
            }
        }

        stage('Cypress tests execution') {
            steps {
                script {
//                    currentUID = sh returnStdout: true, script: 'id -u'.trim()
//                    currentGID = sh returnStdout: true, script: 'id -g'.trim()
//
//                    //0:0
//                    // -u ${currentUID}:${currentGID}
//
//                    docker run - it - v % cd %: /e2e -w / e2e cypress / included: 9.7 .0 run-- env grepTags = smoke, grepFilterSpecs = true-- browser chrome

                    docker
                        .image("cypress/included:${cypressImageVersion}")
                        .inside("--entrypoint=") {
                            stage('Build tests') {
                                sh """
                                    yarn config set @folio:registry https://repository.folio.org/repository/npm-folioci/
                                    yarn install
                                """
                            }

                            stage('Run cypress tests') {
                                sh """
                                    export CYPRESS_BASE_URL=${params.uiUrl}
                                    export CYPRESS_OKAPI_HOST=${params.okapiUrl}
                                    export CYPRESS_OKAPI_TENANT=${params.tenant}
                                    export CYPRESS_diku_login=${params.user}
                                    export CYPRESS_diku_password=${params.password}

                                    cypress run --headless --browser ${browserName} ${params.cypressParameters}
                                """
                            }
                        }
                }
            }

            stage('Generate tests report') {
                steps {
                    script {
                        def allure_home = tool name: allureVersion, type: 'allure'
                        sh "${allure_home}/bin/allure generate --clean"
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

            stage('Archive artifacts') {
                steps {
                    archiveArtifacts artifacts: 'allure-results/*'
                }
            }
        }
    }
}


