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
  // Retrieve the cause related to a specific user, upstream build, or timer trigger
  Map cause = getRelevantCause()

  // Skip approval if the user is approved, the cause is from an upstream build, or the build was triggered by a timer
  if (isApprovedUser(cause?.userId) || isUpstreamBuild(cause) || isTimerTriggered(cause)) {
    return
  }

  // Trigger a manual input approval for non-approved users
  requestApproval()
}

// Method to retrieve the relevant cause for user, upstream build, or timer trigger
private Map getRelevantCause() {
  return currentBuild.getBuildCauses().find {
    it._class == 'hudson.model.Cause$UserIdCause' ||
      it._class == 'org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause' ||
      it._class == 'hudson.triggers.TimerTrigger$TimerTriggerCause'
  }
}

// Method to check if the user ID is in the approved list
private boolean isApprovedUser(String userId) {
  return Constants.JENKINS_KITFOX_USER_IDS.contains(userId)
}

// Method to check if the cause is from an upstream build
private boolean isUpstreamBuild(Map cause) {
  return cause?._class == 'org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause'
}

// Method to check if the build was triggered by a timer
private boolean isTimerTriggered(Map cause) {
  return cause?._class == 'hudson.triggers.TimerTrigger$TimerTriggerCause'
}

// Method to trigger an approval input prompt
private void requestApproval() {
  input(
    message: 'Attention! This action must be approved by the Kitfox team. Please contact #rancher-support on Slack.',
    ok: 'Proceed',
    submitter: Constants.JENKINS_KITFOX_USER_IDS.join(', ')
  )
}
