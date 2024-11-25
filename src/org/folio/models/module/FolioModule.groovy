package org.folio.models.module

import org.folio.rest_v2.Constants
import org.folio.utilities.RestClient

import java.util.regex.Matcher

/**
 * Represents a Folio module with associated properties and methods to manage
 * its details, version retrieval, and type determination.
 */
class FolioModule {
  // Regular expression patterns for module name and version extraction
  private static final String MODULE_NAME_AND_VERSION_PATTERN = /^(.*?)-(\d+\.\d+\.\d+(?:-.+)?|\d+\.\d+\.\d+)$/
  private static final String SNAPSHOT_VERSION_CORE_PATTERN = /\d+\.\d+\.(\d+-SNAPSHOT\.|\d+0{5,6})/

  String id          // Unique identifier for the module
  String name        // Name of the module
  String version     // Version of the module
  String action      // Action associated with the module
  ModuleType type    // Type of the module (e.g., BACKEND, FRONTEND)
  String buildId     // Build ID for snapshot versions
  List descriptor    // Descriptor for the module (optional)
  Map discovery      // Discovery information for the module (e.g., URL)
  VersionType versionType // Type of version (e.g., SNAPSHOT, RELEASE)

  // Default constructor
  FolioModule() {}

  /**
   * Loads module details based on the provided module ID and optional action.
   * This method extracts the name and version from the ID, determines the
   * module type and version type, and sets discovery information if applicable.
   *
   * @param id The unique identifier for the module (not null or empty).
   * @param action Optional action associated with the module.
   * @throws IllegalArgumentException if the ID is null or empty.
   * @throws InputMismatchException if the module ID format is incorrect.
   * @return This instance of FolioModule for method chaining.
   */
  FolioModule loadModuleDetails(String id, String action = null) {
    // Validate the module ID
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("Module id cannot be null or empty")
    }

    this.id = id
    Matcher matcher = _getMatcher(this.id)

    // Extract name and version using the matcher
    if (matcher.matches()) {
      this.name = matcher.group(1)
      this.version = matcher.group(2)
      matcher.reset()
    } else {
      throw new InputMismatchException("Not able to extract module name. Module id '${this.id}' has wrong format")
    }

    // Determine module type and version type
    this.type = _determineModuleType(this.name)
    this.versionType = _determineVersionType(this.version)

    // If the version is a snapshot, extract the build ID
    if (this.versionType == VersionType.SNAPSHOT) {
      this.buildId = _extractModuleBuildId(this.version)
    }

    // Set discovery information for backend modules
    if (this.type == ModuleType.BACKEND) {
      String url = "http://${this.name}"
      this.discovery = [srvcId: this.id, instId: this.id, url: url]
    }

    // Set action if provided
    if (action != null && !action.trim().isEmpty()) {
      this.action = action
    }

    return this
  }

  /**
   * Retrieves the latest version of the module from the registry.
   *
   * @param isRelease Indicates whether to fetch the release version (default is false).
   * @return The latest version ID of the module.
   */
  String getLatestVersionFromRegistry(boolean isRelease = false) {
    return getLatestVersionFromRegistry(this.name, isRelease)
  }

  /**
   * Retrieves the latest version of a specified module from the registry.
   *
   * @param moduleName The name of the module to check.
   * @param isRelease Indicates whether to fetch the release version (default is false).
   * @return The latest version ID of the specified module.
   * @throws Exception if the module version cannot be retrieved.
   */
  String getLatestVersionFromRegistry(String moduleName, boolean isRelease = false) {
    final String URI = '/_/proxy/modules'
    String reqParams = "?filter=${moduleName}&order=desc&orderBy=id&latest=1"

    // Adjust request parameters based on module type
    switch (moduleName) {
      case ~/(mod|mgr)-.*/:
        reqParams += "&preRelease=${isRelease ? 'false' : 'only'}"
        break
      case ~/folio_.*/:
        reqParams += "&npmSnapshot=${isRelease ? 'false' : 'only'}"
        break
      default:
        break
    }

    String url = "${Constants.OKAPI_REGISTRY}${URI}${reqParams}"
    RestClient restClient = new RestClient(this)
    List response = restClient.get(url, [:]).body

    // Handle empty response
    if (response.isEmpty()) {
      throw new Exception("Can not get module version for ${moduleName}")
    }

    return response[0].id
  }

  /**
   * Creates a matcher for the given module ID based on the defined pattern.
   *
   * @param id The module ID to match against the pattern.
   * @return A Matcher object that can be used to extract module name and version.
   */
  private static Matcher _getMatcher(String id) {
    return id =~ MODULE_NAME_AND_VERSION_PATTERN
  }

  /**
   * Determines the module type based on its name.
   *
   * @param name The name of the module.
   * @return The ModuleType corresponding to the given name.
   * @throws Exception if the module type is unknown.
   */
  private static ModuleType _determineModuleType(String name) {
    switch (name) {
      case ~/^mod-.*/:
        return ModuleType.BACKEND
      case ~/^edge-.*/:
        return ModuleType.EDGE
      case ~/^folio_.*/:
        return ModuleType.FRONTEND
      case ~/^mgr-.*/:
        return ModuleType.MGR
      case 'okapi':
        return ModuleType.OKAPI
      default:
        throw new Exception("Type of ${name} module is unknown")
    }
  }

  /**
   * Determines the version type based on the version string.
   *
   * @param version The version string of the module.
   * @return The VersionType corresponding to the given version.
   */
  private static VersionType _determineVersionType(String version) {
    switch (version) {
      case ~/^\d+\.\d+\.(\d+-SNAPSHOT\.\d+|\d+000\d+)$/:
        return VersionType.SNAPSHOT
      case ~/^\d+\.\d+\.(\d+-SNAPSHOT\.\w+|\d+000\w+)$/:
        return VersionType.CUSTOM
      default:
        return VersionType.RELEASE
    }
  }

  /**
   * Extracts the build ID from the snapshot version string.
   *
   * @param version The version string of the module.
   * @return The build ID extracted from the version.
   */
  private static String _extractModuleBuildId(String version) {
    return version.replaceFirst(SNAPSHOT_VERSION_CORE_PATTERN, '')
  }
}
