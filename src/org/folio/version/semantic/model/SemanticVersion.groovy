package org.folio.version.semantic.model

class SemanticVersion implements Serializable {

  String value

  SemanticVersionType type

  Integer major

  Integer minor

  Integer patch

  String timestamp

  String branch

}
