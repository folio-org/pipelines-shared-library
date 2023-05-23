package org.folio.rest.model

class DataMigrationTenant {
    String tenantName
    Module moduleInfo
}

class Module {
    String moduleName
    String moduleNameTo
    String moduleNameFrom
    String execTime
}
