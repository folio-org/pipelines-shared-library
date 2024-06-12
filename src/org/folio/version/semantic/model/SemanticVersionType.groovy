package org.folio.version.semantic.model

enum SemanticVersionType {

  RELEASE(2, "^\\d+\\.\\d+\\.\\d+\$"),
  SNAPSHOT(1, "^\\d+\\.\\d+\\.\\d+-\\d{14}\$"),
  BRANCH(0, "^\\d+\\.\\d+\\.\\d+-(?!SNAPSHOT).*-\\d{14}\$")

  Integer order

  String pattern

  SemanticVersionType(Integer order, String pattern) {
    this.order = order
    this.pattern = pattern
  }

}
