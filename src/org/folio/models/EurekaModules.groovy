package org.folio.models

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
  EurekaModules() {
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
  void setInstallJson(Object installJson, def context = null) {
    context.println("I'm in the EurekaModules.setInstallJson")

    super.setInstallJson(installJson, context)

    context.println("I'm in the EurekaModules.setInstallJson after super.setInstallJson(installJson)")

    this.mgrModules = [:]
    this.discoveryList = []

    this.mgrModules = this.allModules.findAll { name, version -> name.startsWith(MGR_PREFIX) }
    this.backendModules.collect { name, version ->
      String id = "${name}-${version}"
      String location = "http://${name}:8082"
      this.discoveryList << [id: id, name: name, version: version, location: location]
    }
  }
}
