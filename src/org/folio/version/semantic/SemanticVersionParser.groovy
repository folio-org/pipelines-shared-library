package org.folio.version.semantic

import com.cloudbees.groovy.cps.NonCPS
import org.folio.version.semantic.model.SemanticVersion
import org.folio.version.semantic.model.SemanticVersionType

import static org.folio.version.VersionConstants.DEVELOP_BRANCH
import static org.folio.version.VersionConstants.MASTER_BRANCH

class SemanticVersionParser implements Serializable {

  @NonCPS
  SemanticVersion parseSemanticVersion(String version) {
    def retVal = new SemanticVersion(value: version)

    def split = version.split("-")

    String[] semVer = split[0].split("\\.")
    retVal.major = semVer[0] as Integer
    retVal.minor = semVer[1] as Integer
    retVal.patch = semVer[2] as Integer

    if (version ==~ SemanticVersionType.RELEASE.pattern) {
      retVal.type = SemanticVersionType.RELEASE
      retVal.branch = MASTER_BRANCH
    } else if (version ==~ SemanticVersionType.SNAPSHOT.pattern) {
      retVal.type = SemanticVersionType.SNAPSHOT
      retVal.branch = DEVELOP_BRANCH
      retVal.timestamp = split[1]
    } else if (version ==~ SemanticVersionType.BRANCH.pattern) {
      retVal.type = SemanticVersionType.BRANCH

      String branch = ""
      if (split.size() > 3) {
        for (int i = 1; i < split.size() - 1; i++) {
          branch += "-${split[i]}"
        }
        branch = branch.substring(1, branch.length())
      } else {
        branch = split[1]
      }

      retVal.branch = branch
      retVal.timestamp = split[split.size() - 1]
    } else {
      throw new IllegalArgumentException("Unexpected semantic version format '${version}'")
    }

    retVal
  }

}
