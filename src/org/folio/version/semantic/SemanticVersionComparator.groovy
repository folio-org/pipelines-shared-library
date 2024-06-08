package org.folio.version.semantic


import com.cloudbees.groovy.cps.NonCPS
import org.folio.version.semantic.model.SemanticVersion

class SemanticVersionComparator implements Comparator<String>, Serializable {

  Order order

  List<String> preferredBranches

  private SemanticVersionParser parser = new SemanticVersionParser()

  @NonCPS
  @Override
  int compare(String left, String right) {
    SemanticVersion lSemVer = parser.parseSemanticVersion(left)
    SemanticVersion rSemVer = parser.parseSemanticVersion(right)

    if (preferredBranches) {
      def preferredBranchesCompare = comparePreferredBranches(lSemVer, rSemVer)
      if (preferredBranchesCompare != 0) {
        return ensureOrder(preferredBranchesCompare)
      }
    }

    def majorCompare = lSemVer.major <=> rSemVer.major
    if (majorCompare != 0) {
      return ensureOrder(majorCompare)
    }

    def minorCompare = lSemVer.minor <=> rSemVer.minor
    if (minorCompare != 0) {
      return ensureOrder(minorCompare)
    }

    def patchCompare = lSemVer.patch <=> rSemVer.patch
    if (patchCompare != 0) {
      return ensureOrder(patchCompare)
    }

    def typeCompare = lSemVer.type.order <=> rSemVer.type.order
    if (typeCompare != 0) {
      return ensureOrder(typeCompare)
    }

    def timestampCompare = lSemVer.timestamp as Long <=> rSemVer.timestamp as Long
    return ensureOrder(timestampCompare)
  }

  @NonCPS
  private int comparePreferredBranches(SemanticVersion lSemVer, SemanticVersion rSemVer) {
    if (!preferredBranches.contains(lSemVer.branch) && !preferredBranches.contains(rSemVer.branch)) {
      return 0
    } else if (preferredBranches.contains(lSemVer.branch) && !preferredBranches.contains(rSemVer.branch)) {
      return 1
    } else if (!preferredBranches.contains(lSemVer.branch) && preferredBranches.contains(rSemVer.branch)) {
      return -1
    } else {
      return -(preferredBranches.indexOf(lSemVer.branch) <=> preferredBranches.indexOf(rSemVer.branch))
    }
  }

  @NonCPS
  private int ensureOrder(int value) {
    return order == order.ASC ? value : -value
  }

}

