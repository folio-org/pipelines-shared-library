import org.folio.models.InstallRequestParams
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.rest_v2.Edge
import org.folio.rest_v2.Main

/**
 * Deploys Okapi module.
 * @param namespace The Rancher namespace.
 * @param preStages Optional closure for pre-stages.
 * @param postStages Optional closure for post-stages.
 */
void okapi(RancherNamespace namespace, Closure preStages = { -> }, Closure postStages = { -> }) {
  preStages()

  stage('[Helm] Deploy Okapi') {
    folioHelm.withKubeConfig(namespace.getClusterName()) {
      folioHelm.deployFolioModule(namespace, 'okapi', namespace.getOkapiVersion())
//      folioHelm.checkPodRunning(namespace.getNamespaceName(), 'okapi')
    }
    pauseBetweenStages()
  }

  stage('[Rest] Okapi healthcheck') {
    sleep time: 3, unit: 'MINUTES'
    common.healthCheck("https://${namespace.getDomains()['okapi']}/_/proxy/health")
  }

  postStages()
}

/**
 * Deploys backend modules.
 * @param namespace The Rancher namespace.
 * @param preStages Optional closure for pre-stages.
 * @param postStages Optional closure for post-stages.
 */
void backend(RancherNamespace namespace, Closure preStages = { -> }, Closure postStages = { -> }) {
  preStages()

  stage('[Helm] Deploy backend') {
    folioHelm.withKubeConfig(namespace.getClusterName()) {
      folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
//      folioHelm.checkAllPodsRunning(namespace.getNamespaceName())
    }
    pauseBetweenStages()
  }

  postStages()
}

/**
 * Deploys edge modules.
 * @param namespace The Rancher namespace.
 * @param preStages Optional closure for pre-stages.
 * @param postStages Optional closure for post-stages.
 */
void edge(RancherNamespace namespace, boolean skipEdgeUsersCreation = false, Closure preStages = { -> }, Closure postStages = { -> }) {
  Edge edge = new Edge(this, namespace.getDomains()['okapi'])

  preStages()

  stage('[Rest] Render ephemeral-properties') {
    folioEdge.renderEphemeralProperties(namespace)
    if (!skipEdgeUsersCreation) {
      edge.createEdgeUsers(namespace.getTenants()[namespace.getDefaultTenantId()])
    }
  }

  stage('[Helm] Deploy edge') {
    folioHelm.withKubeConfig(namespace.getClusterName()) {
      Map edgeModules = namespace.getModules().getEdgeModules() // Store result in a variable for efficiency
      edgeModules.each { name, version ->
        kubectl.createConfigMap("${name}-ephemeral-properties", namespace.getNamespaceName(), "./${name}-ephemeral-properties")
      }
      folioHelm.deployFolioModulesParallel(namespace, edgeModules)
    }
    pauseBetweenStages()
  }

  postStages()
}

void updatePreparation(RancherNamespace namespace, InstallRequestParams installRequestParams, List newInstallJson) {
  Main main = new Main(this, namespace.getDomains()['okapi'], namespace.getSuperTenant(), false)

  Map okapiModule = newInstallJson.find { it.id.startsWith('okapi') }

  if (okapiModule) {
    namespace.setOkapiVersion(okapiModule.id - 'okapi-')
    newInstallJson.removeAll { it.id.startsWith('okapi') }
  }

  if (!newInstallJson.isEmpty()) {
    Map fullCompareResultMap = folioVersions.getComparisonResultValues().collectEntries { [(it.name()): []] }

    main.getTenantsList().each { tenantId ->
      List currentInstallJson = main.getInstallJson(tenantId)
      Map compareResultMap = folioVersions.compareInstallJsons(currentInstallJson, newInstallJson)
      compareResultMap.each { key, value ->
        fullCompareResultMap[key] += value
      }

      List tenantInstallJson = compareResultMap.UPGRADE
      if (installRequestParams.getReinstall()) {
        tenantInstallJson += compareResultMap.EQUAL
      }

      if (tenantId == 'supertenant') {
        namespace.getSuperTenant().withInstallJson(tenantInstallJson)
      } else {
        namespace.addTenant(new OkapiTenant(tenantId)
          .withInstallJson(tenantInstallJson)
          .withInstallRequestParams(installRequestParams.clone()))
        //TODO change to retrieve admin user information from secrets
        def folioTenants = folioDefault.tenants()
        if (folioTenants.containsKey(tenantId)) {
          namespace.getTenants()[tenantId].setAdminUser(folioTenants[tenantId].getAdminUser())
        }
      }

      println('=' * 15)
      println("TenantId: ${tenantId}\n" +
        "Upgrade:\n${compareResultMap.UPGRADE*.id.collect { "- $it" }.join('\n')}\n" +
        "Equal:\n${compareResultMap.EQUAL*.id.collect { "- $it" }.join('\n')}\n" +
        "Downgrade:\n${compareResultMap.DOWNGRADE*.id.collect { "- $it" }.join('\n')}\n")
    }

    fullCompareResultMap.each { key, value ->
      fullCompareResultMap[key] = value.unique()
    }

    if (installRequestParams.getReinstall()) {
      namespace.getModules().setInstallJson(fullCompareResultMap.UPGRADE + fullCompareResultMap.EQUAL)
    } else {
      namespace.getModules().setInstallJson(fullCompareResultMap.UPGRADE)
    }

  } else {
    println('Install Json list is empty!')
  }
}

