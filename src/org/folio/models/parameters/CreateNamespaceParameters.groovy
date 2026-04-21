package org.folio.models.parameters

import com.cloudbees.groovy.cps.NonCPS
import org.folio.Constants
import org.folio.rest_v2.EntitlementApproach
import org.folio.rest_v2.FolioRelease
import org.folio.rest_v2.PlatformType

/**
 * Represents parameters for creating a namespace in a Kubernetes cluster with options for configuring
 * various services and features within the FOLIO ecosystem.
 */
class CreateNamespaceParameters implements Cloneable {

  FolioRelease releaseType = FolioRelease.SNAPSHOT

  PlatformType platform = PlatformType.OKAPI

  String clusterName

  String namespaceName

  String folioBranch

  String platformBranch

  String okapiVersion

  String configType

  boolean loadReference = true

  boolean loadSample = true

  boolean consortia = true

  boolean consortiaExtra = false

  boolean linkedData

  boolean splitFiles

  boolean ecsCCL

  boolean rwSplit

  boolean greenmail

  boolean mockServer

  boolean rtr

  boolean marcMigrations = false

  boolean hasSecureTenant = true

  String secureTenantId = 'university'

  boolean uiBuild = true

  boolean dataset = false

  boolean scNative = true

  InitializeFromScratchParameters initParams = new InitializeFromScratchParameters()

  String dmSnapshot

  String dbBackupName = ""

  List<String> applications = []

  List<String> folioExtensions = []

  List<String> configExtensions = []

  String pgType = 'built-in'

  String pgVersion = Constants.PGSQL_DEFAULT_VERSION

  String kafkaType = 'built-in'

  String opensearchType = 'aws'

  String s3Type = 'built-in'

  boolean runSanityCheck

  String members = ''

  boolean namespaceOnly = false

  @Deprecated
  String worker

  String keycloakVersion = 'latest'

  String kongVersion = 'latest'

  String type = 'full'

  boolean isConsortiaSingleUi

  private CreateNamespaceParameters() {}

  @NonCPS
  @Override
  CreateNamespaceParameters clone() {
    try {
      CreateNamespaceParameters clone = (CreateNamespaceParameters) super.clone()
      return clone
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Cloning not supported", e)
    }
  }

  Builder toBuilder() {
    return new Builder(this)
  }

  /**
   * Builder class for CreateNamespaceParameters to facilitate the fluent building of parameter objects.
   */
  static class Builder {

    private CreateNamespaceParameters parameters = new CreateNamespaceParameters()
    private Set<String> modifiedFields = [] as Set

    Builder() {}

    Builder(CreateNamespaceParameters existingParameters) {
      this.parameters = existingParameters.clone()
    }

    private Builder setParam(String fieldName, Object value) {
      parameters[fieldName] = value
      modifiedFields << fieldName
      return this
    }

    Builder platform(PlatformType platformType) { return setParam('platform', platformType) }

    /**
     * Sets the cluster name where the namespace will be created.
     * This is a required parameter to identify the target Kubernetes cluster.
     * @param clusterName The name of the Kubernetes cluster.
     * @return Builder instance for method chaining.
     */
    Builder clusterName(String clusterName) { return setParam('clusterName', clusterName) }

    /**
     * Sets the name of the namespace to be created or managed.
     * This name must be unique within the cluster and is used to isolate resources.
     * @param namespaceName The unique name for the namespace.
     * @return Builder instance for method chaining.
     */
    Builder namespaceName(String namespaceName) { return setParam('namespaceName', namespaceName) }

    /**
     * Specifies the FOLIO branch for which the namespace is being configured.
     * This can be used to determine which version of the FOLIO software suite to deploy.
     * @param folioBranch The branch name of the FOLIO repository.
     * @return Builder instance for method chaining.
     */
    Builder folioBranch(String folioBranch) { return setParam('folioBranch', folioBranch) }

    /**
     * Specifies the platform-lsp branch for Eureka platform configuration.
     * This determines the platform descriptor version to use for application and component versions.
     * @param platformBranch The branch name of the platform-lsp repository.
     * @return Builder instance for method chaining.
     */
    Builder platformBranch(String platformBranch) { return setParam('platformBranch', platformBranch) }

    /**
     * Sets the version of Okapi to be deployed within the namespace.
     * Okapi is the gateway API that provides a central point of access for FOLIO services.
     * @param okapiVersion The version number of Okapi.
     * @return Builder instance for method chaining.
     */
    Builder okapiVersion(String okapiVersion) { return setParam('okapiVersion', okapiVersion) }

    /**
     * Defines the configuration type for the namespace, affecting how resources are allocated and managed.
     * @param configType A string indicating the configuration profile to apply.
     * @return Builder instance for method chaining.
     */
    Builder configType(String configType) { return setParam('configType', configType) }

