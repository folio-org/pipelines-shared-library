package org.folio.models

/**
 * This class represents an index in the context of the Folio platform.
 * It provides flexibility by allowing the execution of the index to be run, recreated, or waited for completion based on the provided boolean values.
 */
class Index {
  /** Allowed index types */
  static final List<String> ALLOWED_TYPES = ['instance', 'authority', 'location']

  /** Type of the index */
  String type

  /** Flag to determine if the index should be recreated. */
  boolean recreate

  /** Flag to determine if the operation should wait for the index completion before continuing. */
  boolean waitComplete

  /**
   * Constructor to create a new Index object.
   *
   * @param run A boolean value specifying whether the index should be run.
   * @param recreate A boolean value specifying whether the index should be recreated.
   * @param waitComplete A boolean value specifying whether to wait until the index operation is complete before continuing. Default value is true.
   */
  Index(String type, boolean recreate, boolean waitComplete = false) {
    if (!ALLOWED_TYPES.contains(type)) {
      throw new IllegalArgumentException("Invalid index type: $type. Allowed types are: $ALLOWED_TYPES")
    }
    this.type = type
    this.recreate = recreate
    this.waitComplete = waitComplete
  }
}
