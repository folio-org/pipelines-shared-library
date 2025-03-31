import org.folio.Constants
import org.folio.utilities.Logger
import org.folio.models.parameters.KarateTestsParameters

void call(KarateTestsParameters args) {
  Logger logger = new Logger(this, 'Gatling flow')

  /** Base directory to clone sources from GitHub repository */
  String gatlingBaseDir = 'folio-gatling-tests'

  stage('[Git] Checkout Module repo') {
    dir(gatlingBaseDir) {
      checkout poll: false,
        scm: [
          $class: 'GitSCM',
          branches: [[name: "*/${args.gitBranch}"]],
          extensions: [
            [$class: 'CloneOption', noTags: true, reference: '', shallow: true],
            [$class: 'CloneOption', depth: 10, honorRefspec: true, noTags: false, reference: '', shallow: true],
            [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]
          ],
          userRemoteConfigs: [[credentialsId: 'jenkins-github-sshkey', url: "${Constants.FOLIO_GITHUB_URL}/folio-integration-tests.git"]]
        ]
    }
  }

  stage("[Groovy] Build Karate config") {
    dir(gatlingBaseDir) {
      def files = findFiles(glob: '**/karate-config.js')
      files.each { file ->
        echo "Updating file ${file.path}"
        writeFile file: file.path, text: karateTestUtils.renderKarateConfig(readFile(file.path), args)
      }
    }
  }

  stage('[Maven] Gatling tests') {
    dir(gatlingBaseDir) {
      timeout(time: args.timeout, unit: 'HOURS') {
        withMaven(jdk: args.javaVerson, maven: args.mavenToolName, mavenSettingsConfig: args.mavenSettings) {

          /**
           * The modules to test are passed as a comma separated list of modules.
           * The modules are passed as a parameter to the Jenkins job.
           * The modules are passed to the Maven command as a parameter.
           */
          String modules = args.modulesToTest ? "-pl common,testrail-integration,${args.modulesToTest}" : ""

          sh "mvn gatling:test -Dkarate.env=${args.karateConfig} ${modules}"
        }
      }
    }
  }

  stage('[Report] Publish results') {
    dir(gatlingBaseDir) {
      gatlingArchive()
    }
  }
}
