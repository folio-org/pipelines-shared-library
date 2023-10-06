import org.folio.Constants

/**
 * !Attention! This method should be called inside node block in parent
 *
 * Allowed parameters:
 * - customBuildName
 * - branch
 * - tenantUrl
 * - okapiUrl
 * - tenantId
 * - adminUsername
 * - adminPassword
 * - parallelExecParameters
 * - sequentialExecParameters
 * - testsTimeout
 * - testrailProjectID
 * - testrailRunID
 * - numberOfWorkers
 * - agent
 * @param params
 */
void call(params) {
  folioTools.validateParams(params, ['parallelExecParameters', 'sequentialExecParameters', 'testrailProjectID', 'testrailRunID', 'numberOfWorkers'])

  /* Define variables */
  String customBuildName = params.customBuildName?.trim() ?
    "${params.customBuildName.replaceAll(/[^A-Za-z0-9\s.]/, "").replace(' ', '_')}.${env.BUILD_ID}" : env.BUILD_ID
  String branch = params.branch
  String tenantUrl = params.tenantUrl
  String okapiUrl = params.okapiUrl
  String tenantId = params.tenantId
  String adminUsername = params.adminUsername
  String adminPassword = params.adminPassword
  String parallelExecParameters = params.parallelExecParameters
  String sequentialExecParameters = params.sequentialExecParameters
  String testsTimeout = params.testsTimeout?.trim() ?: Constants.GLOBAL_BUILD_TIMEOUT
  String testrailProjectID = params.testrailProjectID
  String testrailRunID = params.testrailRunID
  int numberOfWorkers = params.numberOfWorkers as int ?: 1
  String agent = params.agent
  String browserName = "chrome"
  String cypressImageVersion = ''
  List resultPaths = []

  buildName customBuildName

  timeout(time: testsTimeout, unit: 'HOURS') {
    if (parallelExecParameters?.trim()) {
      stage('[Cypress] Parallel run') {
        script {
          Map workers = [:]
          for (int workerNumber = 1; workerNumber <= numberOfWorkers; workerNumber++) {
            workers["Worker#${workerNumber}"] = { currentWorkerNumber ->
              node('rancher||jenkins-agent-java11||jenkins-agent-java17') {
                cloneCypressRepo(branch)

                cypressImageVersion = getCypressImageVersion()

                executeTests(cypressImageVersion, tenantUrl, okapiUrl, tenantId, adminUsername, adminPassword,
                  "parallel_${customBuildName}", browserName, parallelExecParameters, testrailProjectID, testrailRunID)

                resultPaths.add(archiveTestResults(currentWorkerNumber))
              }
            }.curry(workerNumber)
          }
          parallel(workers)
        }
      }
    }

    if (sequentialExecParameters?.trim()) {
      stage('[Cypress] Sequential run') {
        script {
          cloneCypressRepo(branch)

          cypressImageVersion = getCypressImageVersion()

          executeTests(cypressImageVersion, tenantUrl, okapiUrl, tenantId, adminUsername, adminPassword,
            "sequential_${customBuildName}", browserName, sequentialExecParameters, testrailProjectID, testrailRunID)

          resultPaths.add(archiveTestResults(numberOfWorkers + 1))
        }
      }
    }
  }

  stage('[Allure] Generate report') {
    script {
      for (path in resultPaths) {
        unstash name: path
        unzip zipFile: "${path}.zip", dir: path
      }
      def allureHome = tool type: 'allure', name: Constants.CYPRESS_ALLURE_VERSION
      sh "${allureHome}/bin/allure generate --clean ${resultPaths.collect { path -> "${path}/allure-results" }.join(" ")}"
    }
  }

  stage('[Allure] Publish report') {
    script {
      allure([
        includeProperties: false,
        jdk              : '',
        commandline      : Constants.CYPRESS_ALLURE_VERSION,
        properties       : [],
        reportBuildPolicy: 'ALWAYS',
        results          : resultPaths.collect { path -> [path: "${path}/allure-results"] }
      ])
    }
  }

  stage('[Allure] Send slack notifications') {
    script {
      def parseAllureReport = readJSON(file: "${WORKSPACE}/allure-report/data/suites.json")
      def statusCounts = [failed: 0, passed: 0, broken: 0]
      parseAllureReport.children.each { child ->
        child.children.each { testCase ->
          def status = testCase.status
          if (statusCounts[status] != null) {
            statusCounts[status] += 1
          }
        }
      }
      def passedTestsCount = statusCounts.passed
      def failedTestsCount = statusCounts.failed
      def brokenTestsCount = statusCounts.broken
      def totalTestsCount = passedTestsCount + failedTestsCount + brokenTestsCount
      def passRateInDecimal = totalTestsCount > 0 ? (passedTestsCount * 100) / totalTestsCount : 100
      def passRate = passRateInDecimal.intValue()
      println "Total passed tests: ${passedTestsCount}"
      println "Total failed tests: ${failedTestsCount}"
      println "Total broken tests: ${brokenTestsCount}"

      if (currentBuild.result == 'FAILURE' || (passRate != null && passRate < 50)) {
        slackSend(channel: "#rancher_tests_notifications", color: 'danger', message: "Build name: ${customBuildName}. Cypress tests results: Passed tests: ${passedTestsCount}, Broken tests: ${brokenTestsCount}, Failed tests: ${failedTestsCount}, Pass rate:${passRate}%")
      } else {
        slackSend(channel: "#rancher_tests_notifications", color: 'good', message: "Build name: ${customBuildName}. Cypress tests results: Passed tests: ${passedTestsCount}, Broken tests: ${brokenTestsCount}, Failed tests: ${failedTestsCount}, Pass rate:${passRate}%")

      }
    }
  }
}

