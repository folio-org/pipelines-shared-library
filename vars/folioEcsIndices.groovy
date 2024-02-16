import org.folio.Constants
import org.folio.utilities.Logger
import org.folio.utilities.RestClient

static void prepareEcsIndices(String username, String password) {

  RestClient client = new RestClient(this)
  Logger logger = new Logger(this, 'common')
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
      client.delete(Constants.FOLIO_OPEN_SEARCH_URL + "/${destination}/?pretty", headers)
      sleep(30000)
    } catch (Exception es) {
      logger.warning("Unable to delete index: ${destination}, error: ${es.getMessage()}")
    }
    finally {
      client.post(Constants.FOLIO_OPEN_SEARCH_URL + "/_reindex?pretty", body, headers)
    }
  }
}
