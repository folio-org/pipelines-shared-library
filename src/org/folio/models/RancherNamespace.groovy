package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException

class RancherNamespace {
    private static final String CONFIG_BRANCH = "master"
    private static final String ROOT_DOMAIN = "ci.folio.org"

    String clusterName
    String namespaceName
    String deploymentConfigBranch
    String deploymentConfigType
    Map deploymentConfig
    Modules modules
    OkapiTenant superTenant
    String defaultTenantId
    Map<String, OkapiTenant> tenants
    String terraformWorkspace
    Map<String, String> terraformVars
    boolean enableRwSplit
    boolean enableConsortia

    RancherNamespace(String clusterName, String namespaceName) {
        this.clusterName = clusterName
        this.namespaceName = namespaceName
        this.deploymentConfigBranch = CONFIG_BRANCH
        this.terraformWorkspace = this.clusterName + "-" + this.namespaceName
        this.enableRwSplit = false
        this.enableConsortia = false
        this.deploymentConfig = [:]
        this.tenants = [:]
        this.terraformVars = [:]
        this.superTenant = new OkapiTenant("supertenant")
    }

    RancherNamespace withDeploymentConfigBranch(String deploymentConfigBranch) {
        this.deploymentConfigBranch = deploymentConfigBranch
        return this
    }

    RancherNamespace withDeploymentConfigType(String deploymentConfigType) {
        this.deploymentConfigType = deploymentConfigType
        addDeploymentConfig()
        return this
    }

    RancherNamespace withInstallJson(Object installJson) {
        this.modules = new Modules().withInstallJson(installJson)
        return this
    }

    RancherNamespace withDefaultTenant(String defaultTenant) {
        this.defaultTenantId = defaultTenant
        initSuperTenant()
        return this
    }

    RancherNamespace withEnableRwSplit(boolean enableRwSplit) {
        this.enableRwSplit = enableRwSplit
        return this
    }

    RancherNamespace withEnableConsortia(boolean enableConsortia) {
        this.enableConsortia = enableConsortia
        return this
    }

    void initSuperTenant() {
        this.superTenant = new OkapiTenant("supertenant")
            .withTenantName('Super tenant')
            .withTenantDescription('Okapi built-in super tenant')
            .withAdminUser(new OkapiUser("super_admin", "admin"))
        generateDomains(this.superTenant.tenantId, this.superTenant)
    }

    void addTenant(String tenantId, OkapiTenant tenant) {
        if (tenantId.toLowerCase() == "supertenant" || tenant.tenantId.toLowerCase() == "supertenant") {
            throw new IllegalArgumentException("Cannot add 'supertenant' to tenant map.")
        }

        generateDomains(tenantId, tenant)
        this.tenants[tenantId] = tenant
    }

    boolean removeTenant(String tenantId) {
        return this.tenants.remove(tenantId) != null
    }

    void generateDomains(String tenantId, OkapiTenant tenant) {
        tenant.domains['okapi'] = "${this.clusterName}-${this.namespaceName}-okapi.${ROOT_DOMAIN}"
        tenant.domains['edge'] = "${this.clusterName}-${this.namespaceName}-edge.${ROOT_DOMAIN}"
        tenant.domains['ui'] = "${this.clusterName}-${this.namespaceName}-${tenantId}.${ROOT_DOMAIN}"
    }

    @NonCPS
    void addDeploymentConfig() {
        if (this.deploymentConfigType) {
            String yamlUrl = "https://raw.githubusercontent.com/folio-org/pipelines-shared-library/${this.deploymentConfigBranch}/resources/helm/${this.deploymentConfigType}.yaml"
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

    void addTerraformVar(String varName, Object varValue) {
        this.terraformVars[varName] = varValue.toString()
    }

    boolean removeTerraformVar(String varName) {
        return this.terraformVars.remove(varName) != null
    }
}
