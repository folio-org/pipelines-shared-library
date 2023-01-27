import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.transform.Field
@Library('pipelines-shared-library@RANCHER-608') _

@Field def cypressImageVersion

def call(params) {
    stage('Checkout Cypress repo') {
        steps {
            script {
                def customBuildName = params.customBuildName?.trim() ? params.customBuildName + '.' + env.BUILD_ID : env.BUILD_ID
                buildName customBuildName
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
                        userRemoteConfigs: [[url: Constants.CYPRESS_REPOSITORY_URL]]
                    ])
                }
            }
        }
    }
    stage('Cypress tests Image version') {
        steps {
            script {
                def packageJson = readJSON(text: readFile("${workspace}/package.json"))
                cypressImageVersion = packageJson.dependencies.cypress
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
            sh "yarn add -D cypress-testrail-simple"
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
            def browserName = "chrome"
            script {
                ansiColor('xterm') {
                    timeout(time: "${params.timeout}", unit: 'HOURS') {
                        catchError (buildResult: 'FAILURE', stageResult: 'FAILURE') {
                            if (params.testrailRunID && params.testrailProjectID) {
                                // Run with TesTrail Integration
                                env.TESTRAIL_HOST = "https://foliotest.testrail.io"
                                env.TESTRAIL_PROJECTID = "${params.testrailProjectID}"
                                env.TESTRAIL_RUN_ID = "${params.testrailRunID}"
                                env.CYPRESS_allureReuseAfterSpec = "true"
                                println "Test results will be send to TestRail. (ProjectID: ${params.testrailProjectID}, RunID: ${params.testrailRunID})"
                                withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
                                    sh "cypress run --headless --browser ${browserName} ${params.cypressParameters}"
                                }
                            } else {
                                sh "cypress run --headless --browser ${browserName} ${params.cypressParameters}"
                            }
                        }
                    }
                }
            }
        }
    }

    stage('Generate tests report') {
        steps {
            script {
                def allure_home = tool type: 'allure', name: Constants.CYPRESS_ALLURE_VERSION
                sh "${allure_home}/bin/allure generate --clean"
            }
        }
    }

    stage('Publish tests report') {
        steps {
            allure([
                includeProperties: false,
                jdk              : '',
                commandline      : Constants.CYPRESS_ALLURE_VERSION,
                properties       : [],
                reportBuildPolicy: 'ALWAYS',
                results          : [[path: 'allure-results']]
            ])
        }
    }

    stage('Archive artifacts') {
        steps {
            script {
                zip zipFile: "allure-results.zip", glob: "allure-results/*"

                archiveArtifacts allowEmptyArchive: true, artifacts: "allure-results.zip", fingerprint: true, defaultExcludes: false
            }
        }
    }
}
