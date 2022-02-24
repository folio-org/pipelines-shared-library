package org.folio.version

def execute(String version, String branch) {
    new ProjectVersion(this, version, branch)
}
