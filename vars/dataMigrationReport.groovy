#!groovy
import groovy.json.*
import groovy.xml.MarkupBuilder
import java.util.concurrent.*
import java.util.Date

def getESLogs(cluster, indexPattern, startDate) {
    def elasticRequestBody = """
    {
        "track_total_hits": false,
        "sort": [
        {
            "@timestamp": {
            "order": "desc",
            "unmapped_type": "boolean"
            }
        }
        ],
        "fields": [
        {
            "field": "log",
            "include_unmapped": "true"
        }
        ],
        "size": 500,
        "version": true,
        "_source": false,
        "query": {
        "bool": {
            "must": [],
            "filter": [
            {
                "bool": {
                "should": [
                    {
                    "bool": {
                        "filter": [
                        {
                            "multi_match": {
                            "type": "phrase",
                            "query": "Activation of module ",
                            "lenient": true
                            }
                        },
                        {
                            "multi_match": {
                            "type": "phrase",
                            "query": "failed",
                            "lenient": true
                            }
                        }
                        ]
                    }
                    },
                    {
                    "bool": {
                        "filter": [
                        {
                            "multi_match": {
                            "type": "phrase",
                            "query": "Activation of module ",
                            "lenient": true
                            }
                        },
                        {
                            "multi_match": {
                            "type": "phrase",
                            "query": "completed successfully",
                            "lenient": true
                            }
                        }
                        ]
                    }
                    }
                ],
                "minimum_should_match": 1
                }
            },
            {
                "range": {
                "@timestamp": {
                    "format": "strict_date_optional_time",
                    "gte": "${startDate}",
                    "lte": "now"
                }
                }
            },
            {
                "match_phrase": {
                "kubernetes.container_name": "okapi"
                }
            }
            ],
            "should": [],
            "must_not": []
        }
        },
        "highlight": {
        "pre_tags": [
            "@kibana-highlighted-field@"
        ],
        "post_tags": [
            "@/kibana-highlighted-field@"
        ],
        "fields": {
            "log": {}
        },
        "fragment_size": 2147483647
        }
    }
    """
    def response = httpRequest httpMode: 'GET', url: "https://${cluster}-elasticsearch.ci.folio.org/${indexPattern}*/_search", validResponseCodes: '100:599', requestBody: elasticRequestBody, contentType: "APPLICATION_JSON"
    def result = new JsonSlurperClassic().parseText(response.content)

    return result
}

@NonCPS
def createHtmlReport(tenantName, tenants) {
    def sortedList = tenants.sort {
        try  {
            it.moduleInfo.execTime.toInteger()
        } catch (NumberFormatException ex) {
            println "Activation of module $it failed"
        }
    }
     
    def groupByTenant = sortedList.reverse().groupBy({
        it.tenantName
    })
    int totalTime = 0
    def writer = new StringWriter()
    def markup = new groovy.xml.MarkupBuilder(writer)
    markup.html {
        markup.table(style: "border-collapse: collapse;") {
            markup.thead(style: "padding: 5px; border: solid 1px #777;") {
                markup.tr {
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #1", "Tenant name")
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #2", "Module name")
                    markup.th(style: "padding: 5px; border: solid 1px #777; background-color: lightblue;", title: "Field #3", "Time(ms)")
                }
            }
            markup.tbody {
                groupByTenant[tenantName].each { tenantInfo -> 
                    totalTime += tenantInfo.moduleInfo.execTime.isNumber() ? tenantInfo.moduleInfo.execTime as Integer: 0
                    markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.tenantName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.moduleInfo.moduleName)
                        markup.td(style: "padding: 5px; border: solid 1px #777;", tenantInfo.moduleInfo.execTime)
                    }
                }
                markup.tr(style: "padding: 5px; border: solid 1px #777;") {
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", "")
                    markup.td(style: "padding: 5px; border: solid 1px #777;", TimeUnit.MILLISECONDS.toMinutes(totalTime.toInteger()) 
                        + ' min')
                }
            }
        }
    }
    return writer.toString()
}
