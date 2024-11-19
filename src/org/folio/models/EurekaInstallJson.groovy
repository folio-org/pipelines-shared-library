package org.folio.models

import org.folio.models.module.EurekaModule

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
}
