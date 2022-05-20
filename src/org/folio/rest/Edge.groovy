package org.folio.rest

import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Edge extends GeneralParameters {

    private Users users = new Users(steps, okapiUrl)

    private Authorization auth = new Authorization(steps, okapiUrl)

    private Permissions permissions = new Permissions(steps, okapiUrl)

    Edge(Object steps, String okapiUrl) {
        super(steps, okapiUrl)
    }

    void createEdgeUsers(OkapiTenant tenant, List modules) {
        OkapiUser defaultEdgeUser = new OkapiUser(
            username: tenant.getId(),
            password: tenant.getId(),
            firstName: "EDGE",
            lastName: "SYSTEM"
        )
        List permissionsList = []
        modules.findAll { it['id'].value ==~ /(?!edge-sip2)edge-.*/ }.each {
            String moduleName = tools.removeLastChar(it['id'] - (it['id'] =~ /\d+\.\d+\.\d+-.*\.\d+|\d+\.\d+.\d+$/).findAll()[0])
            if (OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].defaultUser) {
                permissionsList.addAll(OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].permissions)
            } else if (!OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].defaultUser) {
                OkapiUser edgeUser = new OkapiUser(
                    username: OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].username,
                    password: tenant.getId(),
                    firstName: OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].firstName,
                    lastName: OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].lastName,
                    permissions: OkapiConstants.EDGE_SERVICE_USERS[(moduleName)].permissions
                )
                users.createUser(tenant, edgeUser)
                auth.createUserCredentials(tenant, edgeUser)
                permissions.createUserPermissions(tenant, edgeUser)
            }
        }
        defaultEdgeUser.setPermissions(permissionsList)
        users.createUser(tenant, defaultEdgeUser)
        auth.createUserCredentials(tenant, defaultEdgeUser)
        permissions.createUserPermissions(tenant, defaultEdgeUser)
    }
}
