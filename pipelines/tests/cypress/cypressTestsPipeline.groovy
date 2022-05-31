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

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'baseUrl', description: 'Choose what you want', defaultValue: "https://cypress.ci.folio.org")
        string(name: 'OKAPI_HOST', description: 'Choose what you want', defaultValue: "https://cypress-okapi.ci.folio.org")
        string(name: 'OKAPI_TENANT', description: 'Choose what you want', defaultValue: "diku")
        string(name: 'diku_login', description: 'Choose what you want', defaultValue: "diku_admin")
        password(name: 'diku_password', description: 'Choose what you want', defaultValue: "admin")
        string(name: 'run_param', description: 'Choose what you want', defaultValue: "--env grepTags=smoke,grepFilterSpecs=true")
        [$class      : 'CascadeChoiceParameter',
         choiceType  : 'PT_SINGLE_SELECT',
         description : 'Choose what stripes-testing branch to run from',
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
        ]
    }

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
                            export CYPRESS_BASE_URL=${params.baseUrl}
                            export CYPRESS_OKAPI_HOST=${params.OKAPI_HOST}
                            export CYPRESS_OKAPI_TENANT=${params.OKAPI_TENANT}
                            export CYPRESS_diku_login=${params.diku_login}
                            export CYPRESS_diku_password=${params.diku_password}
                            yarn config set @folio:registry https://repository.folio.org/repository/npm-folioci/
                            yarn install
                            yarn add @interactors/html --dev
                            yarn add @interactors/html @interactors/with-cypress --dev
                            npx cypress run --headless ${params.run_param} || true
                            chown -R ${currentUID.trim()}:${currentGID.trim()} *
                        """
                        }
                    }
                }
            }
        }

        stage {
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
