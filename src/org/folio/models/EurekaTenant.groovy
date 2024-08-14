package org.folio.models

import hudson.util.Secret

/**
 * EurekaTenant class representing a tenant configuration for Eureka.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaTenant extends Tenant {
  /**
   * Keycloak client identifier.
   */
  String clientId

  /**
   * Some secret phrase to authenticate in Keycloak.
   * Is stored in AWS SSM Parameter Store or Hashicorp Vault.
   */
  Secret clientSecret
}
