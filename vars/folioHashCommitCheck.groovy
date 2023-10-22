def changeDetected(branch) {

  def parameter_name = 'Hash-Commit'
  def region = 'us-west-2'
  def currentHash = getCurrentGitHash(branch)
  def savedHash = getSavedHashFromSSM(region,parameter_name)

  if (currentHash == savedHash) {
    echo "No changes detected. Returning true."
    return false
  } else {
    echo "Changes detected. Returning true and updating ssm with new hash: ${currentHash}."
    UpdateSsmParameterValue(region,parameter_name,currentHash)
    return true
  }
}

def getCurrentGitHash(branch) {
  try {
    //return sh(script: "git ls-remote https://github.com/folio-org/pipelines-shared-library.git refs/heads/${branch} | cut -f 1", returnStdout: true).trim()
    def parameterValue1 = sh(script: 'aws ssm get-parameter --name Hash-Commit --region us-west-2 --query "Parameter.Value" --output text', returnStdout: true).trim()
    echo "Value of Hash-Commit: ${parameterValue1}"
  } catch (Exception e) {
    error "Error fetching Git hash: ${e.message}"
  }
}

def getSavedHashFromSSM(region,parameter_name) {
    def parameterValue = GetSsmParameterValue(region,parameter_name);
    echo "${parameterValue}"
    return parameterValue
}


def GetSsmParameterValue(String region, String parameter_name) {
  return sh(script: "aws ssm get-parameter --name ${parameter_name} --region ${region} --query  \"Parameter.Value\" --output text", returnStatus: true)
}

void UpdateSsmParameterValue(String region, String parameter_name, String currentHash) {
  sh(script: "aws ssm put-parameter --name ${parameter_name} --region ${region} --value ${currentHash} --type String --overwrite")
}
