# Jenkins Pipelines Shared Library for FOLIO

# Update Jenkins core and plugins
Define 'jenkinsUrl', 'jenkinsUser' and 'jenkinsApiToken' gradle properies.
Api token can be added on <https://{jenkinsUrl}/user/{jenkinsUrl}/configure> url.

Execute 'gradle syncJenkinsConfig' command in project folder.

# Jenkins "retrieveJenkinsPluginData" fail

If automatic download of Jenkins installed plugins failed due to some issues and bugs your can 
open the <https://jenkins-aws.indexdata.com/pluginManager/api/json?depth=2> url, save the content
to "jenkinsResources/plugins.json" file and rerun "gradle syncJenkinsConfig" and at this time 
all Jenkins dependencies will be added to classpath.
