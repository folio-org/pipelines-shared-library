import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.transform.Field

@Library('pipelines-shared-library@RANCHER-768-adapt-for-kube') _

@Field def cypressImageVersion

def call(params) {
    def customBuildName = params.customBuildName?.trim() ? params.customBuildName + '.' + env.BUILD_ID : env.BUILD_ID
    buildName customBuildName
    def cypressParameters = (params.cypressParameters instanceof String) ? (1..params.numberOfWorkers).collect { params.cypressParameters } : params.cypressParameters
    def cypressWorkers = [:]
    int numberOfWorkers = params.numberOfWorkers as int ?: 1
    def resultPaths = []
    stage('Checkout Cypress repo') {
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
                    userRemoteConfigs: [[url: Constants.CYPRESS_REPOSITORY_URL]]
                ])
            }
        }
    }
    stage('Cypress tests Image version') {
        script {
            def packageJson = readJSON(text: readFile("${workspace}/package.json"))
            cypressImageVersion = packageJson.dependencies.cypress
        }
    }
    for (int workerNumber = 1; workerNumber <= numberOfWorkers; workerNumber++) {
        def currentNumber = workerNumber - 1
        cypressWorkers["CypressWorker#${workerNumber}"] = {
            podTemplate(inheritFrom: 'rancher-kube', containers: [
                containerTemplate(name: 'cypress', image: "cypress/included:${cypressImageVersion}", command: "sleep", args: "99999999")
            ]) {
                node(POD_LABEL) {
                    stage('Checkout Cypress repo') {
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
                                    userRemoteConfigs: [[url: Constants.CYPRESS_REPOSITORY_URL]]
                                ])
                            }
                        }
                    }

                    container('cypress') {
                        stage('Build tests') {
                            script {
                                env.HOME = "${pwd()}/cache"
                                env.CYPRESS_CACHE_FOLDER = "${pwd()}/cache"
                                sh "yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}"
                                sh "yarn add -D cypress-testrail-simple"
                                sh "yarn global add cy2@latest"
                                sh "yarn install"
                            }
                        }

                        stage('Cypress tests execution') {
                            script {
                                env.HOME = "${pwd()}/cache"
                                env.CYPRESS_CACHE_FOLDER = "${pwd()}/cache"
                                env.CYPRESS_BASE_URL = "${params.uiUrl}"
                                env.CYPRESS_OKAPI_HOST = "${params.okapiUrl}"
                                env.CYPRESS_OKAPI_TENANT = "${params.tenant}"
                                env.CYPRESS_diku_login = "${params.user}"
                                env.CYPRESS_diku_password = "${params.password}"
                                env.CYPRESS_API_URL = Constants.CYPRESS_SC_URL
                                env.AWS_DEFAULT_REGION = Constants.AWS_REGION
                                def browserName = "chrome"
                                ansiColor('xterm') {
                                    timeout(time: "${params.timeout}", unit: 'HOURS') {
                                        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                                            withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                                                              credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                                                              accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                                              secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                                if (params.testrailRunID && params.testrailProjectID) {
                                                    // Run with TesTrail Integration
                                                    env.TESTRAIL_HOST = "https://foliotest.testrail.io"
                                                    env.TESTRAIL_PROJECTID = "${params.testrailProjectID}"
                                                    env.TESTRAIL_RUN_ID = "${params.testrailRunID}"
                                                    env.CYPRESS_allureReuseAfterSpec = "true"
                                                    println "Test results will be send to TestRail. (ProjectID: ${params.testrailProjectID}, RunID: ${params.testrailRunID})"
                                                    withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
                                                        sh "cy2 run --config projectId=${Constants.CYPRESS_PROJECT} --key ${Constants.CYPRESS_SC_KEY} --parallel --record --ci-build-id ${customBuildName.replace(' ', '_')} --headless --browser ${browserName} ${cypressParameters[currentNumber]}"
                                                    }
                                                } else {
                                                    sh "cy2 run --config projectId=${Constants.CYPRESS_PROJECT} --key ${Constants.CYPRESS_SC_KEY} --parallel --record --ci-build-id ${customBuildName.replace(' ', '_')} --headless --browser ${browserName} ${cypressParameters[currentNumber]}"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    stage('Archive artifacts') {
                        script {
                            zip zipFile: "allure-results-${env.NODE_NAME}.zip", glob: "allure-results/*"
                            archiveArtifacts allowEmptyArchive: true, artifacts: "allure-results-${env.NODE_NAME}.zip", fingerprint: true, defaultExcludes: false
                            stash name: "allure-results-${env.NODE_NAME}", includes: "allure-results-${env.NODE_NAME}.zip"
                            resultPaths.add([path: "allure-results-${env.NODE_NAME}"])
                        }
                    }
                }
            }
        }
    }

    parallel(cypressWorkers)

    stage('Generate tests report') {
        script {
            for (path in resultPaths) {
                unstash name: path.path
                unzip zipFile: "${path.path}.zip", dir: path.path
            }
            def allure_home = tool type: 'allure', name: Constants.CYPRESS_ALLURE_VERSION
            sh "${allure_home}/bin/allure generate --clean ${resultPaths.collect { result -> "${result.path}/allure-results" }.join(" ")}"
        }
    }

    stage('Publish tests report') {
        script {
            allure([
                includeProperties: false,
                jdk              : '',
                commandline      : Constants.CYPRESS_ALLURE_VERSION,
                properties       : [],
                reportBuildPolicy: 'ALWAYS',
                results          : resultPaths.collect { result -> [path: "${result.path}/allure-results"] }
            ])
        }
    }
}
