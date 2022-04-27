package org.folio.rest

import org.folio.http.HttpClient
import org.folio.utilities.Tools

trait PreConfiguration {
    Tools tools = new Tools()
    HttpClient http = new HttpClient()
    LinkedHashMap headers = ['Content-Type': 'application/json']
}
