package org.folio.models.parameters

import com.cloudbees.groovy.cps.NonCPS

/**
 * Represents parameters for creating a namespace in a Kubernetes cluster with options for configuring
 * various services and features within the FOLIO ecosystem.
 */
class CreateNamespaceParameters implements Cloneable {

  String clusterName

  String namespaceName

  String folioBranch

  String okapiVersion

  String configType

  boolean loadReference

  boolean loadSample

  boolean consortia

  boolean eureka

  boolean linkedData

  boolean splitFiles

  boolean ecsCCL

  boolean rwSplit

  boolean greenmail

  boolean mockServer

  boolean rtr

  boolean uiBuild

  List<String> folioExtensions = []

  String pgType

  String pgVersion

  String kafkaType

  String opensearchType

  String s3Type

  boolean runSanityCheck

  String members

  boolean namespaceOnly = false

  String worker

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

    /**
     * Constructor for Builder, initializes with default parameters.
     */
    Builder() {}

    /**
     * Initializes the builder with an existing set of parameters for cloning or modification.
     * @param existingParameters The existing parameters to initialize the builder with.
     */
    Builder(CreateNamespaceParameters existingParameters) {
      this.parameters = existingParameters.clone()
    }

    /**
     * Sets the cluster name where the namespace will be created.
     * This is a required parameter to identify the target Kubernetes cluster.
     * @param clusterName The name of the Kubernetes cluster.
     * @return Builder instance for method chaining.
     */
    Builder clusterName(String clusterName) {
      parameters.clusterName = clusterName
      return this
    }

    /**
     * Sets the name of the namespace to be created or managed.
     * This name must be unique within the cluster and is used to isolate resources.
     * @param namespaceName The unique name for the namespace.
     * @return Builder instance for method chaining.
     */
    Builder namespaceName(String namespaceName) {
      parameters.namespaceName = namespaceName
      return this
    }

    /**
     * Specifies the FOLIO branch for which the namespace is being configured.
     * This can be used to determine which version of the FOLIO software suite to deploy.
     * @param folioBranch The branch name of the FOLIO repository.
     * @return Builder instance for method chaining.
     */
    Builder folioBranch(String folioBranch) {
      parameters.folioBranch = folioBranch
      return this
    }

    /**
     * Sets the version of Okapi to be deployed within the namespace.
     * Okapi is the gateway API that provides a central point of access for FOLIO services.
     * @param okapiVersion The version number of Okapi.
     * @return Builder instance for method chaining.
     */
    Builder okapiVersion(String okapiVersion) {
      parameters.okapiVersion = okapiVersion
      return this
    }

    /**
     * Defines the configuration type for the namespace, affecting how resources are allocated and managed.
     * @param configType A string indicating the configuration profile to apply.
     * @return Builder instance for method chaining.
     */
    Builder configType(String configType) {
      parameters.configType = configType
      return this
    }

    /**
     * Determines whether reference data should be loaded into the namespace.
     * Useful for initializing a namespace with a set of standard data.
     * @param loadReference `true` to load reference data; `false` otherwise.
     * @return Builder instance for method chaining.
     */
    Builder loadReference(boolean loadReference) {
      parameters.loadReference = loadReference
      return this
    }

    /**
     * Determines whether sample data should be loaded into the namespace.
     * Useful for demos or testing with pre-populated data.
     * @param loadSample `true` to load sample data; `false` otherwise.
     * @return Builder instance for method chaining.
     */
    Builder loadSample(boolean loadSample) {
      parameters.loadSample = loadSample
      return this
    }

