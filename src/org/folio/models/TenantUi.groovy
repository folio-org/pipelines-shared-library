package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.module.EurekaModule
import org.folio.models.module.FolioModule
import org.folio.rest_v2.Constants

/**
 * This class represents a TenantUi, providing various properties related to a tenant's user interface.
 */
class TenantUi implements Cloneable {

  /** Default name for the image. */
  static final String IMAGE_NAME = 'ui-bundle'

  /** Identifier of the tenant. */
  String tenantId

  /** Domain of the tenant. */
  String domain

  /** Kong domain of the tenant. */
  String kongDomain

  /** Keycloak domain of the tenant. */
  String keycloakDomain

  /** Branch name of the tenant's repository. */
  String branch

  /** Hash of the tenant's repository. */
  String hash

  /** Tag for the tenant's image. */
  String tag

  /** Name of the tenant's image. */
  String imageName

  /** Workspace of the tenant. */
  String workspace

  @Deprecated
  List<FolioModule> customUiModules = []

  /** Flag indicating whether Consortia is enabled for the tenant. */
  boolean isConsortia = false

  /** Flag indicating whether the tenant has a single UI for Consortia. */
  boolean isConsortiaSingleUi = false

  /** List of Eureka modules to be added to the tenant's UI. */
  List<EurekaModule> addUIComponents = []

  /** List of Eureka modules to be removed from the tenant's UI. */
  List<EurekaModule> removeUIComponents = []

  /**
   * Constructor that sets the workspace, hash, and branch for the TenantUi.
   * @param workspace The workspace of the tenant.
   * @param hash The hash of the tenant's repository.
   * @param branch The branch name of the tenant's repository.
   */
  TenantUi(String workspace, String hash, String branch) {
    this.workspace = workspace
    this.hash = hash
    this.branch = branch
  }

  /**
   * Sets the tenantId and updates the tag and image name accordingly.
   * @param tenantId Identifier of the tenant.
   */
  void setTenantId(String tenantId) {
    this.tenantId = tenantId
    updateTagAndImageName()
  }

  /**
   * Updates the tag and image name for this TenantUi.
   * This method is invoked when the tenantId is set.
   */
  private void updateTagAndImageName() {
    if (this.tenantId && this.hash) {
      this.tag = "${this.workspace}.${this.tenantId}.${this.hash.take(7)}"
      this.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${IMAGE_NAME}:${this.tag}"
    }
  }

  /**
   * Clones the TenantUi object.
   * Creates new lists for customUiModules, addUIComponents, and removeUIComponents
   * to prevent shared references between cloned instances.
   * Note: Performs shallow copy of list contents (module objects are shared).
   *
   * @return A new TenantUi object with copied properties and independent lists.
   * @throws AssertionError if cloning is not supported.
   */
  @NonCPS
  @Override
  TenantUi clone() {
    try {
      // Create a shallow clone of the current object
      TenantUi cloned = (TenantUi) super.clone()

      // Create new lists to prevent shared references between clones
      // Note: Module objects themselves are shared (shallow copy)
      cloned.customUiModules = this.customUiModules ? new ArrayList<>(this.customUiModules) : []
      cloned.addUIComponents = this.addUIComponents ? new ArrayList<>(this.addUIComponents) : []
      cloned.removeUIComponents = this.removeUIComponents ? new ArrayList<>(this.removeUIComponents) : []

      return cloned
    } catch (CloneNotSupportedException e) {
      throw new AssertionError('Cloning not supported for TenantUi', e)
    }
  }

  /**
   * Returns a string representation of this TenantUi object for debugging purposes.
   * @return A JSON-like string representation of the TenantUi properties.
   */
  @NonCPS
  @Override
  String toString() {
    return """
      "class_name": "TenantUi",
      "tenantId": "${tenantId ?: 'null'}",
      "domain": "${domain ?: 'null'}",
      "kongDomain": "${kongDomain ?: 'null'}",
      "keycloakDomain": "${keycloakDomain ?: 'null'}",
      "branch": "${branch ?: 'null'}",
      "hash": "${hash ?: 'null'}",
      "tag": "${tag ?: 'null'}",
      "imageName": "${imageName ?: 'null'}",
      "workspace": "${workspace ?: 'null'}",
      "isConsortia": ${isConsortia},
      "isConsortiaSingleUi": ${isConsortiaSingleUi},
      "customUiModules": ${customUiModules ? customUiModules.size() : 0} modules (deprecated),
      "addUIComponents": ${addUIComponents ? addUIComponents.size() : 0} components,
      "removeUIComponents": ${removeUIComponents ? removeUIComponents.size() : 0} components
    """
  }

}
