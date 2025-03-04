package org.folio.models.module

enum ModuleType {
  BACKEND, FRONTEND, EDGE, OKAPI, MGR, SIDECAR, KONG, KEYCLOAK

  /**
   * Determines the module type based on its moduleName.
   *
   * @param moduleName The moduleName of the module.
   * @return The ModuleType corresponding to the given moduleName.
   * @throws Exception if the module type is unknown.
   */
   static ModuleType determineModuleType(String moduleName) {
    switch (moduleName) {
      case ~/^mod-.*/:
        return BACKEND
      case ~/^edge-.*/:
        return EDGE
      case ~/^folio_.*/:
        return FRONTEND
      case ~/^mgr-.*/:
        return MGR
      case ~/.*sidecar.*/:
        return SIDECAR
      case 'okapi':
        return OKAPI
      case 'folio-kong':
        return KONG
      case 'folio-keycloak':
        return KEYCLOAK
      default:
        throw new Exception("Type of ${moduleName} module is unknown")
    }
  }
}
