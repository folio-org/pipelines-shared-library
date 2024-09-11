package org.folio.models

/**
 * This class defines the parameters required for application entitlement query.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaRequestParams extends InstallRequestParams {

  /** Remove all in case of rollback. */
  boolean purgeOnRollback = false

  /**
   * Defines if module data must be purged on rollback.
   * @param purge The new value for the purgeOnRollback flag.
   * @return The updated EurekaRequestParams object.
   */
  EurekaRequestParams withPurgeOnRollback(boolean purge) {
    this.purgeOnRollback = purge
    return this
  }

}
