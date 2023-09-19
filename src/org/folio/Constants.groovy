package org.folio

class Constants {
    //AWS
    static String AWS_REGION = 'us-west-2'
    static String AWS_CREDENTIALS_ID = 'stanislav_test'
    static String AWS_S3_SERVICE_ACCOUNT_ID = 'ci-s3-service-account'
    static String AWS_S3_DATA_EXPORT_ID = 'ci-data-export-s3'
    static String AWS_S3_POSTGRES_BACKUPS = 'manage-postgres-db-backups-s3'
    static String AWS_EKS_VPC_NAME = 'folio-rancher-vpc'
    static String AWS_EKS_ADMIN_USERS = 'oleksandrhaimanov,kseniiadubniak,volodymyrkartsev,vasilikapylou,renatsafiulin,tarasspashchenko,stanislav,eldiiarduishenaliev,guramjalaghonia'
    static List AWS_EKS_CLUSTERS = ['folio-testing', 'folio-dev', 'folio-perf', 'folio-tmp']
    static List AWS_EKS_TMP_NAMESPACES = ['test', 'test-1', 'test-2']
    static List AWS_EKS_TESTING_NAMESPACES = ['cypress', 'data-migration', 'karate', 'snapshot', 'sprint']
    static List AWS_EKS_DEV_NAMESPACES = ['bama',
                                          'bienenvolk',
                                          'bulk-edit',
                                          'concorde',
                                          'consortia',
                                          'core-platform',
                                          'data-migration',
                                          'falcon',
                                          'firebird',
                                          'folijet',
                                          'k-int',
                                          'knowledgeware',
                                          'leipzig',
                                          'metadata',
                                          'mjolnir',
                                          'nest-es',
                                          'nla',
                                          'prokopovych',
                                          'reservoir-dogs',
                                          'scout',
                                          'sif',
                                          'siphon',
                                          'spanish',
                                          'spitfire',
                                          'spitfire-2nd',
                                          'spring-force',
                                          'stripes-force',
                                          'tamu',
                                          'task-force',
                                          'task-force-2nd',
                                          'thor',
                                          'thunderjet',
                                          'thunderjet-2nd',
                                          'unam',
                                          'vega',
                                          'volaris',
                                          'volaris-2nd',
                                          'rtr']
    static Map AWS_EKS_NAMESPACE_MAPPING = ['folio-testing': AWS_EKS_TESTING_NAMESPACES,
                                            'folio-dev'    : AWS_EKS_DEV_NAMESPACES,
                                            'folio-perf'   : AWS_EKS_DEV_NAMESPACES,
                                            'folio-tmp'    : AWS_EKS_TMP_NAMESPACES]
    static List AWS_EKS_NAMESPACE_CONFIGS = ['development',
                                             'performance',
                                             'testing']
    //IMPORTANT! Do not change order without need
    static List AWS_INTEGRATED_SERVICE_TYPE = ['built-in', 'aws']

    //Kubecost
    static String KUBECOST_AWS_CREDENTIALS_ID = 'kubecost_athena_aws_user'
    static String KUBECOST_LICENSE_KEY = 'kubecost_license_key'

    //Monitoring/Prometheus
    static String SLACK_WEBHOOK_URL = 'slack_webhook_url'

    //Helm
    static String HELM_MODULES_CONFIG_PATH = 'resources/helm'

    static String FOLIO_ORG = 'folio-org'
    static String FOLIO_GITHUB_URL = 'https://github.com/folio-org'
    static String FOLIO_SSH_GITHUB_URL = 'git@github.com:folio-org'
    static String FOLIO_GITHUB_REPOS_URL = 'https://api.github.com/repos/folio-org'
    static String FOLIO_GITHUB_RAW_URL = 'https://raw.githubusercontent.com/folio-org'
    static String FOLIO_JIRA_URL = 'https://issues.folio.org'
    static String CI_ROOT_DOMAIN = 'ci.folio.org'

