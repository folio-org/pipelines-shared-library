import org.folio.Constants

void defaultJobWrapper(Closure stages, boolean checkoutGit = true) {
  try {
    if (checkoutGit) {
      stage('Checkout') {
        checkout scm
      }
    }
    stages()
  } catch (e) {
    println "Caught exception: ${e}"
    println "Stack trace:"
    e.printStackTrace()
    error(e.getMessage())
  } finally {
    stage('Cleanup') {
      cleanWs notFailBuild: true
    }
  }
}

void kitfoxApproval() {
  // Retrieve the cause related to a specific user
  Map userCause = getUserCause()

  // Check if the user ID is in the allowed list
  if (isApprovedUser(userCause?.userId)) {
    return
  }

  // Trigger a manual input approval for non-approved users
  requestApproval()
}

// Method to retrieve the user cause
private Map getUserCause() {
  return currentBuild.getBuildCauses().find { it._class == 'hudson.model.Cause$UserIdCause' }
}

// Method to check if the user ID is in the approved list
private boolean isApprovedUser(String userId) {
  return Constants.JENKINS_KITFOX_USER_IDS.contains(userId)
}

// Method to trigger an approval input prompt
private void requestApproval() {
  input(
    message: 'Attention! This action must be approved by the Kitfox team. Please contact #rancher-support on Slack.',
    ok: 'Proceed',
    submitter: Constants.JENKINS_KITFOX_USER_IDS.join(', ')
  )
}
