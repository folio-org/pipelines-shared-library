package tests.cypress

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-291') _

def cypressRepositoryUrl = "${Constants.FOLIO_GITHUB_URL}/stripes-testing.git"

def testsBranchesScript = """
def gettags = ("git ls-remote -t -h ${cypressRepositoryUrl}").execute()
return gettags.text.readLines().collect {
  it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\\\^\\\\{\\\\}", '')
}"""

def cypressImageVersion = "9.7.0"
def allureVersion = "2.18.1"

def browserName = "chrome"

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
                                 script   : testsBranchesScript
                ]
            ]
        ],
        string(name: 'uiUrl', defaultValue: "https://folio-testing-cypress.ci.folio.org", description: 'Target environment UI URL'),
        string(name: 'okapiUrl', defaultValue: "https://folio-testing-cypress-okapi.ci.folio.org", description: 'Target environment OKAPI URL'),
        string(name: 'tenant', defaultValue: "diku", description: 'Tenant name'),
        string(name: 'user', defaultValue: "diku_admin", description: 'User name'),
        password(name: 'password', defaultValue: "admin", description: 'User password'),
        string(name: 'cypressParameters', defaultValue: "--spec cypress/integration/finance/funds/funds.search.spec.js", description: 'Cypress execution parameters'),
        //string(name: 'cypressParameters', defaultValue: "--env grepTags=smoke,grepFilterSpecs=true", description: 'Cypress execution parameters'),
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
                            userRemoteConfigs: [[url: cypressRepositoryUrl]]
                        ])
                    }
                }
            }
        }

        stage('Build tests') {
            environment {
                HOME = "${pwd()}/cache"
                CYPRESS_CACHE_FOLDER = "${pwd()}/cache"
            }
            steps {
                sh "yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}"
                sh "yarn install"
            }
        }

        stage('Cypress tests execution') {
            agent {
                docker {
                    image "cypress/included:${cypressImageVersion}"
                    args '--entrypoint='
                    reuseNode true
                }
            }
            environment {
                HOME = "${pwd()}/cache"
                CYPRESS_CACHE_FOLDER = "${pwd()}/cache"

                CYPRESS_BASE_URL = "${params.uiUrl}"
                CYPRESS_OKAPI_HOST = "${params.okapiUrl}"
                CYPRESS_OKAPI_TENANT = "${params.tenant}"
                CYPRESS_diku_login = "${params.user}"
                CYPRESS_diku_password = "${params.password}"
            }
            steps {
                script {
                    ansiColor('xterm') {
                        sh "cypress run --headless --browser ${browserName} ${params.cypressParameters}"
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



