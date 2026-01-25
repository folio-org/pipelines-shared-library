package org.folio.models

/**
 * This class defines the parameters required for application entitlement query.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaRequestParams extends InstallRequestParams {

  /** Remove all in case of rollback. */
  boolean purgeOnRollback = false

  /** Process the request asynchronously. */
  boolean async = false

  /**
   * Defines if module data must be purged on rollback.
   * @param purge The new value for the purgeOnRollback flag.
   * @return The updated EurekaRequestParams object.
   */
  EurekaRequestParams withPurgeOnRollback(boolean purge) {
    this.purgeOnRollback = purge
    return this
  }

  /**
   * Defines if the request should be processed asynchronously.
   * @param async The new value for the async flag.
   * @return The updated EurekaRequestParams object.
   */
  EurekaRequestParams withAsync(boolean async) {
    this.async = async
    return this
  }
}
