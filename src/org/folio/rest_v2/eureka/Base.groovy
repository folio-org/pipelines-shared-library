package org.folio.rest_v2.eureka

import org.folio.utilities.Logger
import org.folio.utilities.RestClient
import org.folio.utilities.Tools

class Base {
  protected def context
  protected Logger logger
  protected Tools tools
  protected RestClient restClient
  boolean debug = false

  Base(def context, boolean debug = false){
    this.context = context
    this.logger = new Logger(context, this.getClass().getCanonicalName())
    this.tools = new Tools(context)
    this.restClient = new RestClient(context, debug)
    this.debug = debug
  }
}
