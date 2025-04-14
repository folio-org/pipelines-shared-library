package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.module.FolioModule
import org.folio.models.module.ModuleType
import org.folio.rest.GitHubUtility

/**
 * OkapiTenant class representing a tenant configuration for Okapi.
 * It provides chainable setter methods following the builder pattern for ease of use.
 */
// TODO: Rename to Tenant
class OkapiTenant extends DTO{
  /** Tenant's identifier. */
  String tenantId

  /** Tenant's name. */
  String tenantName

  /** Description of the tenant. */
  String tenantDescription

  /** Administrator user of the tenant. */
  User adminUser

  /** Modules that are installed for the tenant. */
  FolioInstallJson<FolioModule> modules = new FolioInstallJson(FolioModule.class)

  List<String> enabledExtensions = []

  /** List of index information associated with the tenant. */
  List<Index> indexes = new ArrayList<>()

  /** Parameters for installation requests for the tenant. */
  InstallRequestParams installRequestParams = new InstallRequestParams()

  /** Okapi configuration for the tenant. */
  OkapiConfig okapiConfig = new OkapiConfig()

  /** User Interface (UI) details for the tenant. */
  TenantUi tenantUi

  OkapiTenant(){}

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   */
  OkapiTenant(String tenantId) {
    this.tenantId = tenantId
  }

  /**
   * Chainable setter for tenant's name.
   * This method allows for setting the name of the tenant in a fluent manner.
   *
   * @param tenantName Name of the tenant.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withTenantName(String tenantName) {
    this.tenantName = tenantName
    return this
  }

  /**
   * Chainable setter for tenant's description.
   * This method allows for setting the description of the tenant in a fluent manner.
   *
   * @param tenantDescription Description of the tenant.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withTenantDescription(String tenantDescription) {
    this.tenantDescription = tenantDescription
    return this
  }

  /**
   * Chainable setter for the administrator user.
   * This method allows for specifying the admin user of the tenant in a fluent manner.
   *
   * @param adminUser Administrator user of the tenant.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withAdminUser(User adminUser) {
    this.adminUser = adminUser
    return this
  }

  /**
   * Chainable setter for install JSON.
   * This method sets the installation JSON object while ensuring that specific
   * modules ('mod-consortia' and 'folio_consortia-settings') are removed.
   *
   * @param installJson The install JSON object.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withInstallJson(List<Map<String, String>> installJson) {
//    this.modules.setInstallJsonObject(installJson)
    this.modules.removeModulesByName(['mod-consortia', 'folio_consortia-settings'])
    return this
  }

  /**
   * Method to add an index to the tenant.
   * This method allows for adding an Index object to the tenant's index list in a fluent manner.
   *
   * @param index The Index object to add.
   * @return The OkapiTenant instance for method chaining.
   */
  OkapiTenant withIndex(Index index) {
    this.indexes.add(index)
    return this
  }

  /**
   * Chainable setter for installation request parameters.
   * This method allows for setting the parameters for installation requests in a fluent manner.
   *
   * @param installRequestParams Parameters for installation requests for the tenant.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withInstallRequestParams(InstallRequestParams installRequestParams) {
    this.installRequestParams = installRequestParams
    return this
  }

  /**
   * Chainable setter for Okapi configuration.
   * This method allows for setting the Okapi configuration for the tenant,
   * performing a deep copy of the configuration object.
   *
   * @param okapiConfig The OkapiConfig object.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withConfiguration(OkapiConfig okapiConfig) {
    this.okapiConfig = okapiConfig
    return this
  }

  /**
   * Chainable setter for tenant UI.
   * This method allows for setting the User Interface (UI) details for the tenant.
   * It also associates the tenant ID with the UI details.
   *
   * @param tenantUi User Interface (UI) details for the tenant.
   * @return The OkapiTenant object for method chaining.
   */
  OkapiTenant withTenantUi(TenantUi tenantUi) {
    this.tenantUi = tenantUi
    this.tenantUi.tenantId = this.tenantId
    return this
  }

  OkapiTenant initializeFromRepo(def context, String repo, String branch) {
    List installJson = new GitHubUtility(context).getEnableList(repo, branch)

    installJson.removeAll { module -> module.id == 'okapi' }
    this.withInstallJson(installJson)

    return this
  }

  /**
   * Enables specified Folio extensions for the tenant.
   * This method retrieves the latest version of each specified extension module
   * and adds them to the tenant's installed modules.
   *
   * @param steps The Jenkins script context for accessing pipeline steps.
   * @param extensions List of extension IDs to enable.
   * @param isRelease Indicates whether to fetch the release version of the modules (default is false).
   */
  OkapiTenant enableFolioExtensions(Object steps, List<String> extensions, boolean isRelease = false) {
    extensions.each { extentionId ->
      List modulesList = getExtensionModulesList(steps, extentionId)
      modulesList*.id.each { moduleName ->
        if (!this.modules.installJsonObject.any { module -> (module.name == moduleName) }) {
          String moduleId = new FolioModule().getLatestVersionFromRegistry(steps, moduleName, isRelease)
          this.modules.addModule(moduleId, 'enable')
        }
        FolioModule module = this.modules.getModuleByName(moduleName)
        if (this.tenantUi && module.type == ModuleType.FRONTEND) {
          this.tenantUi.customUiModules.add(module)
        }
      }
    }

    this.enabledExtensions = extensions

    return this
  }

  /**
   * Retrieves a list of module names associated with the specified extension.
   * This method reads the JSON file for the extension and returns the list of modules.
   *
   * @param steps The Jenkins script context for accessing pipeline steps.
   * @param extensionName The name of the extension to retrieve modules for.
   * @return A list of module names associated with the extension.
   */
  @SuppressWarnings('GrMethodMayBeStatic')
  List getExtensionModulesList(Object steps, String extensionName) {
    final String EXTENSIONS_PATH = 'folio-extensions'
    return steps.readJSON(text: steps.libraryResource("${EXTENSIONS_PATH}/${extensionName}.json"))
  }

  @NonCPS
  @Override
  String toString() {
    return """
      "class_name": "OkapiTenant",
      "tenantId": "$tenantId",
      "tenantName": "$tenantName",
      "tenantDescription": "$tenantDescription",
      "modules": $modules,
      "indexes": $indexes
    """
  }
}
