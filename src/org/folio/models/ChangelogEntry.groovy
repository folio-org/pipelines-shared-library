package org.folio.models

import org.folio.models.module.FolioModule

class ChangelogEntry {
  FolioModule module
  String sha
  String commitMessage
  String author
  String commitLink
}
