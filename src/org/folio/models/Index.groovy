package org.folio.models

class Index {
    boolean index
    boolean recreate
    boolean waitComplete

    Index() {
        this.index
        this.recreate
        this.waitComplete
    }

    Index(boolean index, boolean recreate, boolean waitComplete = true) {
        this.index = index
        this.recreate = recreate
        this.waitComplete = waitComplete
    }
}