    /**
     * Determines whether reference data should be loaded into the namespace.
     * Useful for initializing a namespace with a set of standard data.
     * @param loadReference `true` to load reference data; `false` otherwise.
     * @return Builder instance for method chaining.
     */
    Builder loadReference(boolean loadReference) { return setParam('loadReference', loadReference) }

    /**
     * Determines whether sample data should be loaded into the namespace.
     * Useful for demos or testing with pre-populated data.
     * @param loadSample `true` to load sample data; `false` otherwise.
     * @return Builder instance for method chaining.
     */
    Builder loadSample(boolean loadSample) { return setParam('loadSample', loadSample) }

    /**
     * Enables or disables consortia features within the namespace.
     * Consortia features allow for the management and collaboration across multiple tenants.
     * @param consortia `true` to enable consortia features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder consortia(boolean consortia) { return setParam('consortia', consortia) }

    /**
     * Enables or disables the second consortium features within the namespace.
     * @param consortiaExtra `true` to enable the second consortium features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder consortiaExtra(boolean consortiaExtra) { return setParam('consortiaExtra', consortiaExtra) }

    /**
     * Enables or disables linked data features within the namespace.
     * Linked data allows for the creation of relationships between disparate data sources,
     * facilitating data sharing and integration across systems.
     *
     * @param linkedData `true` to enable linked data features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder linkedData(boolean linkedData) { return setParam('linkedData', linkedData) }

    /**
     * Enables or disables split-files features within the namespace.
     * @param splitFiles `true` to enable split-files features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder splitFiles(boolean splitFiles) { return setParam('splitFiles', splitFiles) }

    /**
     * Enables or disables ECS_CCL feature within the namespace.
     * @param ecsCCL `true` to enable ECS_CCL features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder ecsCCL(boolean ecsCCL) { return setParam('ecsCCL', ecsCCL) }

    /**
     * Enables or disables read-write splitting for database access.
     * This can improve performance in read-heavy environments.
     * @param rwSplit `true` to enable read-write splitting; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder rwSplit(boolean rwSplit) { return setParam('rwSplit', rwSplit) }

    /**
     * Enables or disables the use of GreenMail for email testing.
     * GreenMail simulates an email service for development purposes.
     * @param greenmail `true` to enable GreenMail; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder greenmail(boolean greenmail) { return setParam('greenmail', greenmail) }

    /**
     * Enables or disables the mock server for testing without external dependencies.
     * This can be used to simulate third-party services.
     * @param mockServer `true` to enable the mock server; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder mockServer(boolean mockServer) { return setParam('mockServer', mockServer) }

    /**
     * Enables or disables refresh token rotation (RTR)
     * @param rtr `true` to enable RTR; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder rtr(boolean rtr) { return setParam('rtr', rtr) }

    /**
     * Do or not marc-migrations
     * @param doMigrations `true` to do marc-migrations; `false` to skip.
     * @return Builder instance for method chaining.
     */
    Builder doMarcMigrations(boolean doMigrations) { return setParam('marcMigrations', doMigrations) }

    /**
     * Activate or not secure tenant
     * @param has `true` to activate security on tenant secureTenantId
     * @return Builder instance for method chaining.
     */
    Builder hasSecureTenant(boolean has) { return setParam('hasSecureTenant', has) }

    /**
     * Defines the id of the tenant to secure
     * @param id The id of the tenant to secure
     * @return Builder instance for method chaining.
     */
    Builder secureTenantId(String id) { return setParam('secureTenantId', id) }

    /**
     * Defines the type of environment to be used.
     * @param dataset `true` to enable BF like dataset; `false` to disable.
     * @return
     */
    Builder dataset(boolean dataset) { return setParam('dataset', dataset) }

    /**
     * Specifies the application names list for Eureka platform.
     * Application versions are resolved from platform-descriptor.json via FAR.
     * @param list The list of application names to deploy.
     * @return Builder instance for method chaining.
     */
    Builder applications(List<String> list) { return setParam('applications', list) }

    /**
     * Specifies the applications map for Eureka platform.
     * Extracts application names from the map keys.
     * @param map The map of application names to branches (branches are ignored, versions come from FAR).
     * @return Builder instance for method chaining.
     * @deprecated Use {@link #applications(List)} instead. Application versions are now resolved from FAR.
     */
    @Deprecated
    Builder applications(Map map) { return setParam('applications', map.keySet().toList()) }

    /**
     * Specifies the type of PostgreSQL database to be used.
     * @param pgType The type of PostgreSQL deployment, e.g., "built-in" or a specific cloud provider's service.
     * @return Builder instance for method chaining.
     */
    Builder pgType(String pgType) { return setParam('pgType', pgType) }

    /**
     * Sets the version of the PostgreSQL database.
     * @param pgVersion The version number of PostgreSQL to use.
     * @return Builder instance for method chaining.
     */
    Builder pgVersion(String pgVersion) { return setParam('pgVersion', pgVersion) }

