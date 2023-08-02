package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.folio.rest_v2.Constants
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException


/**
 * Represents a Rancher namespace and its configuration.*/
class RancherNamespace {
    private static final String DEPLOYMENT_CONFIG_BRANCH = "master"

    private static final List DOMAINS_LIST = ['okapi', 'edge']

    String clusterName

    String namespaceName

    String deploymentConfigType

    Map deploymentConfig

    String okapiVersion

    Modules modules = new Modules()

    OkapiTenant superTenant = new OkapiTenant("supertenant")

    String defaultTenantId

    Map<String, OkapiTenant> tenants = [:]

    Map domains = [:]

    boolean enableRwSplit = false

    boolean enableConsortia = false

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

    void setEnableConsortia(boolean enableConsortia) {
        this.modules.addModules([this.modules.getModuleVersion('mod-consortia'),
                                 this.modules.getModuleVersion('folio_consortia-settings')])
        this.enableConsortia = enableConsortia
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
     * Adds a deployment configuration to the RancherNamespace.
     * @throws IllegalArgumentException if the YAML URL is malformed or if there's an error parsing the YAML
     * @throws UncheckedIOException if there's an error reading from the YAML URL
     */
    @NonCPS
    void addDeploymentConfig(String branch = DEPLOYMENT_CONFIG_BRANCH) {
        if (this.deploymentConfigType) {
            String yamlUrl = "https://raw.githubusercontent.com/folio-org/pipelines-shared-library/${branch}/resources/helm/${this.deploymentConfigType}.yaml"
            try {
                String yamlString = new URL(yamlUrl).text
                Yaml yamlParser = new Yaml()
                this.deploymentConfig = yamlParser.load(yamlString)
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid URL: ${e.message}", e)
            } catch (IOException e) {
                throw new UncheckedIOException("Error reading from URL: ${e.message}", e)
            } catch (YAMLException e) {
                throw new IllegalArgumentException("Error parsing YAML: ${e.message}", e)
            }
        }
    }

    /**
     * Updates the configuration for consortia tenants in the RancherNamespace.*/
    private void updateConsortiaTenantsConfig() {
        this.tenants.values().findAll { it instanceof OkapiTenantConsortia }.each { OkapiTenantConsortia tenant ->
            OkapiTenantConsortia centralConsortiaTenant = this.tenants.values()
                .findAll { it instanceof OkapiTenantConsortia }
                .find { OkapiTenantConsortia it -> it.isCentralConsortiaTenant }
            if (!tenant.isCentralConsortiaTenant && centralConsortiaTenant) {
                tenant.okapiConfig.resetPasswordLink = centralConsortiaTenant.okapiConfig.resetPasswordLink
            }
        }
    }
}
