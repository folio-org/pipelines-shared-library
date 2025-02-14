package org.folio.utilities.model

@Deprecated
/**
 * Remove, once uiBuild.groovy will be fully deprecated
 */
class Module implements Serializable {
  String name
  String version
  String hash
  String tag
  String imageName
  String mvnOptions
  List descriptor
}
