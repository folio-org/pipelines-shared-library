package org.folio.models

import org.folio.models.module.EurekaModule
import org.folio.models.module.FolioModule

/**
 * The EurekaInstallJson class represents a collection of Eureka modules for installation.
 * It provides methods to manipulate and retrieve details about these modules,
 * facilitating the generation of installation JSON configurations.
 */
class EurekaInstallJson extends FolioInstallJson {

  List<EurekaModule> installJsonObject = []

  /**
   * Default constructor for EurekaInstallJson.
   */
  EurekaInstallJson() {}

  /**
   * Initializes the installJsonObject with a list of modules defined in the provided JSON-like structure.
   *
   * @param installJsonOrig a list of maps containing module details (id and action).
   * @return the instance of FolioInstallJson for method chaining.
   */
  EurekaInstallJson setInstallJsonObject(List<Map<String, String>> installJsonOrig, def context = null) {
    if(context)
      context.println("I'm in EurekaInstallJson.setInstallJsonObject")

    super.setInstallJsonObject(installJsonOrig, context)

    return this
  }
}
