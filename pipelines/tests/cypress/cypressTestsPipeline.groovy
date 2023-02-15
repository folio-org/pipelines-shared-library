import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import hudson.util.Secret

@Library('pipelines-shared-library') _

def cypressRepositoryUrl = "${Constants.FOLIO_GITHUB_URL}/stripes-testing.git"

def testsBranchesScript = """
def gettags = ("git ls-remote -t -h ${cypressRepositoryUrl}").execute()
return gettags.text.readLines().collect {
  it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\\\^\\\\{\\\\}", '')
}"""

def cypressImageVersion
def allureVersion = "2.17.2"
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
        string(name: 'uiUrl', defaultValue: "https://folio-testing-cypress-diku.ci.folio.org", description: 'Target environment UI URL', trim: true),
        string(name: 'okapiUrl', defaultValue: "https://folio-testing-cypress-okapi.ci.folio.org", description: 'Target environment OKAPI URL', trim: true),
        string(name: 'tenant', defaultValue: "diku", description: 'Tenant name'),
        string(name: 'user', defaultValue: "diku_admin", description: 'User name'),
        password(name: 'password', defaultValueAsSecret: Secret.fromString('admin'), description: 'User password'),
        jobsParameters.agents(),
        //string(name: 'cypressParameters', defaultValue: "--spec cypress/integration/finance/funds/funds.search.spec.js", description: 'Cypress execution parameters'),
        string(name: 'cypressParameters', defaultValue: "--env grepTags=smoke,grepFilterSpecs=true", description: 'Cypress execution parameters'),
        string(name: 'customBuildName', defaultValue: "", description: 'Custom name for build'),
        string(name: 'timeout', defaultValue: "4", description: 'Custom timeout for build. Set in hours'),
        string(name: 'testrailProjectID', defaultValue: "", description: 'To enable TestRail integration, enter ProjectID from TestRail, ex. 22', trim: true),
        string(name: 'testrailRunID', defaultValue: "", description: 'To enable TestRail integration, enter RunID from TestRail, ex. 2048', trim: true),
    ])
])

def customBuildName = params.customBuildName?.trim() ? params.customBuildName + '.' + env.BUILD_ID : env.BUILD_ID

pipeline {
    agent { label "$params.agent" }

    stages {
        stage('Checkout') {
            steps {
                script {
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
                            userRemoteConfigs: [[url: cypressRepositoryUrl]]
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
                CYPRESS_API_URL = "https://folio-testing-sc-director.ci.folio.org/api"
            }
            steps {
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
                                        sh "yarn global add cy2@latest && \$HOME/.yarn/bin/cy2 run --config projectId=stripes --parallel --record --key somekey --ci-build-id ${BUILD_NUMBER} --browser ${browserName} ${params.cypressParameters}"
                                    }
                                } else {
                                    sh "yarn global add cy2@latest && \$HOME/.yarn/bin/cy2 run --config projectId=stripes --parallel --record --key somekey --ci-build-id ${BUILD_NUMBER} --browser ${browserName} ${params.cypressParameters}"
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
                    def allure_home = tool type: 'allure', name: allureVersion
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
                script {
                    zip zipFile: "allure-results.zip", glob: "allure-results/*"

                    archiveArtifacts allowEmptyArchive: true, artifacts: "allure-results.zip", fingerprint: true, defaultExcludes: false
                }
            }
        }
    }
}
