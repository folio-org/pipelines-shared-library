import org.folio.Constants
import org.folio.utilities.Logger

call(GatlingTestsParameters args) {
  Logger logger = new Logger(this, 'Gatling flow')

  stage('[Git] Checkout Module repo') {
    dir('folio-gatling-tests') {
      checkout poll: false,
        scm: [
          $class: 'GitSCM',
          branches: [[name: "*/${args.gitBranch}"]],
          extensions: [
            [$class: 'CloneOption', noTags: true, reference: '', shallow: true],
            [$class: 'CloneOption', depth: 10, honorRefspec: true, noTags: false, reference: '', shallow: true],
            [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]
          ],
          userRemoteConfigs: [[credentialsId: 'jenkins-github-sshkey', url: "${Constants.FOLIO_GITHUB_URL}/folio-gatling-tests.git"]]
        ]
    }
  }

  stage('[Maven] Gatling tests') {
    dir('folio-gatling-tests') {
      timeout(time: args.timeout, unit: 'HOURS') {
        withMaven(jdk: args.javaVerson, maven: args.mavenVersion, mavenSettingsConfig: args.mavenSettings) {

          /**
           * The modules to test are passed as a comma separated list of modules.
           * The modules are passed as a parameter to the Jenkins job.
           * The modules are passed to the Maven command as a parameter.
           */
          String modules = args.modulesToTest ? "-pl common,testrail-integration,${args.modulesToTest}" : ""

          sh "mvn gatling:test -Dkarate.env=${args.envType} ${modules}"
        }
      }
    }
  }

  stage('[Report] Publish results') {
    dir('folio-gatling-tests') {
      gatlingArchive()
    }
  }
}
