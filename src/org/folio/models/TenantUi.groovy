package org.folio.models

import org.folio.rest_v2.Constants

class TenantUi {
    private static final String IMAGE_NAME = 'ui-bundle'
    String tenantId
    String branch
    String hash
    String tag
    String imageName
    String workspace

    TenantUi(String workspace, String hash, String branch) {
        this.workspace = workspace
        this.hash = hash
        this.branch = branch
    }

    TenantUi withTenantId(String tenantId) {
        this.tenantId = tenantId
        updateTagAndImageName()
        return this
    }

    TenantUi withBranch(String branch) {
        this.branch = branch
        return this
    }

    TenantUi withHash(String hash) {
        this.hash = hash
        updateTagAndImageName()
        return this
    }

    private void updateTagAndImageName() {
        if (tenantId && hash) {
            this.tag = "${workspace}.${tenantId}.${hash.take(7)}"
            this.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${IMAGE_NAME}:${this.tag}"
        }
    }
}
