package org.folio.models

import hudson.util.Secret

/**
 * Class representing an Okapi User.
 */
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

    OkapiUser withPermissions(List<String> permissions) {
        this.permissions = permissions
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

    /**
     * Returns password in plain text form.
     *
     * @return String Password in plain text.
     */
    String getPasswordPlainText() {
        return password.getPlainText()
    }

    /**
     * Generates a UUID for the user.
     */
    void generateUserUuid() {
        this.uuid = UUID.randomUUID().toString()
    }

    /**
     * Checks if the UUID is set for the user.
     * Throws an exception if it's not set.
     */
    void checkUuid() {
        if (!this.uuid) {
            throw new IllegalStateException("UUID is not set for the user")
        }
    }

    /**
     * Checks if the permissions ID is set for the user.
     * Throws an exception if it's not set.
     */
    void checkPermissionsId() {
        if (!this.permissionsId) {
            throw new IllegalStateException("Permissions ID is not set for the user")
        }
    }

    /**
     * Returns string representation of the object.
     * Password and token are not included for security reasons.
     *
     * @return String String representation of the object.
     */
    @Override
    String toString() {
        return "OkapiUser{username='$username', firstName='$firstName', lastName='$lastName', email='$email', uuid='$uuid', permissions='$permissions', permissionsId='$permissionsId', barcode='$barcode', group='$group'}"
    }
}
