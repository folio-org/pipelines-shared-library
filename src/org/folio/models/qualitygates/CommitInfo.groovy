package org.folio.models.qualitygates

import org.folio.models.FolioModule

class CommitInfo {
  int buildId
  FolioModule module
  String hash
  String author
  String message

  CommitInfo(FolioModule module) {
    this.module = module
  }
}
