package org.folio.rest_v2

import org.folio.utilities.Logger
import org.folio.utilities.Tools
import org.folio.utilities.RestClient

/**
 * Common is a base class that provides essential properties and
 * initialization for its subclasses. It maintains common utilities
 * like logging, tooling, and REST clients, which can be used in
 * the subclasses for various operations.
 */
class Common {
    public Object steps
    public String okapiDomain
    public Logger logger
    public Tools tools
    public RestClient restClient

    /**
     * Initializes a new instance of the Common class.
     *
     * @param context The current context.
     * @param okapiDomain The domain for Okapi.
     * @param debug Debug flag indicating whether debugging is enabled.
     */
    Common(Object context, String okapiDomain, boolean debug = false) {
        this.steps = context
        this.okapiDomain = okapiDomain
        this.logger = new Logger(context, this.getClass().getCanonicalName())
        this.tools = new Tools(context)
        this.restClient = new RestClient(context, debug)
    }
}
