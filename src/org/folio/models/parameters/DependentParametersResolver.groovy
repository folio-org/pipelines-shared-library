package org.folio.models.parameters

import org.folio.Constants
import org.folio.rest_v2.EntitlementApproach
import org.folio.rest_v2.FolioRelease
import org.folio.rest_v2.PlatformType

/**
 * Single home for dependent-parameter derivation: given cluster, namespace, and release,
 * produce a Map of default values for linked parameters. Callers merge this into their
 * own parameter objects. All rules (release-driven, cluster-driven, namespace-driven)
 * live here so they can evolve without touching CreateNamespaceParameters or the UI.
 */
class DependentParametersResolver {

  static Map<String, Object> resolve(String clusterName, String namespaceName, FolioRelease releaseType) {
    Map<String, Object> result = [:]

    FolioRelease effectiveRelease = releaseType ?: FolioRelease.SNAPSHOT

    // Release-driven
    result.entitlementApproach = (effectiveRelease == FolioRelease.SUNFLOWER) ? EntitlementApproach.CREATE : EntitlementApproach.STATE
    result.setBaseUrl = (effectiveRelease != FolioRelease.SUNFLOWER)
    result.configExtensions = (effectiveRelease == FolioRelease.SUNFLOWER) ? ['sunflower'] : []

    // Cluster-driven
    result.platform = resolvePlatform(clusterName)
    result.configType = resolveConfigType(clusterName)
    result.pgType = resolveInfraType(clusterName)
    result.kafkaType = resolveInfraType(clusterName)
    result.s3Type = resolveInfraType(clusterName)

    // Cluster + namespace combined
    result.dataset = resolveDataset(clusterName, namespaceName)
    result.members = resolveMembers(clusterName, namespaceName)

    Map overrides = NAMESPACE_OVERRIDES.get(clusterName)?.get(namespaceName) ?: [:]
    result.putAll(overrides)

    return result
  }

  private static final Map NAMESPACE_OVERRIDES = [
    'folio-etesting': [
      'sprint': [pgType: 'aws', kafkaType: 'aws', s3Type: 'aws', dataset: true]
    ]
  ]

  private static PlatformType resolvePlatform(String clusterName) {
    if (!clusterName) return PlatformType.EUREKA
    Map platformClusters = Constants.AWS_EKS_PLATFORM_CLUSTERS()
    if (platformClusters[PlatformType.EUREKA.name()]?.contains(clusterName)) return PlatformType.EUREKA
    if (platformClusters[PlatformType.OKAPI.name()]?.contains(clusterName)) return PlatformType.OKAPI
    return PlatformType.EUREKA
  }

  private static String resolveConfigType(String clusterName) {
    if (!clusterName) return 'development'
    if (clusterName.contains('testing')) return 'testing'
    if (clusterName.contains('perf')) return 'performance'
    return 'development'
  }

  private static String resolveInfraType(String clusterName) {
    // Performance clusters use AWS-managed services; everything else defaults to built-in.
    if (clusterName == 'folio-perf' || clusterName == 'folio-eperf') return 'aws'
    return 'built-in'
  }

  private static boolean resolveDataset(String clusterName, String namespaceName) {
    if (!clusterName || !namespaceName) return false
    boolean isPerfCluster = (clusterName == 'folio-perf' || clusterName == 'folio-eperf')
    boolean isBugfestNs = namespaceName.toLowerCase().contains('bugfest')
    return isPerfCluster && isBugfestNs
  }

  private static final List<String> SHARED_NAMESPACES = ['sprint', 'snapshot', 'snapshot2']
  private static final List<String> EUREKA_CLUSTERS = ['folio-etesting', 'folio-edev', 'folio-eperf']
  private static final String SHARED_ENV_MEMBERS = 'thunderjet,folijet,spitfire,vega,thor,Eureka,volaris,corsair,Bama,Aggies,Dreamliner,Leipzig,firebird,dojo,erm'

  private static String resolveMembers(String clusterName, String namespaceName) {
    if (!clusterName || !namespaceName) return ''
    if (EUREKA_CLUSTERS.contains(clusterName) && SHARED_NAMESPACES.contains(namespaceName))
      return SHARED_ENV_MEMBERS
    return Constants.ENVS_MEMBERS_LIST.getOrDefault(namespaceName, '') as String
  }
}
