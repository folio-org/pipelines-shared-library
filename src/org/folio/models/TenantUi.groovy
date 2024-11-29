package org.folio.models

import org.folio.models.module.FolioModule
import org.folio.rest_v2.Constants

/**
 * This class represents a TenantUi, providing various properties related to a tenant's user interface.
 */
class TenantUi implements Cloneable {

  /** Default name for the image. */
  private static final String IMAGE_NAME = 'ui-bundle'

  /** Identifier of the tenant. */
  String tenantId

  /** Domain of the tenant. */
  String domain

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

  List<FolioModule> customUiModules = []

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
   * Clones the TenantUi object. Performs a deep clone of the list customUiModules.
   * @return A new TenantUi object with copied properties.
   */
  @Override
  TenantUi clone() {
    // Create a shallow clone of the current object
    TenantUi cloned = (TenantUi) super.clone()

    // Deep clone the customUiModules list to ensure no shared references
    cloned.customUiModules = this.customUiModules.collect { it.clone() }

    return cloned
  }
}
