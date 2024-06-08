package org.folio.rest.model

import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

abstract class GeneralParameters implements Serializable {
  public Object steps

  public String okapi_url

  public Tools tools = new Tools(steps)

  public HttpClient http = new HttpClient(steps)

  public Logger logger = new Logger(steps, this.getClass().getCanonicalName())

  GeneralParameters(Object steps, String okapi_url) {
    this.steps = steps
    this.okapi_url = okapi_url
  }
}
