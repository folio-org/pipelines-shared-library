package org.folio.models

/**
 * The TerraformConfig class holds configuration details needed for running Terraform commands.
 * It contains the working directory, workspace, and variables.
 */
class TerraformConfig {

  /**
   * Working directory where Terraform files are stored and commands are run.
   */
  String workDir

  /**
   * The workspace in which Terraform should operate.
   */
  String workspace

  /**
   * A map holding key-value pairs of Terraform variables.
   */
  Map<String, String> vars

  /**
   * Constructor for creating an instance of TerraformConfig class.
   * Initializes workDir and an empty map for vars.
   *
   * @param workDir the working directory for Terraform files.
   */
  TerraformConfig(String workDir) {
    this.workDir = workDir
    this.vars = [:]
  }

  /**
   * Constructor for creating an instance of TerraformConfig class.
   * Initializes workDir and vars.
   *
   * @param workDir the working directory for Terraform files.
   * @param vars the map of Terraform variables.
   */
  TerraformConfig(String workDir, Map<String, String> vars) {
    this.workDir = workDir
    this.vars = vars
  }

  /**
   * Constructor for creating an instance of TerraformConfig class.
   * Initializes workDir, workspace and vars.
   *
   * @param workDir the working directory for Terraform files.
   * @param workspace the workspace in which Terraform should operate.
   * @param vars the map of Terraform variables.
   */
  TerraformConfig(String workDir, String workspace, Map<String, String> vars) {
    this.workDir = workDir
    this.workspace = workspace
    this.vars = vars
  }

  /**
   * Sets the workspace and returns the TerraformConfig object.
   *
   * @param workspace the workspace to set.
   * @return the TerraformConfig object.
   */
  TerraformConfig withWorkspace(String workspace) {
    this.workspace = workspace
    return this
  }

  /**
   * Sets the Terraform variables and returns the TerraformConfig object.
   *
   * @param vars the map of Terraform variables to set.
   * @return the TerraformConfig object.
   */
  TerraformConfig withVars(Map<String, String> vars) {
    this.vars = vars
    return this
  }

  /**
   * Adds a Terraform variable to the TerraformConfig.
   *
   * @param varName the name of the variable.
   * @param varValue the value of the variable.
   */
  void addVar(String varName, Object varValue) {
    this.vars.put(varName, varValue.toString())
  }

  /**
   * Removes a Terraform variable from the TerraformConfig.
   *
   * @param varName the name of the variable to remove.
   * @return true if the variable was removed, false otherwise.
   */
  boolean removeVar(String varName) {
    return this.vars.remove(varName) != null
  }

  /**
   * Gets the Terraform variables as a string in the format "-var key=value".
   *
   * @return the Terraform variables as a string.
   */
  String getVarsAsString() {
    vars.collect { key, value -> "-var '${key}=${value}'" }.join(' ')
  }
}
