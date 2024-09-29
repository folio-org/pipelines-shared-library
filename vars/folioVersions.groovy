import groovy.json.JsonSlurperClassic
import org.folio.Constants

import java.util.regex.Matcher

Map compareInstallJsons(List currentInstallJson, List newInstallJson) {
  Map resultMap = [:]
  ComparisonResult.values().each { resultMap[it.name()] = [] }

  newInstallJson.each { newModule ->
    String newModuleName = extractName(newModule.id)
    Map currentModule = currentInstallJson.find {
      extractName(it.id) == newModuleName
    }
    if (currentModule) {
      ComparisonResult result = compare(currentModule.id, newModule.id, false)
      resultMap[result.name()] << newModule
    }
  }
  return resultMap
}

/**
 * Compare two module versions to determine if an upgrade, downgrade, or no change is required.
 *
 * @param currentModuleId Current version identifier of the module.
 * @param newModuleId New version identifier of the module.
 * @param debug (Optional) A flag to enable debug logging.
 * @return A string representing the comparison result ('equal', 'upgrade', or 'downgrade').
 */
ComparisonResult compare(String currentModuleId, String newModuleId, boolean debug = false) {

  // Display debug information if enabled
  if (debug) {
    println('=' * 10)
    println("Current module ID: ${currentModuleId}")
    println("New module ID: ${newModuleId}")
  }

  // Extract module names from the id
  String currentModuleName = extractName(currentModuleId)
  String newModuleName = extractName(newModuleId)

  // Ensure we're comparing versions of the same module
  assert currentModuleName == newModuleName: 'Only the same modules comparison of versions allowed.'

  // Extract version details for each module
  List currentModuleVersion = convertVersionListStructuredList(extractVersion(currentModuleId))
  List newModuleVersion = convertVersionListStructuredList(extractVersion(newModuleId))

  /**
   *  Step 1: Compare if both versions are release
   *  Example:
   *  mod-users-1.2.3
   *  mod-users-1.3.4
   */
  if (currentModuleVersion.size() == 1 && newModuleVersion.size() == 1) {
    return compareReleases(currentModuleVersion[0], newModuleVersion[0])
  }

  /**
   *  Step2: Compare if one or both versions not release
   *  Example:
   *  mod-users-1.2.3-SNAPSHOT.23
   *  mod-users-1.2.4
   */
  if (currentModuleVersion.size() > 1 || newModuleVersion.size() > 1) {
    ComparisonResult result = compareReleases(currentModuleVersion[0], newModuleVersion[0])
    if (result != ComparisonResult.EQUAL) return result
  }

  /**
   *  Step3: Compare if one or both versions not release and have same base version
   *  Example:
   *  mod-users-1.2.4-SNAPSHOT.23
   *  mod-users-1.2.4
   *  or
   *  mod-users-1.2.4-SNAPSHOT.23
   *  mod-users-1.2.4-SNAPSHOT.26
   *  or
   *  mod-users-1.2.4-SNAPSHOT.ef63d08
   *  mod-users-1.2.4-SNAPSHOT.26
   */
  def currentModuleIdentifier = currentModuleVersion[1]
  def newModuleIdentifier = newModuleVersion[1]

  if (!currentModuleIdentifier && newModuleIdentifier) {
    return ComparisonResult.UPGRADE
  } else if (currentModuleIdentifier && !newModuleIdentifier) {
    return ComparisonResult.DOWNGRADE
  } else if (currentModuleIdentifier == newModuleIdentifier) {
    return ComparisonResult.EQUAL
  }

  if (isNumeric(currentModuleIdentifier) && isNumeric(newModuleIdentifier)) {
    if (currentModuleIdentifier < newModuleIdentifier) {
      return ComparisonResult.UPGRADE
    } else if (currentModuleIdentifier > newModuleIdentifier) {
      return ComparisonResult.DOWNGRADE
    }
  } else if (!isNumeric(currentModuleIdentifier) && !isNumeric(newModuleIdentifier)) {
    return compareGitHashes(currentModuleName, currentModuleIdentifier, newModuleIdentifier)
  } else {
    return ComparisonResult.UPGRADE
  }
}

static Matcher getMatcher(String moduleId) {
  // This regular expression matches strings in the format of "mod-pubsub-<version>" or "mod-pubsub-<version>-SNAPSHOT.<sub-version>"
  return moduleId =~ /^([a-z_\d\-]+)-(\d+\.\d+\.\d+)(?:-SNAPSHOT(?:\.(\w+))?)?$/
}

/**
 * Extracts the module name from the given moduleId.
 *
 * @param moduleId The full identifier string.
 * @return The extracted module name.
 */
static String extractName(String moduleId) {
  Matcher matcher = getMatcher(moduleId)
  if (matcher) {
    return matcher.group(1)
  } else {
    throw new InputMismatchException("Not able to extract module name. Module id '$moduleId' has wrong format")
  }
}

/**
 * Extracts the version details from the given moduleId.
 *
 * @param moduleId The full identifier string.
 * @return A list containing version and possibly a snapshot identifier.
 */
