package org.folio.models

import java.util.regex.Matcher

class FolioModule {
  static final String MODULE_VERSION_PATTERN = /^(.*?)-(\d+\.\d+\.\d+(?:-.+)?|\d+\.\d+\.\d+)$/

  String id

  String name

  String version

  List descriptor = []

  VersionType versionType

  enum VersionType {
    RELEASE, SNAPSHOT, CUSTOM
  }

  FolioModule(String id) {
    this.id = id
    Matcher matcher = getMatcher(id)
    this.name = matcher[0][1]
    this.version = matcher[0][2]
  }

  private static Matcher getMatcher(String id) {
    Matcher matcher = id =~ MODULE_VERSION_PATTERN
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid format: ${id}")
    }
    return matcher
  }
}


