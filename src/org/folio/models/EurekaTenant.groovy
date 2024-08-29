package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import hudson.util.Secret

/**
 * EurekaTenant class representing a tenant configuration for Eureka.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaTenant extends OkapiTenant {

  String uuid = ""

  /**
   * Keycloak client identifier.
   */
  String clientId = "sidecar-module-access-client"

  /**
   * Some secret phrase to authenticate in Keycloak.
   * Is stored in AWS SSM Parameter Store or Hashicorp Vault.
   */
  Secret clientSecret

  /** Modules that are installed for the tenant. */
  EurekaModules modules

  Map<String, String> applications = [:]

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   */
  EurekaTenant(String tenantId) {
    super(tenantId)
  }

  EurekaTenant(String tenantId, String uuid) {
    super(tenantId)
    this.uuid = uuid
  }

  EurekaTenant withUUID(String uuid){
    this.uuid = uuid
    return this
  }

  EurekaTenant withClientSecret(Secret secret){
    clientSecret = secret
    return this
  }

  Map toMap(){
    Map ret = [
      name: tenantId,
      description: tenantDescription
    ]

    if(tenantId.trim())
      ret.put("id", uuid)

    return ret
  }

  static EurekaTenant getTenantFromContent(Map content){
    return new EurekaTenant(content.name as String, content.id as String)
      .withTenantName(content.name as String)
      .withTenantDescription(content.description as String) as EurekaTenant
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "EurekaTenant",
      "uuid": "$uuid"
      "tenantId": "$tenantId",
      "tenantName": "$tenantName",
      "tenantDescription": "$tenantDescription"
    """
  }
}