static List extractVersion(String moduleId) {
  Matcher matcher = getMatcher(moduleId)
  if (matcher.groupCount() > 3) {
    throw new Exception("Unhandled exception: More than 3 matcher groups found. Module ID: ${moduleId}")
  }
  if (matcher) {
    // If there is a match, return the version and identifier (if it exists) as a list
    String version = matcher.group(2)
    String identifier = matcher.group(3) ?: '0'
    return identifier ? [version, identifier] : [version]
  } else {
    throw new InputMismatchException("Not able to extract module versions. Module id '${moduleId}' has wrong format")
  }
}

/**
 * Converts the given version details into a list with numeric values.
 *
 * @param version The version details.
 * @return A list with numeric representation of the version details.
 */
static List convertVersionListStructuredList(List version) {
  return version.collect {
    if (it.contains('.')) {
      it.split('\\.').collect { part -> stringToNumeric(part) }
    } else {
      try {
        stringToNumeric(it)
      } catch (Exception e) {
        it
      }
    }
  }
}

/**
 * Compares two release versions to determine their relative ordering.
 *
 * @param currentRelease The current release version.
 * @param newRelease The new release version.
 * @return A string representing the comparison result ('equal', 'upgrade', or 'downgrade').
 */
static ComparisonResult compareReleases(List currentRelease, List newRelease) {
  for (int i = 0; i < currentRelease.size(); i++) {
    if (currentRelease[i] < newRelease[i]) {
      return ComparisonResult.UPGRADE
    } else if (currentRelease[i] > newRelease[i]) {
      return ComparisonResult.DOWNGRADE
    }
  }
  return ComparisonResult.EQUAL
}

/**
 * Compares two Git hashes using the GitHub API to determine their relative ordering.
 *
 * @param moduleName The name of the module.
 * @param currentHash The current Git hash.
 * @param newHash The new Git hash.
 * @return A string representing the comparison result ('equal', 'upgrade', or 'downgrade').
 */
static ComparisonResult compareGitHashes(String moduleName, String currentHash, String newHash) {
  URL url = new URL("${Constants.FOLIO_GITHUB_REPOS_URL}/${moduleName}/compare/${currentHash}...${newHash}")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  withCredentials([string(credentialsId: 'id-jenkins-github-personal-token', variable: 'token')]) {
    conn.setRequestProperty("Authorization", "Bearer \$token")
  }

  try {
    int responseCode = conn.responseCode
    if (responseCode == HttpURLConnection.HTTP_OK) {
      String status = new JsonSlurperClassic().parseText(conn.inputStream.text).status
      switch (status) {
        case 'identical':
          return ComparisonResult.EQUAL
        case 'ahead':
          return ComparisonResult.UPGRADE
        case 'behind':
          return ComparisonResult.DOWNGRADE
        default:
          throw new Exception("Unknown status received: $status")
      }
    } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new Exception("Invalid hash(es) provided for comparison.")
    } else {
      throw new Exception("Unexpected response from server: ${conn.responseMessage}")
    }
  } finally {
    conn.disconnect()
  }
}

/**
 * Converts a string representation to its numeric equivalent (either Integer or Long).
 *
 * @param str The string to be converted.
 * @return The numeric representation of the string.
 */
static def stringToNumeric(String str) {
  try {
    return str.toInteger()
  } catch (NumberFormatException eInt) {
    try {
      return str.toLong()
    } catch (NumberFormatException eLong) {
      throw new Exception(eLong)
    }
  }
}


/**
 * Checks if the provided value is of numeric type (either Integer or Long).
 *
 * @param value The value to be checked.
 * @return True if the value is numeric, otherwise false.
 */
static boolean isNumeric(value) {
  return value instanceof Integer || value instanceof Long
}

/**
 * Enumeration defining possible results of a module version comparison.
 *
 * The `ComparisonResult` enum contains three possible states that can result
 * from comparing two version strings, particularly pertaining to software
 * module versions.
 *
 * Values:
 * - `UPGRADE`: Indicates that the new version is higher than the current version.
 * - `DOWNGRADE`: Indicates that the new version is lower than the current version.
 * - `EQUAL`: Indicates that the new version and the current version are identical.
 *
 * Example Usage:
 * ```groovy
 * if (compare(currentModuleId, newModuleId) == ComparisonResult.UPGRADE) {
 *     println("An upgrade is available!")
 * }
 * ```
 *
 * This enum is utilized throughout the version comparison code to denote
 * the relationship between two versions being compared, aiding in decision-making
 * processes about whether to upgrade, downgrade, or maintain the current version.
 *
 * Note: Ensure you understand the specific criteria used in your version comparison
 * functions to interpret these results accurately in context.
 */
enum ComparisonResult { //TODO move enum to separate structure
  UPGRADE,   // New version is higher than the current version
  DOWNGRADE, // New version is lower than the current version
  EQUAL      // New version and current version are identical
}

static def getComparisonResultValues() {
  return ComparisonResult.values()
}
