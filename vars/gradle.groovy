import org.folio.Constants

def run(String task, String parameters) {
  withCredentials([
    usernamePassword(credentialsId: Constants.NEXUS_PUBLISH_CREDENTIALS_ID, usernameVariable: 'HELM_USERNAME', passwordVariable: 'HELM_PASSWORD'),
    usernamePassword(credentialsId: Constants.DOCKER_FOLIO_REPOSITORY_CREDENTIALS_ID, usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')
  ]) {
    sh "./gradlew ${task} ${parameters} " +
      "-PhelmRegistryUrl=${Constants.FOLIO_HELM_HOSTED_REPO_URL} -PhelmRegistryUsername=${HELM_USERNAME} -PhelmRegistryPassword=${HELM_PASSWORD} " +
      "-PdockerRegistryUrl=${Constants.DOCKER_FOLIO_REPOSITORY} -PdockerRegistryUsername=${DOCKER_USERNAME} -PdockerRegistryPassword=${DOCKER_PASSWORD}"
  }
}

def build(String parameters) {
  run("build", parameters)
}

def publish(String parameters) {
  run("publish", parameters)
}
