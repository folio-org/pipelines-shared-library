package org.folio.models

/**
 * This class represents the index for a folio model.
 * It includes details about whether the index should be run, recreated and whether to wait until completion.
 */
class Index {
    boolean run
    boolean recreate
    boolean waitComplete

    /**
     * Creates a new Index.
     *
     * @param run whether the index should be run
     * @param recreate whether the index should be recreated
     * @param waitComplete whether to wait until the index operation is complete before continuing (default: true)
     */
    Index(boolean run, boolean recreate, boolean waitComplete = true) {
        this.run = run
        this.recreate = recreate
        this.waitComplete = waitComplete
    }
}