    /**
     * Enables or disables consortia features within the namespace.
     * Consortia features allow for the management and collaboration across multiple tenants.
     * @param consortia `true` to enable consortia features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder consortia(boolean consortia) {
      parameters.consortia = consortia
      return this
    }

    /**
     * Enables or disables Eureka IDP within the namespace.
     * Eureka is a successor of Okapi
     * @param eureka `true` to enable Eureka features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    public Builder eureka(boolean eureka) {
      parameters.eureka = eureka
      return this
    }

    /**
     * Enables or disables linked data features within the namespace.
     * Linked data allows for the creation of relationships between disparate data sources,
     * facilitating data sharing and integration across systems.
     *
     * @param linkedData `true` to enable linked data features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder linkedData(boolean linkedData) {
      parameters.linkedData = linkedData
      return this
    }

    /**
     * Enables or disables split-files features within the namespace.
     * @param splitFiles `true` to enable split-files features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder splitFiles(boolean splitFiles) {
      parameters.splitFiles = splitFiles
      return this
    }

    /**
     * Enables or disables ECS_CCL feature within the namespace.
     * @param ecsCCL `true` to enable ECS_CCL features; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder ecsCCL(boolean ecsCCL) {
      parameters.ecsCCL = ecsCCL
      return this
    }

    /**
     * Enables or disables read-write splitting for database access.
     * This can improve performance in read-heavy environments.
     * @param rwSplit `true` to enable read-write splitting; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder rwSplit(boolean rwSplit) {
      parameters.rwSplit = rwSplit
      return this
    }

    /**
     * Enables or disables the use of GreenMail for email testing.
     * GreenMail simulates an email service for development purposes.
     * @param greenmail `true` to enable GreenMail; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder greenmail(boolean greenmail) {
      parameters.greenmail = greenmail
      return this
    }

    /**
     * Enables or disables the mock server for testing without external dependencies.
     * This can be used to simulate third-party services.
     * @param mockServer `true` to enable the mock server; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder mockServer(boolean mockServer) {
      parameters.mockServer = mockServer
      return this
    }

    /**
     * Enables or disables refresh token rotation (RTR)
     * @param rtr `true` to enable RTR; `false` to disable.
     * @return Builder instance for method chaining.
     */
    Builder rtr(boolean rtr) {
      parameters.rtr = rtr
      return this
    }

    /**
     * Specifies the type of PostgreSQL database to be used.
     * @param pgType The type of PostgreSQL deployment, e.g., "built-in" or a specific cloud provider's service.
     * @return Builder instance for method chaining.
     */
    Builder pgType(String pgType) {
      parameters.pgType = pgType
      return this
    }

    /**
     * Sets the version of the PostgreSQL database.
     * @param pgVersion The version number of PostgreSQL to use.
     * @return Builder instance for method chaining.
     */
    Builder pgVersion(String pgVersion) {
      parameters.pgVersion = pgVersion
      return this
    }

    /**
     * Defines the type of Kafka service to be used.
     * @param kafkaType The type of Kafka deployment, e.g., "built-in" or managed service.
     * @return Builder instance for method chaining.
     */
    Builder kafkaType(String kafkaType) {
      parameters.kafkaType = kafkaType
      return this
    }

    /**
     * Sets the type of OpenSearch service to be used.
     * @param opensearchType The deployment option for OpenSearch, such as "built-in" or a cloud service.
     * @return Builder instance for method chaining.
     */
    Builder opensearchType(String opensearchType) {
      parameters.opensearchType = opensearchType
      return this
    }

    /**
     * Determines the type of S3-compatible storage to be used.
     * @param s3Type The storage option for S3, indicating whether it's "built-in" or a specific provider.
     * @return Builder instance for method chaining.
     */
    Builder s3Type(String s3Type) {
      parameters.s3Type = s3Type
      return this
    }

    /**
     * Identify if Cypress sanity check should be run.
     * @param runSanityCheck The option to run or skip cypress sanity check.
     * @return Builder instance for method chaining.
     */
    Builder runSanityCheck(boolean runSanityCheck) {
      parameters.runSanityCheck = runSanityCheck
      return this
    }

    /**
     * Lists members associated with the namespace, typically used for access control or resource sharing.
     * @param members A comma-separated list of members.
     * @return Builder instance for method chaining.
     */
    Builder members(String members) {
      parameters.members = members
      return this
    }

    /**
     * Marks the namespace as being solely for namespace management without deploying the full stack.
     * This is useful for administrative operations.
     * @param namespaceOnly `true` to indicate only namespace creation/deletion; `false` for full deployment.
     * @return Builder instance for method chaining.
     */
    Builder namespaceOnly(boolean namespaceOnly) {
      parameters.namespaceOnly = namespaceOnly
      return this
    }

    /**
     * Specifies the Jenkins worker node that should execute the deployment tasks for this namespace.
     * This allows for specifying targeted resources or environments.
     * @param worker The name of the Jenkins worker node.
     * @return Builder instance for method chaining.
     */
    Builder worker(String worker) {
      parameters.worker = worker
      return this
    }

    /**
     * Specifies whether UI  bundle should be build.
     * This allows to exclude\include UI-bundle
     * @param uiBuild default true.
     * @return decision for UI.
     */
    Builder uiBuild(boolean uiBuild) {
      parameters.uiBuild = uiBuild
      return this
    }

    /**
     * Builds the CreateNamespaceParameters instance.
     * @return A new instance of CreateNamespaceParameters based on the builder settings.
     */
    CreateNamespaceParameters build() {
      return parameters
    }
  }
}
