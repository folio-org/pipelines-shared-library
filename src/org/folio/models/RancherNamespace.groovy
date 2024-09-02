package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.folio.rest_v2.Constants
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException


/**
 * Represents a Rancher namespace and its configuration.*/
class RancherNamespace {
  protected static final String DEPLOYMENT_CONFIG_BRANCH = "master"

  protected static final List DOMAINS_LIST = ['okapi', 'edge', 'kong', 'keycloak']

  protected static final String GITHUB_SHARED_LIBRARY_RAW = "https://raw.githubusercontent.com/folio-org/pipelines-shared-library"

  String clusterName

  String namespaceName

  String deploymentConfigType

  Map deploymentConfig

  String okapiVersion

  Modules modules = new Modules()

  OkapiTenant superTenant = new OkapiTenant("supertenant")

  boolean superTenantLocked

  String defaultTenantId

  Map<String, OkapiTenant> tenants = [:]

  Map domains = [:]

  boolean enableRwSplit = false

  public boolean enableConsortia = false

  boolean enableSplitFiles = false

  boolean enableRtr = false

  RancherNamespace(String clusterName, String namespaceName) {
    this.clusterName = clusterName
    this.namespaceName = namespaceName

    DOMAINS_LIST.each {
      this.domains.put(it, "${this.clusterName}-${this.namespaceName}-${it}.${Constants.CI_ROOT_DOMAIN}")
    }
  }

  RancherNamespace withDeploymentConfigType(String deploymentConfigType) {
    this.deploymentConfigType = deploymentConfigType
    return this
  }

  RancherNamespace withOkapiVersion(String okapiVersion) {
    this.okapiVersion = okapiVersion
    return this
  }

  RancherNamespace withDefaultTenant(String defaultTenantId) {
    this.defaultTenantId = defaultTenantId
    return this
  }

  RancherNamespace withSuperTenantAdminUser(String username = 'super_admin', Object password = 'admin') {
    this.superTenant.setAdminUser(new OkapiUser(username, password))
    return this
  }

  void setEnableConsortia(boolean enableConsortia, boolean releaseVersion = false) {
    this.modules.addModules([this.modules.getModuleVersion('mod-consortia', releaseVersion),
                             this.modules.getModuleVersion('folio_consortia-settings', releaseVersion)])
    this.enableConsortia = enableConsortia
  }

  boolean getEnableConsortia() {
    return enableConsortia
  }

  /**
   * Adds a tenant to the RancherNamespace.
   * @param tenant the OkapiTenant to add
   * @throws IllegalArgumentException if the tenant ID is "supertenant"
   */
  void addTenant(OkapiTenant tenant) {
    if (tenant.tenantId.toLowerCase() == "supertenant") {
      throw new IllegalArgumentException("Cannot add 'supertenant' to tenant map. As it is already exists")
    }
    if (tenant.tenantUi) {
      tenant.tenantUi.domain = generateDomain(tenant.tenantId)
      tenant.okapiConfig.resetPasswordLink = "https://" + tenant.tenantUi.domain
    }
    this.tenants.put(tenant.tenantId, tenant)
    updateConsortiaTenantsConfig()
  }

  /**
   * Removes a tenant from the RancherNamespace.
   * @param tenantId the ID of the tenant to remove
   * @return true if the tenant was removed, false otherwise
   */
  boolean removeTenant(String tenantId) {
    return this.tenants.remove(tenantId) != null
  }

  /**
   * Generates a domain name based on the provided prefix.
   * @param prefix the prefix for the domain name
   * @return the generated domain name
   */
  String generateDomain(String prefix) {
    return "${this.clusterName}-${this.namespaceName}-${prefix}.${Constants.CI_ROOT_DOMAIN}"
  }

  /**
   * Adds a domain to the RancherNamespace.
   * @param key the key for the domain
   * @param value the value of the domain
   */
  void addDomain(String key, String value) {
    this.domains.put(key, value)
  }

  /**
   * Updates the configuration for consortia tenants in the RancherNamespace.*/
  protected void updateConsortiaTenantsConfig() {
    OkapiTenantConsortia centralConsortiaTenant = findCentralConsortiaTenant()
    if (centralConsortiaTenant) {
      this.tenants.values().findAll { it instanceof OkapiTenantConsortia && !it.isCentralConsortiaTenant }
        .each { tenant ->
          tenant.okapiConfig.resetPasswordLink = centralConsortiaTenant.okapiConfig.resetPasswordLink
        }
    }
  }

  private OkapiTenantConsortia findCentralConsortiaTenant() {
    this.tenants.values().find { it instanceof OkapiTenantConsortia && it.isCentralConsortiaTenant }
  }

  /**
   * Adds a deployment configuration to the RancherNamespace.
   * @throws IllegalArgumentException if the YAML URL is malformed or if there's an error parsing the YAML
   * @throws UncheckedIOException if there's an error reading from the YAML URL
   */
  void addDeploymentConfig(String branch = DEPLOYMENT_CONFIG_BRANCH) {
    if (this.deploymentConfigType) {
      Map deploymentConfig = fetchYaml("${GITHUB_SHARED_LIBRARY_RAW}/${branch}/resources/helm/${this.deploymentConfigType}.yaml")
      if (this.enableSplitFiles) {
        deploymentConfig = mergeMaps(deploymentConfig, getFeatureConfig('split-files', branch))
      }
      this.deploymentConfig = deploymentConfig
    }
  }

  private Map getFeatureConfig(String feature, String branch = DEPLOYMENT_CONFIG_BRANCH) {
    return fetchYaml("${GITHUB_SHARED_LIBRARY_RAW}/${branch}/resources/helm/features/${feature}.yaml")
  }

  @NonCPS
  protected Map fetchYaml(String yamlUrl) {
    try {
      String yamlString = new URL(yamlUrl).text
      Yaml yamlParser = new Yaml()
      return yamlParser.load(yamlString)
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid URL: ${e.message}", e)
    } catch (IOException e) {
      throw new UncheckedIOException("Error reading from URL: ${e.message}", e)
    } catch (YAMLException e) {
      throw new IllegalArgumentException("Error parsing YAML: ${e.message}", e)
    }
  }

  Map mergeMaps(Map map1, Map map2) {
    map2.each { key, value ->
      if (map1.containsKey(key)) {
        if (map1[key] instanceof Map && value instanceof Map) {
          // Merge maps recursively
          map1[key] = mergeMaps(map1[key] as Map, value as Map)
        } else if (map1[key] instanceof List && value instanceof List) {
          // Merge lists
          List mergedList = new ArrayList(map1[key])
          mergedList.addAll(value)
          map1[key] = mergedList.unique() // Remove duplicates, if required
        } else {
          map1[key] = value
        }
      } else {
        map1[key] = value
      }
    }
    return map1
  }

  String getWorkspaceName() {
    return "${this.clusterName}-${this.namespaceName}"
  }
}
