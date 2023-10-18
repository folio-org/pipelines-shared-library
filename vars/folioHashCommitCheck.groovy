// folioHashCommitCheck.groovy

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest

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
  def parameterName = "CommitHash" // Adjust the parameter name as needed

  try {
    def parameterRequest = new GetParameterRequest().withName(parameterName)
    def parameterValue = ssmClient.getParameter(parameterRequest).getParameter().getValue()
    return parameterValue.trim()
  } catch (Exception e) {
    error "Error fetching saved hash from AWS SSM parameters: ${e.message}"
    return null
  }
}
