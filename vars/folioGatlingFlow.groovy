import org.folio.Constants
import org.folio.jenkins.JenkinsAgentLabel
import org.folio.jenkins.PodTemplates
import org.folio.models.parameters.KarateTestsParameters

void call(KarateTestsParameters args) {
  PodTemplates podTemplates = new PodTemplates(this)
  /** Base directory to clone sources from GitHub repository */
  String gatlingBaseDir = 'folio-gatling-tests'

  podTemplates.javaTemplate(args.javaVerson) {
    node(JenkinsAgentLabel.JAVA_AGENT.getLabel()) {
      stage('Ini') {
        def existing = currentBuild.description?.trim()
        def causeDescription = currentBuild.getBuildCauses()[0]?.shortDescription ?: "Unknown cause"

        currentBuild.description = (existing ? "$existing\n" : "") + causeDescription
      }

      stage('[Git] Checkout Module repo') {
        checkout(scmGit(
          branches: [[name: "*/${args.gitBranch}"]],
          extensions: [cloneOption(depth: 10, noTags: true, reference: '', shallow: true),
                       [$class: 'RelativeTargetDirectory', relativeTargetDir: gatlingBaseDir]],
          userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                               url          : "${Constants.FOLIO_GITHUB_URL}/folio-integration-tests.git"]]))
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
        container('java') {
          dir(gatlingBaseDir) {
            timeout(time: args.timeout, unit: 'HOURS') {
              withMaven(jdk: args.javaToolName, maven: args.mavenToolName,
                mavenOpts: '-XX:MaxRAMPercentage=85',
                mavenLocalRepo: "${podTemplates.WORKING_DIR}/.m2/repository",
                traceability: true,
                options: [artifactsPublisher(disabled: true)]) {
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
      }

      stage('[Report] Publish results') {
        dir(gatlingBaseDir) {
          gatlingArchive()
        }
      }
    }
  }
}
