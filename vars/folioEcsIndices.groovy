import org.folio.Constants
import org.folio.rest_v2.Common
import org.folio.utilities.RestClient

static void prepareEcsIndices(String username, String password) {

  RestClient client = new RestClient(this, true, 10800000)
  Common common = new Common(this, "https://fake-url.ecs-snapshot.env")

  Map indices = [
    "ecs-snapshot_instance_subject_cs00000int": "folio-testing-ecs-snapshot_instance_subject_cs00000int",
    "ecs-snapshot_instance_cs00000int"        : "folio-testing-ecs-snapshot_instance_cs00000int",
    "ecs-snapshot_contributor_cs00000int"     : "folio-testing-ecs-snapshot_contributor_cs00000int",
    "ecs-snapshot_authority_cs00000int"       : "folio-testing-ecs-snapshot_authority_cs00000int"
  ]
  Map headers = [
    "Content-type" : "application/json",
    "Authorization": "Basic " + "${username}:${password}".getBytes().encodeBase64()
  ]

  Map headers_del = [
    "Authorization": "Basic " + "${username}:${password}".getBytes().encodeBase64()
  ]

  indices.each { source, destination ->
    logger.info("Source index: ${source} AND Destination index: ${destination}")

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

    try {
      client.delete(Constants.FOLIO_OPEN_SEARCH_URL + "/${destination}", headers_del)
      common.logger.warning("Deleting this index: ${destination}...")
      sleep(30000)
    } catch (Exception es) {
      logger.error("Unable to delete index: ${destination}, error: ${es.getMessage()}")
    }
    finally {
      common.logger.info("Working on creation ${destination} index...")
      client.post(Constants.FOLIO_OPEN_SEARCH_URL + "/_reindex", body, headers)
    }
  }
}