    /**
     * Defines the type of Kafka service to be used.
     * @param kafkaType The type of Kafka deployment, e.g., "built-in" or managed service.
     * @return Builder instance for method chaining.
     */
    Builder kafkaType(String kafkaType) { return setParam('kafkaType', kafkaType) }

    /**
     * Sets the type of OpenSearch service to be used.
     * @param opensearchType The deployment option for OpenSearch, such as "built-in" or a cloud service.
     * @return Builder instance for method chaining.
     */
    Builder opensearchType(String opensearchType) { return setParam('opensearchType', opensearchType) }

    /**
     * Determines the type of S3-compatible storage to be used.
     * @param s3Type The storage option for S3, indicating whether it's "built-in" or a specific provider.
     * @return Builder instance for method chaining.
    */
    Builder s3Type(String s3Type) { return setParam('s3Type', s3Type) }

    /**
     * Identify if Cypress sanity check should be run.
     * @param runSanityCheck The option to run or skip cypress sanity check.
     * @return Builder instance for method chaining.
     */
    Builder runSanityCheck(boolean runSanityCheck) { return setParam('runSanityCheck', runSanityCheck) }

    /**
     * Lists members associated with the namespace, typically used for access control or resource sharing.
     * @param members A comma-separated list of members.
     * @return Builder instance for method chaining.
     */
    Builder members(String members) { return setParam('members', members) }

    /**
     * Marks the namespace as being solely for namespace management without deploying the full stack.
     * This is useful for administrative operations.
     * @param namespaceOnly `true` to indicate only namespace creation/deletion; `false` for full deployment.
     * @return Builder instance for method chaining.
     */
    Builder namespaceOnly(boolean namespaceOnly) { return setParam('namespaceOnly', namespaceOnly) }

    @Deprecated
    /**
     * Specifies the Jenkins worker node that should execute the deployment tasks for this namespace.
     * This allows for specifying targeted resources or environments.
     * @param worker The name of the Jenkins worker node.
     * @return Builder instance for method chaining.
     */
    Builder worker(String worker) { return setParam('worker', worker) }

    /**
     * Specifies whether UI  bundle should be build.
     * This allows to exclude\include UI-bundle
     * @param uiBuild default true.
     * @return decision for UI.
     */
    Builder uiBuild(boolean uiBuild) { return setParam('uiBuild', uiBuild) }

    Builder kongVersion(String kongVersion) { return setParam('kongVersion', kongVersion) }

    Builder keycloakVersion(String keycloakVersion) { return setParam('keycloakVersion', keycloakVersion) }

    Builder type(String type) { return setParam('type', type) }

    /**
     * Specifies the snapshot of data migration to be used.
     * @param dmSnapshot The snapshot of data migration to use.
     * @return Builder instance for method chaining.
     */
    Builder dmSnapshot(String dmSnapshot) { return setParam('dmSnapshot', dmSnapshot) }

    /**
     * Specifies the name of the database backup to be used.
     * @param name The name of the database backup.
     * @return Builder instance for method chaining.
     */
    Builder dbBackupName(String name) { return setParam('dbBackupName', name) }

    /**
     * Specifies whether the namespace should be created with native support for SC (SideCar).
     * This is typically used for environments that require specific configurations for SideCar features.
     * @param scNative `true` to enable native support for SC; `false` otherwise.
     * @return Builder instance for method chaining.
     */
    Builder scNative(boolean scNative) { return setParam('scNative', scNative) }

    Builder initParams(InitializeFromScratchParameters initParams) { return setParam('initParams', initParams) }

    Builder releaseType(FolioRelease releaseType) { return setParam('releaseType', releaseType) }

    Builder configExtensions(List<String> configExtensions) { return setParam('configExtensions', configExtensions) }

    /**
     * Specifies whether the consortia single UI feature should be enabled.
     * @param isConsortiaSingleUi
     * @return
     */
    Builder isConsortiaSingleUi(boolean isConsortiaSingleUi) { return setParam('isConsortiaSingleUi', isConsortiaSingleUi) }

    CreateNamespaceParameters build(def context = null) {
      Map<String, Object> defaults = DependentParametersResolver.resolve(
        parameters.clusterName, parameters.namespaceName, parameters.platformBranch, context)

      defaults.each { name, value ->
        if (!modifiedFields.contains(name) && value != null && parameters.hasProperty(name)) {
          parameters[name] = value
        }
      }

      if (parameters.initParams.entitlementApproach == null && defaults.entitlementApproach != null)
        parameters.initParams.entitlementApproach = defaults.entitlementApproach as EntitlementApproach

      parameters.folioExtensions = (parameters.folioExtensions + DependentParametersResolver.resolveFolioExtensions(parameters)).unique()

      return parameters
    }
  }
}
