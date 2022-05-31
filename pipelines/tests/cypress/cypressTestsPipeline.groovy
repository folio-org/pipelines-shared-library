package tests.cypress

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-291') _

def code = """
def gettags = ("git ls-remote -t -h https://github.com/folio-org/stripes-testing.git").execute()
return gettags.text.readLines().collect {
  it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\\\^\\\\{\\\\}", '')
}"""

// Variables
def cypressBrowsersVersion = "node16.14.2-slim-chrome100-ff99-edge"
def allureVersion = "2.17.2"
def currentUID
def currentGID

properties([
    parameters([
        [
            $class      : 'CascadeChoiceParameter',
            choiceType  : 'PT_SINGLE_SELECT',
            description : 'Cypress tests repository branch',
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
        string(name: 'uiUrl', description: 'Folio UI url', defaultValue: "https://cypress.ci.folio.org"),
        string(name: 'okapiUrl', description: 'Okapi url', defaultValue: "https://cypress-okapi.ci.folio.org"),
        string(name: 'tenant', description: 'Tenant to execute tests', defaultValue: "diku"),
        string(name: 'user', description: 'User for test', defaultValue: "diku_admin"),
        password(name: 'password', description: 'Password for test', defaultValue: "admin"),
        string(name: 'cypressParameters', description: 'Cypress execution parameters', defaultValue: "--env grepTags=smoke,grepFilterSpecs=true"),
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
                            userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/stripes-testing.git"]]
                        ])
                    }
                }
            }
        }

        stage('Run cypress tests') {
            steps {
                script {
                    currentUID = sh returnStdout: true, script: 'id -u'
                    currentGID = sh returnStdout: true, script: 'id -g'

                    docker.image("cypress/browsers:${cypressBrowsersVersion}").inside('-u 0:0 --entrypoint=') {
                        stage('Execute cypress tests') {
                            sh """
                            export CYPRESS_BASE_URL=${params.uiUrl}
                            export CYPRESS_OKAPI_HOST=${params.okapiUrl}
                            export CYPRESS_OKAPI_TENANT=${params.tenant}
                            export CYPRESS_diku_login=${params.user}
                            export CYPRESS_diku_password=${params.password}

                            yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}
                            yarn install
                            yarn add @interactors/html --dev
                            yarn add @interactors/html @interactors/with-cypress --dev

                            npx cypress run --headless ${params.cypressParameters} || true
                            chown -R ${currentUID.trim()}:${currentGID.trim()} *
                        """
                        }
                    }
                }
            }
        }

        stage("Reports") {
            stages {
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
}
