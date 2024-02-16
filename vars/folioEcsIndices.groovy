import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.Logger

String prepareEcsIndices(String username, String password) {

  Map indices = [
    "ecs-snapshot_instance_subject_cs00000int": "folio-testing-ecs-snapshot_instance_subject_cs00000int",
    "ecs-snapshot_instance_cs00000int"        : "folio-testing-ecs-snapshot_instance_cs00000int",
    "ecs-snapshot_contributor_cs00000int"     : "folio-testing-ecs-snapshot_contributor_cs00000int",
    "ecs-snapshot_authority_cs00000int"       : "folio-testing-ecs-snapshot_authority_cs00000int"
  ]

  indices.each { source, destination ->
    new Logger(this, 'folioEcsIndices').info("Source index: ${source} AND Destination index: ${destination}")

    String body = """
      {
        "source": {
        "index": "${source}"
        },
        "dest": {
        "index": "${destination}"
        }
      }
  """

    writeJSON file: "create.json", json: new JsonSlurperClassic().parseText(body)

    try {
      new Logger(this, 'folioEcsIndices').warning("Deleting this index: ${destination}...")
      sh("curl -u \"${username}:${password}\" -X DELETE ${Constants.FOLIO_OPEN_SEARCH_URL}/${destination} > /dev/null 2>&1 &")
      sleep(3)
    } catch (Error es) {
      new Logger(this, 'folioEcsIndices').error("Unable to delete index: ${destination}, error: ${es.getMessage()}")
    } finally {
      new Logger(this, 'folioEcsIndices').info("Working on creation ${destination} index...")
      sh("curl -u \"${username}:${password}\" -X POST ${Constants.FOLIO_OPEN_SEARCH_URL}/_reindex -d @${env.WORKSPACE}/create.json > /dev/null 2>&1 &")
    }
  }
}
