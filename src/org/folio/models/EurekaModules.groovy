package org.folio.models

import com.cloudbees.groovy.cps.NonCPS

/**
 * The Modules class is responsible for managing information about modules
 * and allows operations such as setting installation JSON, removing modules,
 * and generating installation JSON from module IDs.
 */
class EurekaModules extends Modules {

  /** Prefix used to distinguish mgr modules. */
  private static final String MGR_PREFIX = "mgr-"

  /** A map of all mgr modules. */
  Map mgrModules

  /**
   * Default constructor for creating an instance of the Modules class.
   */
  public EurekaModules() {
    super()
  }

  /**
   * Sets the installation JSON from a string or a list and initializes
   * all modules, backend modules, edge modules, and discovery list.
   *
   * @param installJson the installation JSON as a string or a list.
   * @throws IllegalArgumentException if installJson is not a string or a list,
   *                                  or if installJson is null.
   */
  @Override
  void setInstallJson(Object installJson) {
    super.setInstallJson(installJson)

    this.mgrModules = this.allModules.findAll { name, version -> name.startsWith(MGR_PREFIX) }

    updateDiscoveryList()
  }

  void updateDiscoveryList(List restrictionList = null){
    discoveryList = []

    backendModules.each { name, version ->
      String id = "${name}-${version}"
      String location = "http://${name}:8082"

      if(!(restrictionList && !restrictionList.find({ value -> value == id })))
        discoveryList << [id: id, name: name, version: version, location: location]
    }

    edgeModules.each { name, version ->
      String id = "${name}-${version}"
      String location = "http://${name}"

      if(!(restrictionList && !restrictionList.find({ value -> value == id })))
        discoveryList << [id: id, name: name, version: version, location: location]
    }
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "EurekaModules",
      "installJson": "$installJson",
      "allModules": "$allModules",
      "backendModules": "$backendModules",
      "edgeModules": "$edgeModules",
      "mgrModules": "$mgrModules",
      "discoveryList": "$discoveryList"
    """
  }
}
