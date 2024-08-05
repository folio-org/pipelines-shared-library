import org.folio.Constants
import org.folio.client.reportportal.ReportPortalClient
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestType
import org.folio.testing.cypress.results.CypressRunExecutionSummary

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
 * - useReportPortal
 * - runType
 * @param params
 */
void call(params) {
  folioTools.validateParams(params, ['parallelExecParameters', 'sequentialExecParameters', 'testrailProjectID', 'testrailRunID', 'numberOfWorkers'])

  /* Define variables */
  String customBuildName = params.customBuildName?.trim() ?
    "#${params.customBuildName.replaceAll(/[^A-Za-z0-9\s.]/, "").replace(' ', '_')}.${env.BUILD_ID}" : "#${env.BUILD_ID}"
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
  String runType = params.runType
  IRunExecutionSummary testRunExecutionSummary
  boolean sendSlackNotification = params.sendSlackNotification ? params?.sendSlackNotification?.trim()?.toLowerCase()?.toBoolean() : true
  int numberOfWorkers = params.numberOfWorkers as int ?: 1
  boolean useReportPortal = params?.useReportPortal?.trim()?.toLowerCase()?.toBoolean()
  boolean runSanityCheck = params?.runSanityCheck?.trim()?.toLowerCase()?.toBoolean()

  def rpLaunchID

  String agent = params.agent
  String browserName = "chrome"
  String cypressImageVersion = ''
  List resultPaths = []

  ReportPortalClient reportPortal = null

  buildName customBuildName

  if (useReportPortal) {
    stage('[ReportPortal config bind & launch]') {
      try {
        reportPortal = new ReportPortalClient(this, TestType.CYPRESS, customBuildName, env.BUILD_NUMBER, env.WORKSPACE, runType)

        rpLaunchID = reportPortal.launch()
        println("${rpLaunchID}")

        String portalExecParams = reportPortal.getExecParams()
        println("Report portal execution parameters: ${portalExecParams}")

        parallelExecParameters = parallelExecParameters?.trim() ?
          "${parallelExecParameters} ${portalExecParams}" : parallelExecParameters

        sequentialExecParameters = sequentialExecParameters?.trim() ?
          "${sequentialExecParameters} ${portalExecParams}" : sequentialExecParameters
      } catch (Exception e) {
        println("Error: " + e.getMessage())
      }
    }
  }

  try {
    timeout(time: testsTimeout, unit: 'HOURS') {
      if (runSanityCheck) {
        stage('[Cypress] Run sanity suite') {
          String sanityExecParams = "--env grepTags=\"fse+sanity\""

          setupCommonEnvironmentVariables(tenantUrl, okapiUrl, tenantId, adminUsername, adminPassword)

          cloneCypressRepo(branch)
          cypressImageVersion = readPackageJsonDependencyVersion('./package.json', 'cypress')

          compileTests(cypressImageVersion)

          executeTests(cypressImageVersion, "sanity_${customBuildName}", browserName, sanityExecParams,
            testrailProjectID, testrailRunID)

          resultPaths.add(archiveTestResults('0'))
        }
      }
      if (parallelExecParameters?.trim()) {
        stage('[Cypress] Parallel run') {
          script {
            int workersLimit
            int batchSize
            switch (agent) {
              case 'cypress-ci':
                workersLimit = 4
                batchSize = 4
                break
              case 'cypress-static':
                workersLimit = 6
                batchSize = 6
                break
              case 'cypress':
                workersLimit = 12
                batchSize = 4
                break
              default:
                error("Worker agent label unknown! '${agent}'")
                break
            }
            int maxWorkers = Math.min(numberOfWorkers, workersLimit) // Ensuring not more than limited workers number
            List<List<Integer>> batches = (1..maxWorkers).toList().collate(batchSize)

            setupCommonEnvironmentVariables(tenantUrl, okapiUrl, tenantId, adminUsername, adminPassword)

            // Divide workers into batches
            Map<String, Closure> batchExecutions = [failFast: false]
            batches.eachWithIndex { batch, batchIndex ->
              batchExecutions["Batch#${batchIndex + 1}"] = {
                node(agent) {
                  cleanWs notFailBuild: true

                  dir("cypress-${batch[0]}") {
                    cloneCypressRepo(branch)
                    cypressImageVersion = readPackageJsonDependencyVersion('./package.json', 'cypress')

                    compileTests(cypressImageVersion, "${batch[0]}")
                  }

                  batch.eachWithIndex { copyBatch, copyBatchIndex ->
                    if (copyBatchIndex > 0) {
                      sh "mkdir -p cypress-${copyBatch}"
                      sh "cp -r cypress-${batch[0]}/. cypress-${copyBatch}"
                    }
                  }

                  Map<String, Closure> parallelWorkers = [failFast: false]
                  batch.each { workerNumber ->
                    parallelWorkers["Worker#${workerNumber}"] = {
                      dir("cypress-${workerNumber}") {
                        executeTests(cypressImageVersion, "parallel_${customBuildName}"
                          , browserName, parallelExecParameters
                          , testrailProjectID, testrailRunID, workerNumber.toString())
                      }
                    }
                  }
                  parallel(parallelWorkers)

                  batch.each { workerNumber ->
                    dir("cypress-${workerNumber}") {
                      resultPaths.add(archiveTestResults("${workerNumber}"))
                    }
                  }
                }
              }
            }
            parallel(batchExecutions)
          }
        }
      }
      if (sequentialExecParameters?.trim()) {
        stage('[Cypress] Sequential run') {
          script {
            setupCommonEnvironmentVariables(tenantUrl, okapiUrl, tenantId, adminUsername, adminPassword)

            cloneCypressRepo(branch)
            cypressImageVersion = readPackageJsonDependencyVersion('./package.json', 'cypress')

            compileTests(cypressImageVersion)

            executeTests(cypressImageVersion, "sequential_${customBuildName}", browserName
              , sequentialExecParameters, testrailProjectID, testrailRunID)

            resultPaths.add(archiveTestResults((numberOfWorkers + 1).toString()))
          }
        }
      }
    }
  } catch (e) {
    println(e)
    error("Tests execution stage failed")
  } finally {
    if (useReportPortal) {
      stage("[ReportPortal] Finish run") {
        try {
          def res_end = reportPortal.launchFinish()
          println("${res_end}")
        } catch (Exception e) {
          println("Couldn't stop run in ReportPortal\nError: ${e.getMessage()}")
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

    stage('[Report] Analyze results') {
      def jsonSuites = readJSON(file: "${WORKSPACE}/allure-report/data/suites.json")
      def jsonDefects = readJSON(file: "${WORKSPACE}/allure-report/data/categories.json")

      testRunExecutionSummary = CypressRunExecutionSummary.addFromJSON(jsonSuites)
      testRunExecutionSummary.addDefectsFromJSON(jsonDefects)
    }

    if (sendSlackNotification) {
      stage('[Slack] Send notification') {
        slackSend(attachments: folioSlackNotificationUtils
          .renderBuildAndTestResultMessage(
            TestType.CYPRESS
            , testRunExecutionSummary
            , customBuildName
            , useReportPortal
            , "${env.BUILD_URL}allure/"
          )
          , channel: "#rancher_tests_notifications")
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

String readPackageJsonDependencyVersion(String filePath, String dependencyName) {
  def packageJson = readJSON file: filePath
  return packageJson['dependencies'][dependencyName] ?: packageJson['devDependencies'][dependencyName]
}

void setupCommonEnvironmentVariables(String tenantUrl, String okapiUrl, String tenantId, String adminUsername, String adminPassword) {
  env.CYPRESS_BASE_URL = tenantUrl
  env.CYPRESS_OKAPI_HOST = okapiUrl
  env.CYPRESS_OKAPI_TENANT = tenantId
  env.CYPRESS_diku_login = adminUsername
  env.CYPRESS_diku_password = adminPassword
  env.AWS_DEFAULT_REGION = Constants.AWS_REGION
}

void compileTests(String cypressImageVersion, String batchID = '') {
  stage('Compile tests') {
    runInDocker(cypressImageVersion, "compile-${env.BUILD_ID}-${batchID}", {
      sh """export HOME=\$(pwd); export CYPRESS_CACHE_FOLDER=\$(pwd)/cache
        node -v; yarn -v
        yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}
        env; yarn install
        yarn add -D cypress-testrail-simple@${readPackageJsonDependencyVersion('./package.json', 'cypress-testrail-simple')}
        yarn global add cypress-cloud@${readPackageJsonDependencyVersion('./package.json', 'cypress-cloud')}"""
//      sh "yarn add @reportportal/agent-js-cypress@latest"
    })
  }
}

void executeTests(String cypressImageVersion, String customBuildName, String browserName, String execParameters,
                  String testrailProjectID = '', String testrailRunID = '', String workerId = '') {
  stage('Run tests') {
    String runId = workerId?.trim() ? "${env.BUILD_ID}${workerId}" : env.BUILD_ID
    runId = runId.length() > 2 ? runId : "0${runId}"
    String execString = """
      export HOME=\$(pwd); export CYPRESS_CACHE_FOLDER=\$(pwd)/cache
      export DISPLAY=:${runId[-2..-1]}
      mkdir -p /tmp/.X11-unix
      Xvfb \$DISPLAY -screen 0 1920x1080x24 &
      env; npx cypress-cloud run --parallel --record --browser ${browserName} --ci-build-id ${customBuildName} ${execParameters}
      pkill Xvfb
    """

    runInDocker(cypressImageVersion, "worker-${runId}", {
      if (testrailProjectID?.trim() && testrailRunID?.trim()) {
        execString = """
        export TESTRAIL_HOST=${Constants.CYPRESS_TESTRAIL_HOST}
        export TESTRAIL_PROJECTID=${testrailProjectID}
        export TESTRAIL_RUN_ID=${testrailRunID}
        export CYPRESS_allureReuseAfterSpec=true
        """ + execString

        println "Test results will be posted to TestRail.\nProjectID: ${testrailProjectID},\nRunID: ${testrailRunID}"
        withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
          sh execString
        }
      } else {
        sh execString
      }
    })
  }
}

String archiveTestResults(String id) {
  stage('Archive test results') {
    script {
      zip zipFile: "allure-results-${id}.zip", glob: "allure-results/*"
      archiveArtifacts allowEmptyArchive: true, artifacts: "allure-results-${id}.zip", fingerprint: true, defaultExcludes: false
      stash name: "allure-results-${id}", includes: "allure-results-${id}.zip"
      return "allure-results-${id}"
    }
  }
}

void runInDocker(String cypressImageVersion, String containerNameSuffix, Closure<?> closure) {
  String containerName = "cypress-${containerNameSuffix}"
  def containerObject
  try {
    docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
      containerObject = docker.image("732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:latest").inside("--init --name=${containerName} --entrypoint=") {
        withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                          credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
          closure()
        }
      }
    }
  } catch (e) {
    println(e)
    if (containerName.contains('cypress-compile')) {
      currentBuild.result = 'FAILED'
      error('Unable to compile tests')
    } else {
      currentBuild.result = 'UNSTABLE'
    }
  } finally {
    if (containerObject) {
      containerObject.stop()
    }
  }
}
