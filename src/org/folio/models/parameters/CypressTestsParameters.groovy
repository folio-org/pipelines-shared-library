package org.folio.models.parameters

import org.folio.models.OkapiTenant

/**
 * Represents the parameters required to execute Cypress tests.
 *
 * This class encapsulates the necessary configuration for running Cypress tests,
 * including the CI build ID, test source branch, browser settings, and optional
 * reporting configurations. It also provides methods for managing execution parameters.
 */
class CypressTestsParameters implements Cloneable {
  // Unique identifier for the CI build
  String ciBuildId

  // The source branch from which tests will be executed
  String testsSrcBranch

  // The name of the browser to use for testing, defaults to 'chrome'
  String browserName = 'chrome'

  // Additional execution parameters for Cypress
  String execParameters

  // The URL of the Okapi service
  String okapiUrl

  // The base URL of the tenant being tested
  String tenantUrl

  // The tenant object containing specific tenant details
  OkapiTenant tenant

  // TestRail project ID for reporting, defaults to an empty string
  String testrailProjectID = ''

  // TestRail run ID for reporting, defaults to an empty string
  String testrailRunID = ''

  // Label to identify the worker executing the tests, defaults to 'cypress'
  String workerLabel = 'cypress'

  // Number of workers to use for running tests, defaults to 1
  int numberOfWorkers = 1

  // Timeout duration for tests in minutes, defaults to 480 (8 hours)
  String timeout = '480'

  /**
   * Sets the CI build ID after sanitizing the input.
   *
   * This method removes unwanted characters and replaces spaces with dashes
   * to ensure the CI build ID is properly formatted.
   *
   * @param ciBuildId The CI build ID to set. May contain unwanted characters.
   */
  void setCiBuildId(String ciBuildId) {
    // Sanitize input: remove unwanted characters and replace spaces with dashes
    this.ciBuildId = ciBuildId?.replaceAll(/[^A-Za-z0-9\s.-]/, "")?.replace(' ', '-')
  }

  /**
   * Adds an execution parameter to the existing parameters.
   *
   * If the parameter is non-empty, it appends it to the current execution parameters.
   * If there are no existing parameters, it sets the parameter as the current value.
   *
   * @param parameter The execution parameter to add. Should not be empty.
   */
  void addExecParameter(String parameter) {
    if (parameter?.isEmpty()) {
      return // Do nothing if the parameter is empty
    }
    this.execParameters = this.execParameters ? "${this.execParameters} ${parameter}" : parameter
  }

  /**
   * Creates a clone of the current instance.
   *
   * @return A shallow copy of this instance.
   */
  @Override
  Object clone() {
    return super.clone()
  }
}
