package org.folio.version

import com.cloudbees.groovy.cps.NonCPS

import static org.folio.version.VersionConstants.*

/**
 * Project version
 */
class ProjectVersion implements Serializable {

  def pipeline

  private String version

  private String branch

  private String timestamp

  ProjectVersion(def pipeline, String version, String branch) {
    this.pipeline = pipeline
    this.version = version
    this.branch = branch
    this.timestamp = new Date().format("yyyyMMddHHmmss")

    //  TODO: add "release/" branch???
    verifyBranchVersionMatch()
  }

  def getProjectVersion() {
    def retVal
    if (branch == VersionConstants.MASTER_BRANCH) {
      retVal = version
    } else if (branch == DEVELOP_BRANCH) {
      retVal = "${version.substring(0, version.length() - SNAPSHOT_POSTFIX.length())}${timestamp}"
    } else {
      retVal = "${version.substring(0, version.length() - SNAPSHOT_POSTFIX.length())}${branch}-${timestamp}"
    }
    return retVal.replaceAll('/', '-')
  }

  def getJarProjectVersion() {
    def retVal
    if (branch == MASTER_BRANCH || branch == DEVELOP_BRANCH) {
      retVal = version
    } else {
      retVal = "${version.substring(0, version.length() - SNAPSHOT_POSTFIX.length())}${branch}-${SNAPSHOT_POSTFIX}"
    }
    return retVal.replaceAll('/', '-')
  }

  @NonCPS
  private verifyBranchVersionMatch() {
    if (branch == MASTER_BRANCH && !(version ==~ "^\\d+\\.\\d+\\.\\d+\$")) {
      pipeline.error("Version '${version}' doesn't match expected 'x.x.x' format.")
    } else if (branch != MASTER_BRANCH && !(version ==~ "^\\d+\\.\\d+\\.\\d+-SNAPSHOT\$")) {
      pipeline.error("Version '${version}' doesn't match expected 'x.x.x-SNAPSHOT' format.")
    }
  }

}
