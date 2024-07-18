package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  Eureka(Object context, String okapiDomain, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  boolean isApplicationRegistered(String applicationId) {

    String url = generateKongUrl("/applications/${applicationId}")

    try {
      restClient.get(url).body
      logger.info("Application ${applicationId} is already registered.")
      return true
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")
        return false
      } else {
        throw new RequestException("Application manager is unavailable", e.statusCode)
      }
    }
  }

  def getDescriptorsList(applicationId) {

    steps.awscli.withAwsClient() {
      steps.sh(script: "aws s3api get-object --bucket ${Constants.EUREKA_BUCKET_NAME} --key apps/${applicationId}.json ${applicationId}.json")
    }
    logger.info(steps.readJSON(file: "${applicationId}.json"))
    return steps.readJSON(file: "${applicationId}.json")
  }

  String getEurekaToken(String client_id, String client_secret, String grant_type) {
    logger.info("Getting access token from Keycloak service")

    String url = "${Constants.EUREKA_KEYCLOAK_URL}realms/master/protocol/openid-connect/token"
    Map<String,String> headers = [
      'Content-Type':'application/x-www-form-urlencoded'
    ]
    String requestBody = "client_id=$client_id&client_secret=$client_secret&grant_type=$grant_type"

    try {
      def response = restClient.post(url, requestBody, headers).body
      logger.info("Access token received successfully from Keycloak service")
      logger.info("${response.access_token}")
      return response.access_token
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Cant get token.")
      } else {
        throw new RequestException("Keycloak is unavailable", e.statusCode)
      }
    }
  }

  def registerApplication(String applicationId) {
    String descriptorsList = getDescriptorsList(applicationId)
    if (isApplicationRegistered(applicationId)) {
      logger.warning("Application ${applicationId} is already registered.")
      return
    }

    String url = "${Constants.EUREKA_KONG_URL}applications?check=false"
    Map<String,String> headers = [
      'x-okapi-token': getEurekaToken(),
      'Content-Type': 'application/json'
    ]
    try {
      restClient.post(url, descriptorsList, headers)
      logger.info("Application registered: ${descriptorsList}")
    } catch (RequestException e) {
      throw new RequestException("Application is not registered", e.statusCode)
    }
  }
}
