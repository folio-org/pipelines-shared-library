package org.folio.models

class FolioModule {
  String name

  String version

  List descriptor

  VersionType versionType

  enum VersionType {
    RELEASE, SNAPSHOT, CUSTOM
  }
}


