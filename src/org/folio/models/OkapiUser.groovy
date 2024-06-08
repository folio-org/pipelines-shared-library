package org.folio.models

import hudson.util.Secret

/**
 * Class representing an Okapi User.
 *
 * OkapiUser is a user representation for Okapi system.
 * It includes information like username, password, user's full name, email, UUID, permissions,
 * permissions ID, authentication token, barcode, and user group.
 */
class OkapiUser {

  /**
   * Username of the Okapi user.
   */
  String username

  /**
   * Password of the Okapi user stored securely.
   */
  Secret password

  /**
   * First name of the Okapi user.
   */
  String firstName = ""

  /**
   * Last name of the Okapi user.
   */
  String lastName = ""

  /**
   * Email of the Okapi user.
   */
  String email

  /**
   * UUID of the Okapi user.
   */
  String uuid

  /**
   * List of permissions assigned to the Okapi user.
   */
  List<String> permissions = []

  /**
   * ID of the permissions set assigned to the Okapi user.
   */
  String permissionsId

  /**
   * Authentication token for the Okapi user.
   */
  String token

  /**
   * Barcode associated with the Okapi user.
   */
  String barcode

  /**
   * User group the Okapi user belongs to.
   */
  String group


  /**
   * User type the Okapi user belongs to.
   */
  String type

  /**
   * Authentication cookie for the Okapi user.
   */
  Map cookie

  /**
   * Constructor for creating an instance of OkapiUser class.
   * Initializes username, password, and email.
   *
   * @param username The username of the Okapi user.
   * @param password The password of the Okapi user.
   */
  OkapiUser(String username, Object password) {
    this.username = username
    this.password = password instanceof Secret ? password : Secret.fromString(password)
    this.email = "$username@example.org"
  }

  /**
   * Sets the first name for the Okapi user.
   *
   * @param firstName The first name of the user.
   * @return the OkapiUser instance with the first name set.
   */
  OkapiUser withFirstName(String firstName) {
    this.firstName = firstName
    return this
  }

  /**
   * Sets the last name for the Okapi user.
   *
   * @param lastName The last name of the user.
   * @return the OkapiUser instance with the last name set.
   */
  OkapiUser withLastName(String lastName) {
    this.lastName = lastName
    return this
  }

  /**
   * Sets the email for the Okapi user.
   *
   * @param email The email of the user.
   * @return the OkapiUser instance with the email set.
   */
  OkapiUser withEmail(String email) {
    this.email = email
    return this
  }

  /**
   * Sets the permissions for the Okapi user.
   *
   * @param permissions The permissions of the user.
   * @return the OkapiUser instance with the permissions set.
   */
  OkapiUser withPermissions(List<String> permissions) {
    this.permissions = permissions
    return this
  }

  /**
   * Sets the barcode for the Okapi user.
   *
   * @param barcode The barcode of the user.
   * @return the OkapiUser instance with the barcode set.
   */
  OkapiUser withBarcode(String barcode) {
    this.barcode = barcode
    return this
  }

  /**
   * Sets the group for the Okapi user.
   *
   * @param group The user group of the user.
   * @return the OkapiUser instance with the user group set.
   */
  OkapiUser withGroup(String group) {
    this.group = group
    return this
  }

  /**
   * Sets the type for the Okapi user.
   *
   * @param type The user type of the user.
   * @return the OkapiUser instance with the user type set.
   */
  OkapiUser withType(String type) {
    this.type = type
    return this
  }

  /**
   * Adds a permission to the user's permissions list.
   *
   * @param permission The permission to add.
   */
  void addPermission(String permission) {
    this.permissions.add(permission)
  }

  /**
   * Adds a list of permissions to the user's permissions list.
   *
   * @param permissionsList The list of permissions to add.
   */
  void addPermissions(List<String> permissionsList) {
    this.permissions.addAll(permissionsList)
    this.permissions = this.permissions.unique()
  }

  /**
   * Removes a permission from the user's permissions list.
   *
   * @param permission The permission to remove.
   * @return true if the permission was removed, false otherwise.
   */
  void removePermission(String permission) {
    this.permissions.remove(permission)
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
    return "OkapiUser{username='$username', firstName='$firstName', lastName='$lastName', email='$email', uuid='$uuid', permissions='$permissions', permissionsId='$permissionsId', barcode='$barcode', group='$group',type='$type'}"
  }
}