/* Functions */

void cloneCypressRepo(String branch) {
  stage('Checkout Cypress repo') {
    script {
      checkout([$class           : 'GitSCM',
                branches         : [[name: "*/${branch}"]],
                extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true],
                                    [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]],
                userRemoteConfigs: [[credentialsId: Constants.GITHUB_SSH_CREDENTIALS_ID,
                                     url          : Constants.CYPRESS_SSH_REPOSITORY_URL]]])
    }
  }
}

String getCypressImageVersion() {
  stage('Cypress tests Image version') {
    script {
      return readJSON(text: readFile("${env.WORKSPACE}/package.json"))['dependencies']['cypress']
    }
  }
}

void executeTests(String cypressImageVersion, String tenantUrl, String okapiUrl, String tenantId, String adminUsername,
                  String adminPassword, String customBuildName, String browserName, String execParameters,
                  String testrailProjectID = '', String testrailRunID = '') {
  stage('Run tests') {
    script {
      catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
        docker.image("cypress/included:${cypressImageVersion}").inside('--entrypoint=') {
          env.HOME = "${pwd()}"
          env.CYPRESS_CACHE_FOLDER = "${pwd()}/cache"
          env.CYPRESS_BASE_URL = "${tenantUrl}"
          env.CYPRESS_OKAPI_HOST = "${okapiUrl}"
          env.CYPRESS_OKAPI_TENANT = "${tenantId}"
          env.CYPRESS_diku_login = "${adminUsername}"
          env.CYPRESS_diku_password = "${adminPassword}"
          env.AWS_DEFAULT_REGION = Constants.AWS_REGION

          withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                            credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

            String cypressTestrailSimpleVersion = readJSON(text: readFile("${env.WORKSPACE}/package.json"))['dependencies']['cypress-testrail-simple']
            String cypressCloudVersion = readJSON(text: readFile("${env.WORKSPACE}/package.json"))['dependencies']['cypress-cloud']
            String execString = "\$HOME/.yarn/bin/cypress-cloud run --parallel --record --browser ${browserName} --ci-build-id ${customBuildName} ${execParameters}"

            sh "yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}"
            sh "yarn install"
            sh "yarn add -D cypress-testrail-simple@${cypressTestrailSimpleVersion}"
            sh "yarn global add cypress-cloud@${cypressCloudVersion}"

            if (testrailProjectID?.trim() && testrailRunID?.trim()) {
              env.TESTRAIL_HOST = Constants.CYPRESS_TESTRAIL_HOST
              env.TESTRAIL_PROJECTID = testrailProjectID
              env.TESTRAIL_RUN_ID = testrailRunID
              env.CYPRESS_allureReuseAfterSpec = "true"

              println "Test results will be posted to TestRail.\nProjectID: ${testrailProjectID},\nRunID: ${testrailRunID}"

              withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
                sh execString
              }
            } else {
              sh execString
            }
          }
        }
      }
    }
  }
}

String archiveTestResults(def id) {
  stage('Archive test results') {
    script {
      zip zipFile: "allure-results-${id}.zip", glob: "allure-results/*"
      archiveArtifacts allowEmptyArchive: true, artifacts: "allure-results-${id}.zip", fingerprint: true, defaultExcludes: false
      stash name: "allure-results-${id}", includes: "allure-results-${id}.zip"
      return "allure-results-${id}"
    }
  }
}
