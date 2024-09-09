package org.folio.models


import org.folio.models.module.FolioModule
import org.folio.models.module.ModuleType

class FolioInstallJson {

  List<FolioModule> installJsonObject = []

  FolioInstallJson() {}

  List<FolioModule> setInstallJsonObject(List<Map<String, String>> installJsonOrig) {
    this.installJsonObject = installJsonOrig.collect { module ->
      new FolioModule().loadModuleDetails(module['id'], module['action'])
    }

    return this
  }

  void addModule(String id, String action = null) {
    this.installJsonObject.add(new FolioModule().loadModuleDetails(id, action))
  }

  void addModulesWithActions(List<Map<String, String>> modules) {
    modules.each { module ->
      addModule(module['id'], module['action'])
    }
  }

  void addModulesWithSameAction(List<String> moduleIds, String action) {
    moduleIds.each { moduleId ->
      addModule(moduleId, action)
    }
  }

  void removeModuleByName(String name) {
    this.installJsonObject.removeAll { module ->
      module.name == name
    }
  }

  void removeModulesByName(List<String> names) {
    names.each { name ->
      removeModuleByName(name)
    }
  }

  List getBackendModules() {
    return _getModulesByType(ModuleType.BACKEND)
  }

  List getEdgeModules() {
    return _getModulesByType(ModuleType.EDGE)
  }

  List getUiModules() {
    return _getModulesByType(ModuleType.FRONTEND)
  }

  FolioModule getOkapiModule() {
    return this.installJsonObject.find { module ->
      module.getType() == ModuleType.OKAPI
    }
  }

  List getInstallJson() {
    return _convertToInstallJson(this.installJsonObject)
  }

  List getBackendModulesInstallJson() {
    return _convertToInstallJson(getBackendModules())
  }

  List getEdgeModulesInstallJson() {
    return _convertToInstallJson(getEdgeModules())
  }

  List getUiModulesInstallJson() {
    return _convertToInstallJson(getUiModules())
  }

  private List<FolioModule> _getModulesByType(ModuleType type) {
    return this.installJsonObject.findAll { module ->
      module.getType() == type
    }
  }

  private List<Map<String, String>> _convertToInstallJson(List<FolioModule> modules) {
    return modules.collect { module ->
      _validateAction(module)
      [id: module.id, action: module.action]
    }
  }

  @SuppressWarnings('GrMethodMayBeStatic')
  private void _validateAction(FolioModule module) {
    if (!module.action?.trim()) {
      throw new IllegalArgumentException("Action for module '${module.id}' is null or empty")
    }
  }
}