/**
 * Updates modules based on namespace configuration.
 * @param namespace The Rancher namespace.
 */
void update(RancherNamespace namespace, boolean debug = false) {
  Main main = new Main(this, namespace.getDomains()['okapi'], namespace.getSuperTenant(), debug)

  if (namespace.getOkapiVersion()) {
    okapi(namespace)
  } else {
    println('Skipping Okapi deploy stage: No okapi module to update.')
  }

  if (namespace.getModules().getInstallJson()) {
    stage('[Rest] refresh service discovery') {
      main.refreshServicesDiscovery()
    }
    stage('[Rest] Publish descriptors') {
      main.publishDescriptors(namespace.getModules().getInstallJson())
    }
    stage('[Rest] Simulate installation') {
      namespace.getTenants().each { tenantId, tenant ->
        main.simulateInstall(tenant, tenant.getModules().getInstallJson())
      }
    }
    stage('[Rest] Publish discovery') {
      main.publishServiceDiscovery(namespace.getModules().getDiscoveryList())
    }

    //Deploy backend modules
    if (namespace.getModules().getBackendModules()) {
      backend(namespace,
        {
          stage('[Rest] Unlock supertenant') {
            namespace.setSuperTenantLocked(main.isTenantLocked(namespace.getSuperTenant()))
            if (namespace.getSuperTenantLocked()) {
              main.unlockSuperTenant(namespace.getSuperTenant())
            }
          }
        },
        {
          if (namespace.getSuperTenant().getModules().getInstallJson()) {
            stage('[Rest] Update supertenant') {
              main.updateSuperTenant(namespace.getSuperTenant())
            }
            stage('[Rest] Lock supertenant') {
              if (namespace.getSuperTenantLocked()) {
                main.lockSuperTenant(namespace.getSuperTenant())
              }
            }
          }
        }
      )
    } else {
      println('Skipping Backend modules deploy stage: No Backend modules to update.')
    }

    stage('[Rest] Update') {
      main.update(namespace.getTenants())
    }

    //Deploy edge modules
    if (namespace.getModules().getEdgeModules()) {
      edge(namespace)
    } else {
      println('Skipping Edge modules deploy stage: No Edge modules to update.')
    }
  }
}

void restore(RancherNamespace namespace, boolean debug = false) {
  Main main = new Main(this, namespace.getDomains()['okapi'], namespace.getSuperTenant(), debug)

  if (namespace.getOkapiVersion()) {
    okapi(namespace)
  } else {
    error('Okapi version not set!')
  }

  //Deploy backend modules
  if (namespace.getModules().getBackendModules()) {
    backend(namespace,
      {
        stage('[Rest] Preinstall') {
          main.refreshServicesDiscovery()
        }
      },
      {
        //TODO add system-users password reset after https://issues.folio.org/browse/RANCHER-989 implementation
      }
    )
  } else {
    println('Skipping Backend modules deploy stage: No Backend modules to deploy.')
  }

//  stage('[Rest] Update') {
//    main.update(namespace.getTenants())
//  }

  //Deploy edge modules
  if (namespace.getModules().getEdgeModules()) {
    edge(namespace, true)
  } else {
    println('Skipping Edge modules deploy stage: No Edge modules to deploy.')
  }
}

/**
 * Pauses execution for a defined duration between stages.
 */
void pauseBetweenStages(long minutes = 1) {
  sleep time: minutes, unit: 'MINUTES'
}
