package org.folio.rest.model

import org.folio.utilities.Logger
import org.folio.utilities.Tools
import org.folio.utilities.HttpClient

abstract class GeneralParameters implements Serializable {
    public Object steps

    public String okapiUrl

    public Tools tools = new Tools(steps)

    public HttpClient http = new HttpClient(steps)

    public Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    GeneralParameters(Object steps, String okapiUrl) {
        this.steps = steps
        this.okapiUrl = okapiUrl
    }
}
