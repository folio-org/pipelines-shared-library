package org.folio.rest_v2.eureka.kong

import org.folio.models.EurekaNamespace
import org.folio.models.EurekaTenant
import org.folio.models.User

class Edge extends Users {
  Edge(Object context, String kongUrl, String keycloakUrl) {
    super(context, kongUrl, keycloakUrl)
  }

  final public String EUREKA_EDGE_USERS_CONFIG = 'edge/config_eureka.yaml'

  def createEurekaUsers(EurekaNamespace namespace) {

    tools.copyResourceFileToCurrentDirectory(EUREKA_EDGE_USERS_CONFIG)

    def userData = context.readYaml file: './config_eureka.yaml'

    namespace.getTenants().each { tenantId, tenant ->

      Map headers = getTenantHttpHeaders(tenant as EurekaTenant)

      def capabilities = restClient.get(super.generateUrl("/capabilities?limit=5000"), headers).body
      def capabilitiesSets = restClient.get(super.generateUrl("/capability-sets?limit=5000"), headers).body

      List caps = []

      List capSets = []

      userData.each { name, user ->

        user['capabilities'].each { defaultCap ->
          def caps_tmp = capabilities.capabilities.find { it -> it['name'] == defaultCap }?.id
          caps += caps_tmp ? [caps_tmp] : []
        }

        user['capabilitiesSet'].each { defaultCapSet ->
          def capsSets_tmp = capabilitiesSets.capabilitySets.find { it -> it['name'] == defaultCapSet }?.id
          capSets += capsSets_tmp ? [capsSets_tmp] : []
        }
      }

      User edgeUser = new User()
      edgeUser.setUsername('edge_admin')
      edgeUser.setFirstName('edge')
      edgeUser.setLastName('admin')
      edgeUser.setActive(true)
      edgeUser.setType('patron')
      edgeUser.setEmail('edge_admin@ci.folio.org')
      edgeUser.setPreferredContactTypeId('002')

      def response = restClient.post(generateUrl("/users-keycloak/users"), edgeUser.toMap(), headers, [201, 409])

      if (response.responseCode == 409) {
        logger.info("tenantId: ${tenant.tenantId} edge_admin user already exists...")
      } else {
        logger.info("tenantId: ${tenant.tenantId} edge_admin user created...")
      }

      Map userPass = [
        username: 'edge_admin',
        userId  : response['body']['id'],
        password: 'admin'
      ]

      def creds = restClient.post(generateUrl("/authn/credentials"), userPass, headers, [201, 400, 409])
      switch (creds.responseCode) {
        case 201:
          logger.info("tenantId: ${tenant.tenantId} edge_admin user password set...")
          break
        case 400:
          logger.warning("tenantId: ${tenant.tenantId} edge_admin user password set failed...")
          break
        case 409:
          logger.info("tenantId: ${tenant.tenantId} edge_admin user password already set...")
          break
      }

      if (caps) {
        Map userCaps = [
          userId       : response['body']['id'],
          capabilityIds: caps
        ]

        def capsStatus = restClient.post(generateUrl("/users/capabilities"), userCaps, headers, [200, 201, 400, 409])

        switch (capsStatus.responseCode) {
          case 200:
            logger.info("tenantId: ${tenant.tenantId} edge_admin user has assigned capabilities...")
            break
          case 201:
            logger.info("tenantId: ${tenant.tenantId} edge_admin user capabilities assignment created...")
            break
          case 400:
            logger.warning("tenantId: ${tenant.tenantId} edge_admin user has assigned capabilities failed...")
            break
          case 409:
            logger.info("tenantId: ${tenant.tenantId} edge_admin user has assigned capabilities already...")
            break
        }
      }

      if (capSets) {

        Map userCapsSets = [
          userId          : response['body']['id'],
          capabilitySetIds: capSets
        ]

        def capsSetsStatus = restClient.post(generateUrl("/users/capability-sets"), userCapsSets, headers, [200, 201, 400, 409])

        switch (capsSetsStatus.responseCode) {
          case 200:
            logger.info("tenantId: ${tenant.tenantId} edge_admin user has assigned capability sets...")
            break
          case 201:
            logger.info("tenantId: ${tenant.tenantId} edge_admin user assignment created...")
            break
          case 400:
            logger.warning("tenantId: ${tenant.tenantId} edge_admin user has assigned capability sets failed...")
            break
          case 409:
            logger.info("tenantId: ${tenant.tenantId} edge_admin user has assigned capability sets already...")
            break
        }
      }
    }
  }
}
