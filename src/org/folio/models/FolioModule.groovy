package org.folio.models

import java.util.regex.Matcher

class FolioModule {
  private static final String MODULE_NAME_AND_VERSION_PATTERN = /^(.*?)-(\d+\.\d+\.\d+(?:-.+)?|\d+\.\d+\.\d+)$/
  private static final String SNAPSHOT_VERSION_CORE_PATTERN = /\d+\.\d+\.(\d+-SNAPSHOT\.|\d+00000)/

  String id

  String name

  String version

  ModuleType type

  String buildId

  List descriptor

  VersionType versionType

  enum VersionType {
    RELEASE, SNAPSHOT, CUSTOM
  }

  enum ModuleType {
    BACKEND, FRONTEND, EDGE
  }

  FolioModule(String id) {
    this.id = id
  }

  void loadModuleDetails(){
    Matcher matcher = _getMatcher(this.id)

    if (matcher) {
      this.name = matcher.group(1)
      this.version = matcher.group(2)
      matcher.reset()
    } else {
      throw new InputMismatchException("Not able to extract module name. Module id '${this.id}' has wrong format")
    }

    this.type = _setModuleType(this.name)
    this.versionType = _setVersionType(this.version)

    if (this.versionType == VersionType.SNAPSHOT) {
      this.buildId = _getModuleBuildId(this.version)
    }
  }

  private static Matcher _getMatcher(String id) {
    return id =~ MODULE_NAME_AND_VERSION_PATTERN
  }

  private static ModuleType _setModuleType(String name) {
    switch (name) {
      case ~/^mod-.*$/:
        return ModuleType.BACKEND
        break
      case ~/^edge-.*$/:
        return ModuleType.EDGE
        break
      case ~/^folio_.*$/:
        return ModuleType.FRONTEND
        break
      default:
        throw new Exception("Type of ${name} module is unknown")
    }
  }

  private static VersionType _setVersionType(String version) {
    switch (version) {
      case ~/^\d+\.\d+\.(\d+-SNAPSHOT\.\d+|\d+00000\d+)$/:
        return VersionType.SNAPSHOT
        break
      case ~/^\d+\.\d+\.(\d+-SNAPSHOT\.\w+|\d+00000\w+)$/:
        return VersionType.CUSTOM
        break
      default:
        return VersionType.RELEASE
        break
    }
  }

  private static String _getModuleBuildId(String version) {
    return version.replaceFirst(SNAPSHOT_VERSION_CORE_PATTERN, '')
  }
}


