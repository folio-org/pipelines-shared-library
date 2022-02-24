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
            withCredentials([
              usernamePassword(credentialsId: 'jenkins-nexus', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')
            ]) {
              sh "./gradlew build -PprojectVersion=${projectVersion.getProjectVersion()} -PhelmRegistryUrl=https://repository.folio.org/repository/helm-hosted/ -PhelmRegistryUsername=${NEXUS_USERNAME} -PhelmRegistryPassword=${NEXUS_PASSWORD}"
            }
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
            withCredentials([
              usernamePassword(credentialsId: 'jenkins-nexus', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')
            ]) {
              sh "./gradlew publish -PprojectVersion=${projectVersion.getProjectVersion()} -PhelmRegistryUrl=https://repository.folio.org/repository/helm-hosted/ -PhelmRegistryUsername=${NEXUS_USERNAME} -PhelmRegistryPassword=${NEXUS_PASSWORD}"
            }

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
