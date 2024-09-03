package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

import java.util.regex.Matcher

/**
 * The Modules class is responsible for managing information about modules
 * and allows operations such as setting installation JSON, removing modules,
 * and generating installation JSON from module IDs.
 */
class Modules {

  /** Prefix used to distinguish edge modules. */
  protected static final String EDGE_PREFIX = "edge-"

  /** Prefix used to distinguish backend modules. */
  protected static final String MOD_PREFIX = "mod-"

  /** Stores the JSON data representing the modules that need to be installed. */
  public List installJson

  /** A map of all modules. */
  Map<String, String> allModules

  /** A map of all backend modules. */
  Map backendModules

  /** A map of all edge modules. */
  Map edgeModules

  /** A list representing the modules that need to be discovered. */
  List discoveryList

  /**
   * Default constructor for creating an instance of the Modules class.
   */
  public Modules() {}

  /**
   * Sets the installation JSON from a string or a list and initializes
   * all modules, backend modules, edge modules, and discovery list.
   *
   * @param installJson the installation JSON as a string or a list.
   * @throws IllegalArgumentException if installJson is not a string or a list,
   *                                  or if installJson is null.
   */
  void setInstallJson(Object installJson) {
    if (installJson == null) {
      throw new IllegalArgumentException("installJson cannot be null")
    }
    if (installJson instanceof String) {
      this.installJson = new JsonSlurper().parseText(installJson) as List
    } else if (installJson instanceof List) {
      this.installJson = installJson
    } else {
      throw new IllegalArgumentException("installJson must be a JSON string or a List<Map>. Received: ${installJson.getClass()}")
    }

    this.allModules = [:]
    this.backendModules = [:]
    this.edgeModules = [:]
    this.discoveryList = []

    this.installJson.id.each { id ->
      Matcher match = (id =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)
      if (match) {
        def (_, module_name, version) = match[0]
        this.allModules[module_name] = version
      }
      match.reset()
    }

    this.edgeModules = this.allModules.findAll { name, version -> name.startsWith(EDGE_PREFIX) }
    this.backendModules = this.allModules.findAll { name, version -> name.startsWith(MOD_PREFIX) }
    this.backendModules.collect { name, version ->
      String id = "${name}-${version}"
      String url = "http://${name}"
      this.discoveryList << [srvcId: id, instId: id, url: url]
    }
  }

  @NonCPS
  List getInstallJson(){
    return installJson
  }

  /**
   * Adds a new module to the installJson list.
   * The module is represented as a map with an 'id' key (set to the moduleId argument)
   * and an 'action' key (set to 'enable').
   *
   * @param moduleId the ID of the module to add
   */
  void addModule(String moduleId) {
    Map<String, String> module = [
      'id'    : moduleId,
      'action': 'enable'
    ]
    this.installJson << module
    this.setInstallJson(this.installJson)
  }

  /**
   * Adds multiple new modules to the installJson list.
   * It does this by calling the addModule method for each ID in the moduleIds argument.
   *
   * @param moduleIds the list of module IDs to add
   */
  void addModules(List<String> modulesIds) {
    modulesIds.each { moduleId ->
      boolean moduleExist = this.installJson.any { it.id.startsWith(extractModuleNameFromId(moduleId)) }
      if (moduleExist) {
        return
      }
      Map<String, String> module = [
        'id'    : moduleId,
        'action': 'enable'
      ]
      this.installJson << module
    }
    this.setInstallJson(this.installJson)
  }

  /**
   * Removes a module by its name.
   *
   * @param moduleName the name of the module to be removed.
   */
  void removeModule(String moduleName, def context = null) {
    if(context) {
      this.installJson.each { it ->
        context.println("I'm inside Modules.removeModule moduleName: $moduleName module: $it")

        if(it !=~ /${moduleName}-\d+\..*/)
          context.println("false")
        else
          context.println("true")
      }
    }

    context.println("this.installJson before moduleName: $moduleName installJson: $installJson")

    this.installJson = this.installJson.findAll { it !=~ /${moduleName}-\d+\..*/ }

    context.println("this.installJson after moduleName: $moduleName installJson: $installJson")

    this.setInstallJson(this.installJson)

    context.println("this.installJson after moduleName: $moduleName modules: ${this}")
  }

  /**
   * Removes modules by its names.
   *
   * @param modulesNames the list of names of the modules to be removed.
   */
  void removeModules(List<String> modulesNames) {
    modulesNames.each { moduleName ->
      this.installJson = this.installJson.findAll { it.id?.startsWith(moduleName) != true }
    }
    this.setInstallJson(this.installJson)
  }

  /**
   * Generates a list of maps for installation JSON from a list of module IDs and an action.
   *
   * @param moduleIds the list of module IDs.
   * @param action the action to be taken.
   * @return a list of maps with the module ID and the action.
   */
  static List<Map<String, String>> generateInstallJsonFromIds(List<String> moduleIds, String action) {
    return moduleIds.collect { moduleId -> [id: moduleId, action: action] }
  }

  /**
   * Fetch module version from registry by module name.
   *
   * @param moduleName the string of module name.
   * @return a string with the module ID.
   */
  String getModuleVersion(String moduleName, boolean releaseVersion = false) {
    String versionType = 'only'
    if (releaseVersion) {
      versionType = 'false'
    }
    URLConnection registry = new URL("https://folio-registry.dev.folio.org/_/proxy/modules?filter=${moduleName}&preRelease=${versionType}&latest=1").openConnection()
    if (registry.getResponseCode().equals(200)) {
      return new JsonSlurperClassic().parseText(registry.getInputStream().getText())*.id.first()
    } else {
      throw new RuntimeException("Unable to get ${moduleName} version. Url: ${registry.getURL()}. Status code: ${registry.getResponseCode()}.")
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
  static String extractModuleNameFromId(String moduleId) {
    Matcher matcher = getMatcher(moduleId)
    if (matcher) {
      return matcher.group(1)
    } else {
      throw new InputMismatchException("Not able to extract module name. Module id '$moduleId' has wrong format")
    }
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "Modules",
      "installJson": "$installJson",
      "allModules": "$allModules"
    """
  }
}
