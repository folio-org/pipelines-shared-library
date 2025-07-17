package org.folio.rest.model

@Deprecated
class DataMigrationTenant {
  String tenantName
  Module moduleInfo
}

@Deprecated
class Module {
  String moduleName
  String moduleVersionDst
  String moduleVersionSrc
  String execTime
}
