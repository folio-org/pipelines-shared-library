package org.folio.utilities

class HttpClient {
  private Object steps

  HttpClient(Object steps) {
    this.steps = steps
  }

  void getRequest(String url, ArrayList headers = [], Boolean quiet = true) {
    steps.httpRequest url: url,
      quiet: quiet,
      httpMode: "GET",
      customHeaders: headers,
      validResponseCodes: "100:599"
  }

  void postRequest(String url, String body, ArrayList headers = [], Boolean quiet = true, Integer timeout = 0) {
    steps.httpRequest url: url,
      quiet: quiet,
      httpMode: "POST",
      customHeaders: headers,
      requestBody: body,
      timeout: timeout,
      validResponseCodes: "100:599"
  }

  void putRequest(String url, String body, ArrayList headers = []) {
    steps.httpRequest url: url,
      httpMode: "PUT",
      customHeaders: headers,
      requestBody: body,
      validResponseCodes: "100:599"
  }

  void deleteRequest(String url, String body, ArrayList headers = []) {
    steps.httpRequest url: url,
      httpMode: "DELETE",
      customHeaders: headers,
      requestBody: body,
      validResponseCodes: "100:599"
  }

  void uploadRequest(String url, String filePath, ArrayList headers = []) {
    steps.httpRequest url: url,
      httpMode: "POST",
      contentType: "APPLICATION_FORM_DATA",
      customHeaders: headers,
      wrapAsMultipart: true,
      multipartName: new File(filePath).getName(),
      uploadFile: filePath,
      validResponseCodes: "100:599"
  }

  static String buildHttpErrorMessage(Object response) {
    return "\nStatus code: ${response.status}\nResponse body: ${response.content}"
  }
}
