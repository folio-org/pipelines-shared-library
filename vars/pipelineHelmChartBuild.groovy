import org.folio.version.ProjectVersion

def call() {
  ProjectVersion projectVersion

  pipeline {
    agent { label 'jenkins-agent-java11' }

    options {
      disableConcurrentBuilds()
    }

    stages {
      stage("Configure environment") {
        steps {
          script {
            sh "curl -LSs https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash -s -- --version v3.8.0"
          }
        }
      }

      stage("Calculate version") {
        steps {
          script {
            def props = readProperties file: 'gradle.properties'
            projectVersion = new ProjectVersion(this, props.version, BRANCH_NAME)
            echo "Calculated version: ${projectVersion.getProjectVersion()}"
          }
        }
      }

      stage('Build') {
        steps {
          script {
            gradle.build("-PprojectVersion=${projectVersion.getProjectVersion()}")
          }
        }
        post {
          always {
            archiveArtifacts 'build/helm/charts/*.tgz'
          }
        }
      }

      stage("Publish all artifacts") {
        steps {
          script {
            gradle.publish("-PprojectVersion=${projectVersion.getProjectVersion()}")

            currentBuild.description = "--- helm charts ---\n"
            findFiles(glob: '**/helm/charts/*.tgz').each { file ->
              currentBuild.description += "${file.name}\n"
            }
          }
        }
      }
    }
  }
}
