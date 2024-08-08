package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.models.OkapiTenantConsortia
import org.folio.models.OkapiUser

class Main extends Okapi {
  private Users users
  private Permissions permissions
  private Configurations config
  private Consortia consortia
  private static KNOWN_INSTALL_ERRORS = ['Connection refused', 'Bad Request(400) - POST request for mod-serials-management']

  Main(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, superTenant, debug)
    this.users = new Users(context, okapiDomain, debug)
    this.permissions = new Permissions(context, okapiDomain, debug)
    this.config = new Configurations(context, okapiDomain, debug)
    this.consortia = new Consortia(context, okapiDomain, debug)
  }

  void publishDescriptors(List installJson) {
    publishModulesDescriptors(getUnregisteredModuleDescriptors(installJson))
  }

  void simulateInstall(OkapiTenant tenant, Object installJson) {
    if (!tenant.getInstallRequestParams().getSimulate()) {
      logger.warning("Simulation not requested!")
      return
    }
    tenantInstall(tenant, installJson)
    tenant.installRequestParams.simulate = false
  }

  void lockSuperTenant(OkapiTenant superTenant) {
    if (superTenant.tenantId != 'supertenant') {
      logger.error("${superTenant.tenantId} is not a supertenant")
    }
    Map modules = requiredModules()
    superTenant.adminUser.setPermissions(Constants.OKAPI_SUPER_USER_PERMISSIONS)
    tenantInstall(superTenant, superTenant.modules.generateInstallJsonFromIds([modules['mod-users'], modules['mod-permissions'], modules['mod-login']], 'enable'))
    createOkapiUser(superTenant, superTenant.adminUser)
    tenantInstall(superTenant, superTenant.modules.generateInstallJsonFromIds([modules['mod-authtoken']], 'enable'))
    createOkapiUser(superTenant, new OkapiUser('testing_admin', 'admin')
      .withPermissions(Constants.OKAPI_SUPER_USER_PERMISSIONS))
  }

  void unlockSuperTenant(OkapiTenant superTenant) {
    if (superTenant.tenantId != 'supertenant') {
      logger.error("${superTenant.tenantId} is not a supertenant")
    }

    if (!superTenant.getAdminUser()) {
      logger.warning("Admin user not specified for supertenant.")
      return
    }

    List installJson = getInstallJson(superTenant.getTenantId(), 'disable')
    installJson.removeAll { it.id.startsWith('okapi') }

    logger.warning(installJson)
    tenantInstall(superTenant, installJson)
  }

  void createTenantFlow(OkapiTenant tenant) {
    createTenant(tenant)
    tenantInstall(tenant, tenant.modules.generateInstallJsonFromIds(['okapi'], 'enable'))

    tenantInstallRetry(3, 300000, KNOWN_INSTALL_ERRORS) {
      tenantInstall(tenant, tenant.modules.installJson, 900000)
    }

    if (tenant.adminUser) {
      createAdminUser(tenant)
      createAdminUser(tenant, new OkapiUser('service_admin', 'admin'))
      configureTenant(tenant)
    }
  }

  void setUpConsortia(List<OkapiTenantConsortia> consortiaTenants) {
    consortia.setUpConsortia(consortiaTenants)
  }

  void initializeFromScratch(Map<String, OkapiTenant> tenants, boolean enableConsortia) {
    if (superTenant.adminUser) {
      lockSuperTenant(superTenant)
    }
    tenants.each { tenantId, tenant ->
      createTenantFlow(tenant)
    }
    if (enableConsortia) {
      setUpConsortia(tenants.values().findAll { it instanceof OkapiTenantConsortia })
    }
    tenants.each { tenantId, tenant ->
      if (tenant.indexes) {
        tenant.indexes.each { index ->
          runIndex(tenant, index)
        }
      }
    }
  }

  void update(Map<String, OkapiTenant> tenants) {
    tenants.each { tenantId, tenant ->
      tenantInstall(tenant, tenant.modules.generateInstallJsonFromIds(['okapi'], 'enable'))
      tenantInstall(tenant, tenant.modules.installJson, 900000)
      if (tenant.getAdminUser()) {
        loginUser(tenant)
        permissions.purgeDeprecatedPermissions(tenant)
        permissions.refreshAdminPermissions(tenant, tenant.getAdminUser())
//                configureTenant(tenant)
      }
    }

  }

  void updateSuperTenant(OkapiTenant superTenant) {
    tenantInstall(superTenant, superTenant.modules.generateInstallJsonFromIds(['okapi'], 'enable'))
    tenantInstall(superTenant, superTenant.modules.installJson, 900000)
  }

  void createOkapiUser(OkapiTenant tenant, OkapiUser user) {
    users.createUser(tenant, user)
    permissions.createUserPermissions(tenant, user)
    setUserCredentials(tenant, user)
  }

  void createAdminUser(OkapiTenant tenant, OkapiUser adminUser = tenant.adminUser) {
    List<Map> tmpDisableList = tenantInstall(tenant, tenant.modules.generateInstallJsonFromIds([getLatestModuleId('mod-authtoken')], 'disable'))
    adminUser.addPermissions(permissions.getAllPermissions(tenant))
    createOkapiUser(tenant, adminUser)
    tenantInstall(tenant, tmpDisableList.reverse().collect { [id: it.id, action: 'enable'] })
    if (isModuleEnabled(tenant.tenantId, 'mod-inventory-storage')) {
      if (!users.checkUserHasServicePointsRecords(tenant, adminUser)) {
        users.createServicePointsUserRecord(tenant, adminUser, users.getServicePointsIds(tenant))
      }
    } else {
      logger.warning('Module mod-inventory-storage does not enabled')
    }
    users.setPatronGroup(tenant, adminUser)
  }

  void configureTenant(OkapiTenant tenant) {
    config.setSmtp(tenant)
    config.setResetPasswordLink(tenant)
    if (isModuleEnabled(tenant.tenantId, 'mod-copycat')) {
      config.setWorldcat(tenant)
    } else {
      logger.warning("Module (mod-copycat) not enabled for tenant ${tenant.tenantId}")
    }
    if (isModuleEnabled(tenant.tenantId, 'mod-kb-ebsco-java')) {
      config.setRmapiConfig(tenant)
    } else {
      logger.warning("Module (mod-kb-ebsco-java) not enabled for tenant ${tenant.tenantId}")
    }
    /**
     * Moved to initializeFromScratch method
     */
//    if (tenant.indexes) {
//      tenant.indexes.each { index ->
//        runIndex(tenant, index)
//      }
//    }
    if (tenant.okapiConfig.ldpConfig) {
      config.setLdpDbSettings(tenant)
      config.setLdpSqConfig(tenant)
    }
  }

  private Map requiredModules() {
    return ['mod-users'      : getLatestModuleId('mod-users'),
            'mod-permissions': getLatestModuleId('mod-permissions'),
            'mod-login'      : getLatestModuleId('mod-login'),
            'mod-authtoken'  : getLatestModuleId('mod-authtoken')]
  }


  def tenantInstallRetry(int times, int delayMillis, List errorList = [], closure) {
    int attempt = 0
    while (attempt < times) {
      try {
        return closure()
      } catch (Exception e) {
        logger.warning(e.getMessage())
        attempt++
        def errorMessage = e.message ?: ""
        boolean shouldRetry = errorList.any { errorMessage.contains(it) }
        if (attempt >= times || !shouldRetry) {
          throw e
        }
        logger.warning("Attempt ${attempt} failed, retrying in ${delayMillis}ms...")
        sleep(delayMillis)
      }
    }
  }
}
