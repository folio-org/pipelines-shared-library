package org.folio.rest_v2

enum FolioRelease {

  SUNFLOWER('R1-2025'),
  TRILLIUM('R1-2026'),
  SNAPSHOT(null)

  final String versionPrefix

  FolioRelease(String versionPrefix) {
    this.versionPrefix = versionPrefix
  }

  static FolioRelease fromPlatformVersion(String version) {
    if (!version) return SNAPSHOT
    def matcher = version =~ /^(R\d+-\d{4})/
    if (matcher.find()) {
      String prefix = matcher.group(1)
      return values().find { it.versionPrefix == prefix } ?: SNAPSHOT
    }
    return SNAPSHOT
  }
}
