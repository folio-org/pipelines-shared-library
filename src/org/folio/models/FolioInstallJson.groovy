package org.folio.models

import org.folio.models.module.FolioModule
import org.folio.models.module.ModuleType

/**
 * The FolioInstallJson class represents a collection of Folio modules for installation.
 * It provides methods to manipulate and retrieve details about these modules,
 * facilitating the generation of installation JSON configurations.
 */
class FolioInstallJson<T extends FolioModule> {

  List<T> installJsonObject = []
  Class<T> moduleType

  FolioInstallJson(List<Map<String, String>> installJsonOrig, Class<T> moduleType) {
    this(moduleType)

    setInstallJsonObject(installJsonOrig)
  }

  FolioInstallJson(Class<T> moduleType) {
    this.moduleType = moduleType
  }

  /**
   * Initializes the installJsonObject with a list of modules defined in the provided JSON-like structure.
   *
   * @param installJsonOrig a list of maps containing module details (id and action).
   * @return the instance of FolioInstallJson for method chaining.
   */
  FolioInstallJson<T> setInstallJsonObject(List<Map<String, String>> installJsonOrig, def context = null) {
    this.installJsonObject = installJsonOrig.collect(({
      module ->
//        if(context)
//          context.println(module)

        moduleType.getDeclaredConstructor().newInstance()
        .loadModuleDetails(module['id'] as String, module['action'] as String)
    } as Closure<T>))

    return this
  }

  /**
   * Adds a single module to the installJsonObject.
   *
   * @param id the ID of the module to add.
   * @param action the action to perform on the module (optional).
   */
  void addModule(String id, String action = null) {
    this.installJsonObject.add(moduleType.getDeclaredConstructor().newInstance().loadModuleDetails(id, action) as T)
  }

  /**
   * Adds multiple modules with their respective actions to the installJsonObject.
   *
   * @param modules a list of maps where each map contains the module ID and its action.
   */
  void addModulesWithActions(List<Map<String, String>> modules) {
    modules.each { module -> addModule(module['id'], module['action']) }
  }

  /**
   * Adds multiple modules with the same action to the installJsonObject.
   *
   * @param moduleIds a list of module IDs to be added.
   * @param action the action to perform on all specified modules.
   */
  void addModulesWithSameAction(List<String> moduleIds, String action) {
    moduleIds.each { moduleId -> addModule(moduleId, action) }
  }

  /**
   * Removes a module from the installJsonObject by its name.
   *
   * @param name the name of the module to remove.
   */
  void removeModuleByName(String name) {
    this.installJsonObject.removeAll { module -> module.name == name }
  }

  /**
   * Removes multiple modules from the installJsonObject by their names.
   *
   * @param names a list of names corresponding to the modules to remove.
   */
  void removeModulesByName(List<String> names) {
    names.each { name -> removeModuleByName(name) }
  }

  /**
   * Retrieves all backend modules from the installJsonObject.
   *
   * @return a list of backend FolioModules.
   */
  List<T> getBackendModules() {
    return _getModulesByType(ModuleType.BACKEND)
  }

  /**
   * Retrieves all edge modules from the installJsonObject.
   *
   * @return a list of edge FolioModules.
   */
  List<T> getEdgeModules() {
    return _getModulesByType(ModuleType.EDGE)
  }

  /**
   * Retrieves all edge modules from the installJsonObject.
   *
   * @return a list of edge FolioModules.
   */
  List<T> getMgrModules() {
    return _getModulesByType(ModuleType.MGR)
  }

  /**
   * Retrieves sidecars from the installJsonObject.
   *
   * @return a list of edge FolioModules.
   */
  List<T> getSidecars() {
    return _getModulesByType(ModuleType.SIDECAR)
  }

  /**
   * Retrieves all UI modules from the installJsonObject.
   *
   * @return a list of frontend FolioModules.
   */
  List<T> getUiModules() {
    return _getModulesByType(ModuleType.FRONTEND)
  }

  /**
   * Retrieves the Okapi module from the installJsonObject.
   *
   * @return the FolioModule representing Okapi, or null if not found.
   */
  T getOkapiModule() {
    return this.installJsonObject.find { module -> module.getType() == ModuleType.OKAPI }
  }

  T getModuleByName(String moduleName) {
    return this.installJsonObject.find { module -> module.getName() == moduleName }
  }

  /**
   * Converts the current installJsonObject into a list of maps suitable for installation JSON.
   *
   * @return a list of maps containing module IDs and their actions.
   */
  List<Map<String, String>> getInstallJson() {
    return _convertToInstallJson(installJsonObject)
  }

  /**
   * Retrieves Map contains all modules in the
   * module name : module version format
   *
   * @return a module name : module version map.
   */
  Map<String, String> getModuleVersionMap(){
    return installJsonObject.collectEntries {module ->
      [(module.name): module.version]
    }
  }

  /**
   * Generates a discovery list from the backend modules.
   *
   * @return a list of discovery details for the backend modules.
   */
  List getDiscoveryList(List<String> restrictionList = null) {
    return this.installJsonObject
      .findAll{module ->
        module?.discovery && !(restrictionList && !restrictionList.find({ value -> value == module.id }))
      }
      .collect { module -> module?.discovery }
  }

  /**
   * Retrieves the installation JSON for backend modules.
   *
   * @return a list of maps with the module IDs and their actions for backend modules.
   */
  List<Map<String, String>> getBackendModulesInstallJson() {
    return _convertToInstallJson(getBackendModules())
  }

  /**
   * Retrieves the installation JSON for edge modules.
   *
   * @return a list of maps with the module IDs and their actions for edge modules.
   */
  List<Map<String, String>> getEdgeModulesInstallJson() {
    return _convertToInstallJson(getEdgeModules())
  }

  /**
   * Retrieves the installation JSON for UI modules.
   *
   * @return a list of maps with the module IDs and their actions for UI modules.
   */
  List<Map<String, String>> getUiModulesInstallJson() {
    return _convertToInstallJson(getUiModules())
  }

  /**
   * Retrieves modules of a specific type from the installJsonObject.
   *
   * @param type the ModuleType to filter by.
   * @return a list of FolioModules matching the specified type.
   */
  private List<T> _getModulesByType(ModuleType type) {
    return this.installJsonObject.findAll { module -> module.getType() == type }
  }

  /**
   * Converts a list of FolioModules into a format suitable for installation JSON.
   *
   * @param modules the list of FolioModules to convert.
   * @return a list of maps containing module IDs and their actions.
   */
  private List<Map<String, String>> _convertToInstallJson(List<T> modules) {
    return modules.collect { module ->
      _validateAction(module)
      [id: module.id, action: module.action]
    }
  }

  /**
   * Validates that the action for a given module is not null or empty.
   *
   * @param module the FolioModule to validate.
   * @throws IllegalArgumentException if the action is null or empty.
   */
  @SuppressWarnings('GrMethodMayBeStatic')
  private void _validateAction(T module) {
    if (!module.action?.trim()) {
      throw new IllegalArgumentException("Action for module '${module.id}' is null or empty")
    }
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
}
