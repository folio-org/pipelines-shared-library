package org.folio.models.module

import java.util.regex.Matcher

/**
 * Represents a Eureka module with associated properties and methods to manage
 * its details, version retrieval, and type determination.
 */
class EurekaModule extends FolioModule {

  // Default constructor
  EurekaModule() {}

  /**
   * Loads module details based on the provided module ID and optional action.
   * This method extracts the name and version from the ID, determines the
   * module type and version type, and sets discovery information if applicable.
   *
   * @param id The unique identifier for the module (not null or empty).
   * @param action Optional action associated with the module.
   * @throws IllegalArgumentException if the ID is null or empty.
   * @throws InputMismatchException if the module ID format is incorrect.
   * @return This instance of EurekaModule for method chaining.
   */
  @Override
  EurekaModule loadModuleDetails(String id, String action = null) {
    super.loadModuleDetails(id, action)

    // Set discovery information for backend modules
    if (this.type == ModuleType.BACKEND || this.type == ModuleType.EDGE) {
      String location = "http://${this.name}${this.type == ModuleType.BACKEND ? ':8082' : ''}"

      this.discovery = [id: "${name}-${version}", name: name, version: version, location: location]
    }

    return this
  }
}
