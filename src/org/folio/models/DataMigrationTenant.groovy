package org.folio.models

class DataMigrationTenant {
  String tenantName
  Module moduleInfo
}

class Module {
  String moduleName
  String moduleVersionDst
  String moduleVersionSrc
  String execTime
}
