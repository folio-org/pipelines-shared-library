package org.folio.models

import hudson.util.Secret

class OkapiUser {
    String username
    Secret password
    String firstName = ""
    String lastName = ""
    String email
    String uuid
    List<String> permissions = []
    String permissionsId
    String token
    String barcode
    String group

    OkapiUser(String username, Object password) {
        this.username = username
        this.password = password instanceof Secret ? password : Secret.fromString(password)
        this.email = "$username@example.org"
    }

    OkapiUser withFirstName(String firstName) {
        this.firstName = firstName
        return this
    }

    OkapiUser withLastName(String lastName) {
        this.lastName = lastName
        return this
    }

    OkapiUser withEmail(String email) {
        this.email = email
        return this
    }

    OkapiUser withUuid(String uuid) {
        this.uuid = uuid
        return this
    }

    OkapiUser withPermissions(List<String> permissions) {
        this.permissions = permissions
        return this
    }

    OkapiUser withPermissionsId(String permissionsId) {
        this.permissionsId = permissionsId
        return this
    }

    OkapiUser withToken(String token) {
        this.token = token
        return this
    }

    OkapiUser withBarcode(String barcode) {
        this.barcode = barcode
        return this
    }

    OkapiUser withGroup(String group) {
        this.group = group
        return this
    }

    void addPermission(String permission) {
        this.permissions.add(permission)
    }

    void addPermissions(List<String> permissionsList) {
        this.permissions.addAll(permissionsList)
        this.permissions = this.permissions.unique()
    }

    boolean removePermission(String permission) {
        return this.permissions.remove(permission)
    }

    String getPasswordPlainText() {
        return password.getPlainText()
    }

    void generateUserUuid() {
        this.uuid = UUID.randomUUID().toString()
    }

    void checkUuid() {
        if (!this.uuid) {
            throw new IllegalStateException("UUID is not set for the user")
        }
    }

    void checkPermissionsId() {
        if (!this.permissionsId) {
            throw new IllegalStateException("Permissions ID is not set for the user")
        }
    }
}
