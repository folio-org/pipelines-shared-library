package org.folio.utilities.model

import org.folio.rest.model.OkapiTenant

class Project implements Serializable {
  String hash
  String uiBundleTag
  String clusterName
  String projectName
  String action
  Boolean enableModules
  Map domains
  List installJson
  Map installMap
  String configType
  Map modulesConfig
  Boolean restoreFromBackup
  String backupType
  String backupName
  String backupEngineVersion
  String backupMasterUsername
  String backupFilesPath
  OkapiTenant tenant

  static constraints = {
    backupType(inList: ['rds', 'postgresql'])
    action(inList: ['apply', 'destroy', 'nothing'])
  }
}