    static String GITHUB_CREDENTIALS_ID = '11657186-f4d4-4099-ab72-2a32e023cced'
    static String GITHUB_SSH_CREDENTIALS_ID = 'jenkins-github-sshkey'
    static String PRIVATE_GITHUB_CREDENTIALS_ID = 'id-jenkins-github-personal-token-with-username'
    static String JIRA_CREDENTIALS_ID = 'jenkins-jira'

    //Rancher
    static String RANCHER_TOKEN_ID = 'rancher_token_v2'

    static String EBSCO_KB_CREDENTIALS_ID = 'cypress_api_key_apidvcorp'

    static String PG_ROOT_DEFAULT_PASSWORD = 'postgres_password_123!'
    static String PG_LDP_DEFAULT_PASSWORD = 'diku_ldp9367'
    static String PGADMIN_DEFAULT_PASSWORD = 'SuperSecret'

    //SMTP
    static String EMAIL_SMTP_CREDENTIALS_ID = 'ses-smtp-rancher'
    static String EMAIL_SMTP_SERVER = 'email-smtp.us-west-2.amazonaws.com'
    static String EMAIL_SMTP_PORT = '587'
    static String EMAIL_FROM = 'noreply@ci.folio.org'

    static String NEXUS_PUBLISH_CREDENTIALS_ID = 'jenkins-nexus'
    static String FOLIO_HELM_REPO_NAME = 'folio-helm'
    static String FOLIO_HELM_REPO_URL = 'https://folio-org.github.io/folio-helm'
    static String FOLIO_HELM_HOSTED_REPO_NAME = 'helm-hosted'
    static String FOLIO_HELM_HOSTED_REPO_URL = 'https://repository.folio.org/repository/helm-hosted/'
    static String FOLIO_NPM_REPO_URL = 'https://repository.folio.org/repository/npm-folioci/'

    static String FOLIO_HELM_V2_REPO_URL = 'https://repository.folio.org/repository/folio-helm-v2/'
    static String FOLIO_HELM_V2_REPO_NAME = "folio-helm-v2"

    // Docker
    static String DOCKERHUB_URL = 'https://hub.docker.com/v2'
    static String DOCKER_DEV_REPOSITORY_CREDENTIALS_ID = 'folio-docker-dev'
    static String DOCKER_DEV_REPOSITORY = 'docker.dev.folio.org'
    static String DOCKER_FOLIO_REPOSITORY_CREDENTIALS_ID = 'folio-docker-dev'
    static String DOCKER_FOLIO_REPOSITORY = 'docker-folio.dev.folio.org'
    static String ECR_FOLIO_REPOSITORY = '732722833398.dkr.ecr.us-west-2.amazonaws.com'
    static String ECR_FOLIO_REPOSITORY_CREDENTIALS_ID = 'aws-ecr-rw-credentials'
    static String DOCKER_K8S_CLIENT_IMAGE = 'alpine/k8s:1.22.15'

    //Jenkins
    static String JENKINS_MASTER_NODE = 'master'
    static String JENKINS_JOB_PROJECT = '/Rancher/Project'
    static String JENKINS_JOB_RESTORE_PG_BACKUP = 'Rancher/Create-Restore-PosgreSQL-DB-backup'
    static String JENKINS_JOB_CREATE_TENANT = 'Rancher/Update/create-tenant'
    static String JENKINS_JOB_BACKEND_MODULES_DEPLOY_BRANCH = '/Rancher/Update/backend-modules-deploy-branch'
    static List JENKINS_AGENTS = ['rancher',
                                  'rancher||jenkins-agent-java11',
                                  'jenkins-agent-java11',
                                  'jenkins-agent-java11-test',
                                  'jenkins-agent-java17',
                                  'jenkins-agent-java17-test']

    static String TERRAFORM_DOCKER_CLIENT = 'hashicorp/terraform:1.4'

    //PostgreSQL
    static String PSQL_DUMP_HELM_CHART_NAME = 'psql-dump'
    static String PSQL_DUMP_HELM_INSTALL_CHART_VERSION = '1.0.3'
    static String PSQL_DUMP_BACKUPS_BUCKET_NAME = 'folio-postgresql-backups'

    //Tools
    static String MAVEN_TOOL_NAME = 'maven3-jenkins-slave-all'

