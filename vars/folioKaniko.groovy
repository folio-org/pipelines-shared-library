import org.folio.Constants

/**
 * Logs in to Docker Hub using Kaniko by creating a Docker configuration file with encoded credentials.
 * <a href="https://github.com/GoogleContainerTools/kaniko/blob/main/README.md#pushing-to-docker-hub">Pushing to Docker Hub</a>
 * @param configDir The directory where the Docker configuration file will be created. Defaults to '/kaniko/.docker'.
 */
void dockerHubLogin(String configDir = '/kaniko/.docker') {
  // Use Jenkins credentials to retrieve Docker Hub username and password
  withCredentials([usernamePassword(credentialsId: Constants.DOCKER_FOLIOCI_PULL_CREDENTIALS_ID,
    usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASSWORD')]) {

    // Encode username:password to base64
    String credentials = "${DOCKER_USER}:${DOCKER_PASSWORD}".bytes.encodeBase64().toString()

    // Define the JSON content for Docker config (v1 endpoint as required)
    Map dockerConfig = [auths: ['https://index.docker.io/v1/': [auth: credentials]]]

    // Ensure the configuration directory exists and write the JSON content to the file
    sh "mkdir -p ${configDir} && echo '${writeJSON(json: dockerConfig, returnText: true)}' > ${configDir}/config.json"
  }
}
