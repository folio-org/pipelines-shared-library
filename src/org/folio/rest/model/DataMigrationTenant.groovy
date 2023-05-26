package org.folio.rest.model
import groovy.transform.Field

class DataMigrationTenant {
    @Field String tenantName
    Module moduleInfo
}

class Module {
    String moduleName
    String moduleVersionDst
    String moduleVersionSrc
    String execTime
}