    static String OKAPI_SUPERADMIN_CREDENTIALS_ID = 'okapi-superadmin-credentials'

    //Teams
    static Map ENVS_MEMBERS_LIST = ['bama'         : 'Bama',
                                    'concorde'     : 'concorde',
                                    'consortia'    : 'thunderjet',
                                    'core-platform': 'core-platform',
                                    'cypress'      : 'AQA',
                                    'ebsco-core'   : 'ebsco-core',
                                    'falcon'       : 'falcon',
                                    'firebird'     : 'firebird',
                                    'folijet'      : 'folijet',
                                    'folijet-lotus': 'folijet',
                                    'karate'       : '',
                                    'metadata'     : 'spitfire',
                                    'nla'          : 'nla',
                                    'prokopovych'  : 'core-functional',
                                    'scout'        : 'scout',
                                    'spanish'      : 'unam',
                                    'spitfire'     : 'spitfire',
                                    'spitfire-2nd' : 'spitfire',
                                    'nest-es'      : 'spitfire',
                                    'sprint'       : '',
                                    'stripes-force': 'stripes-force',
                                    'tamu'         : '',
                                    'task-force'   : 'ebsco-core',
                                    'task-force-2nd' : 'ebsco-core',
                                    'thor'         : 'thor',
                                    'thunderjet'   : 'thunderjet',
                                    'unam'         : 'unam',
                                    'vega'         : 'vega',
                                    'volaris'      : 'volaris',
                                    'volaris-2nd'  : 'volaris',
                                    'rtr'          : 'volaris']

    //Cypress
    static String CYPRESS_REPOSITORY_URL = "${FOLIO_GITHUB_URL}/stripes-testing.git"
    static String CYPRESS_SSH_REPOSITORY_URL = "${FOLIO_SSH_GITHUB_URL}/stripes-testing.git"
    static String CYPRESS_ALLURE_VERSION = '2.17.2'
    static String CYPRESS_SC_URL = 'https://folio-testing-sc-director.ci.folio.org'
    static String CYPRESS_SC_KEY = 'secretCypressKey'
    static String CYPRESS_PROJECT = 'stripes'
    static String CYPRESS_TESTRAIL_HOST = 'https://foliotest.testrail.io'

    //RDS
    static String BUGFEST_SNAPSHOT_DBNAME = 'folio'

    static Map JMX_METRICS_AVAILABLE = ['mod-bulk-operations': '2.0.0-SNAPSHOT.71']

    // List of modules that support R/W split
    static List READ_WRITE_MODULES = ['mod-audit',
                                      'mod-authtoken',
                                      'mod-circulation-storage',
                                      'mod-data-import',
                                      'mod-di-converter-storage',
                                      'mod-email',
                                      'mod-erm-usage',
                                      'mod-erm-usage-harvester',
                                      'mod-event-config',
                                      'mod-feesfines',
                                      'mod-invoice-storage',
                                      'mod-kb-ebsco-java',
                                      'mod-notify',
                                      'mod-oai-pmh',
                                      'mod-organization-storage',
                                      'mod-orders',
                                      'mod-orders-storage',
                                      'mod-patron-blocks',
                                      'mod-permissions',
                                      'mod-pubsub',
                                      'mod-source-record-manager',
                                      'mod-source-record-storage',
                                      'mod-template-engine',
                                      'mod-users']

    // List of module for CONSORTIUM_ENABLED=true
    static List CONSORTIUM_ENABLED = ["mod-search"]

    // Data Migration Jira tickets (used for Schema Diff)
    static String DM_ISSUE_SUMMARY_PREFIX = 'Schema Difference found after migration:'
    static String DM_JIRA_ISSUE_PRIORITY = 'P2'
    static String DM_ISSUE_LABEL = 'dataMigrationSchemaDiff'
    static String DM_JIRA_PROJECT = 'FAT'
    static String DM_JIRA_ISSUE_TYPE = 'Bug'
    static String DM_ISSUES_JQL = 'labels = dataMigrationSchemaDiff and status != Closed'

    static String GLOBAL_BUILD_TIMEOUT = '4'
}
