// folioHashCommitCheck.groovy

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

def checkHashChanges(branch, awsRegionHash) {


  def currentHash = getCurrentGitHash(branch)
  def savedHash = getSavedHashFromSSM(awsRegionHash)

  if (currentHash == savedHash) {
    echo "No changes detected. Returning true."
    return true
  } else {
    echo "Changes detected. Returning false."
    return false
  }
}

def getCurrentGitHash(branch) {
  try {
    return sh(script: "git ls-remote https://github.com/folio-org/pipelines-shared-library.git refs/heads/${branch} | cut -f 1", returnStdout: true).trim()
  } catch (Exception e) {
    error "Error fetching Git hash: ${e.message}"
    return null
  }
}

def getSavedHashFromSSM(awsRegionHash) {
  def ssmClient = AWSSimpleSystemsManagementClientBuilder.standard().withRegion(awsRegionHash).build()
  def parameterName = "CommitHash" // Parameter name

  try {
    def parameterRequest = new GetParameterRequest().withName(parameterName)
    def parameterValue = ssmClient.getParameter(parameterRequest).getParameter().getValue()
    return parameterValue.trim()
  } catch (Exception e) {
    error "Error fetching saved hash from AWS SSM parameters: ${e.message}"
    return null
  }
}



String getLastCommitHash(String repository, String branch) {
  String url = "https://api.github.com/repos/${Constants.FOLIO_ORG}/${repository}/branches/${branch}"
  def response = new HttpClient(this).getRequest(url)
  if (response.status == HttpURLConnection.HTTP_OK) {
    return new Tools(this).jsonParse(response.content).commit.sha
  } else {
    new Logger(this, 'common').error(new HttpClient(this).buildHttpErrorMessage(response))
  }
}
