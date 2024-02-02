import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger

static void prepareEcsIndices(String username, String password) {
  String body_del = ""
  HttpClient client = new HttpClient(this)
  Logger logger = new Logger(this, 'common')
  Map indices = [
    "ecs-snapshot_instance_subject_cs00000int": "folio-testing-ecs-snapshot_instance_subject_cs00000int",
    "ecs-snapshot_instance_cs00000int"        : "folio-testing-ecs-snapshot_instance_cs00000int",
    "ecs-snapshot_contributor_cs00000int"     : "folio-testing-ecs-snapshot_contributor_cs00000int",
    "ecs-snapshot_authority_cs00000int"       : "folio-testing-ecs-snapshot_authority_cs00000int"
  ]
  ArrayList headers = [
    [name: 'Content-type', value: "application/json"],
    [name: 'Authorization', value: "Basic ${username}:${password}", maskValue: true]
  ]

  indices.each { source, destination ->
    logger.info("Source index: ${source} AND Destination index: ${destination}")

    String body = """
      [{
        "source": {
        "index": "${source}"
        },
        "dest": {
        "index": "${destination}"
        }
      }]
  """
    def res = client.getRequest(Constants.FOLIO_OPEN_SEARCH_URL + "/${destination}/?pretty", headers)
    if (res['body']["$destination"] == "${destination}") {
      try {
        client.deleteRequest(Constants.FOLIO_OPEN_SEARCH_URL + "/${destination}/?pretty", body_del, headers)
        sleep(30000)
      } catch (Exception es) {
        logger.warning("Unable to delete index: ${destination}, error: ${es.getMessage()}")
      }
    } else {
      client.postRequest(Constants.FOLIO_OPEN_SEARCH_URL + "/_reindex?pretty", body, headers)
    }
  }
}
