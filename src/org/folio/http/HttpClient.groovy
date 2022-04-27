package org.folio.http

class HttpClient implements Serializable {
    @NonCPS
    def request(Map args) {
        HttpURLConnection con
        try {
            URL obj = new URL(args.url + args.uri)
            con = (HttpURLConnection) obj.openConnection()
            con.setRequestMethod(args.method)
            args.headers.each {
                con.setRequestProperty(it.key, it.value)
            }
            if (args.timeout) {
                con.setConnectTimeout(args.timeout)
            }
            if (args.method == 'POST' || args.method == 'PUT') {
                con.setDoOutput(true)
                OutputStream os = con.getOutputStream();
                os.write(args.body.getBytes());
                os.flush();
                os.close();
            }
            Integer responseCode = con.getResponseCode()
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                BufferedReader buf = new BufferedReader(new InputStreamReader(con.getInputStream()))
                String inputLine
                StringBuffer response = new StringBuffer()
                while ((inputLine = buf.readLine()) != null) {
                    response.append(inputLine)
                }
                buf.close()
                return ["status_code": responseCode, "response": response.toString(), "headers": con.getHeaderFields()]
            } else {
                return ["status_code": responseCode]
            }
        } catch (exception) {
            throw new Exception(exception)
        } finally {
            con.disconnect()
        }
    }
}
