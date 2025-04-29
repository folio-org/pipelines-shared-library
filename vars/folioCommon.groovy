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

/**
 * Method to update the build name with a new name
 * This method is used to set the build name in Jenkins.
 * @param newName
 */
void updateBuildName(String newName) {
  if (currentBuild.displayName == "#${env.BUILD_ID}") {
    currentBuild.displayName = newName
  }
}

/**
 * Method to update the build description with new content
 * @param newContent The new content to be added to the build description
 */
void updateBuildDescription(String newContent) {
  if (!currentBuild.description) {
    currentBuild.description = newContent
  } else {
    currentBuild.description += "\n${newContent}"
  }
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

void validateNamespace(String namespace) {
  Map cause = getRelevantCause()
  List allNamespaces = []
  allNamespaces.addAll(Constants.AWS_EKS_TMP_NAMESPACES)
  allNamespaces.addAll(Constants.AWS_EKS_TESTING_NAMESPACES)
  allNamespaces.addAll(Constants.AWS_EKS_RELEASE_NAMESPACES)
  allNamespaces.addAll(Constants.AWS_EKS_DEV_NAMESPACES)
  allNamespaces.addAll(Constants.RANCHER_KNOWN_NAMESPACES)

  if (!isApprovedUser(cause?.userId) && !allNamespaces.collect { it.toLowerCase() }.contains(namespace.toLowerCase())) {
    error("Unknown namespace: ${namespace}")
  }
}
