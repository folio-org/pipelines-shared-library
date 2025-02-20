package org.folio.rest_v2

class Constants {
  public static final String OKAPI_REGISTRY = "https://folio-registry.dev.folio.org"
  public static final String CI_ROOT_DOMAIN = "ci.folio.org"
  public static final String ECR_FOLIO_REPOSITORY = "732722833398.dkr.ecr.us-west-2.amazonaws.com"

  public static final String KB_API_URL = "https://api.ebsco.io"
  public static final String KB_CUSTOMER_ID = "apidvcorp"

  public static final List OKAPI_SUPER_USER_PERMISSIONS = [
    "perms.users.assign.immutable",
    "perms.users.assign.mutable",
    "perms.users.assign.okapi",
    "perms.all",
    "okapi.all",
    "okapi.proxy.pull.modules.post",
    "login.all",
    "users.all"
  ]

  public static final Map<String, List> CONFIGURATIONS = [
    smtpConfig   : ["email_from.json.template",
                    "email_password.json.template",
                    "email_smtp_host.json.template",
                    "email_smtp_port.json.template",
                    "email_username.json.template"],
    resetPassword: ["usersbl_reset.json.template"]
  ]

  public static final Map WORLDCAT = [
    name                  : "OCLC WorldCat",
    url                   : "zcat.oclc.org/OLUCWorldCat",
    externalIdQueryMap    : "@attr 1=1211 \$identifier",
    internalIdEmbedPath   : "999ff\$i",
    createJobProfileId    : "d0ebb7b0-2f0f-11eb-adc1-0242ac120002",
    updateJobProfileId    : "91f9b8d6-d80e-4727-9783-73fb53e3c786",
    targetOptions         : [
      charset: "utf-8"
    ],
    externalIdentifierType: "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef",
    enabled               : true,
    authentication        : "100473910/PAOLF"
  ]

  public static final List<Map<String, ?>> APPLICATION_FULL = [
    [
      name       : "app-platform-full"
      , branch   : "snapshot"
      , consortia: false
      , core     : true
      , byDefault: true
      , dependsOn: []
    ],
    [
      name       : "app-consortia"
      , branch   : "snapshot"
      , consortia: true
      , core     : false
      , byDefault: true
      , dependsOn: ["app-consortia-manager", "app-platform-full"]
    ],
    [
      name       : "app-consortia-manager"
      , branch   : "master"
      , consortia: true
      , core     : false
      , byDefault: true
      , dependsOn: ["app-consortia", "app-platform-full"]
    ],
    [
      name       : "app-linked-data"
      , branch   : "snapshot"
      , consortia: false
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-full"]
    ]
  ]

  public static final List<Map<String, ?>> APPLICATION_COMPLETE = [
    [
      name: "app-platform-minimal"
      , branch: "master"
      , consortia: false
      , core     : true
      , byDefault: true
      , dependsOn: []
    ],
    [
      name: "app-platform-complete"
      , branch: "snapshot"
      , consortia: false
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-minimal"]
    ],
    [
      name: "app-consortia"
      , branch: "master"
      , consortia: true
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete", "app-consortia-manager"]
    ],
    [
      name: "app-consortia-manager"
      , branch: "master"
      , consortia: true
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete", "app-consortia"]
    ],
    [
      name: "app-linked-data"
      , branch: "master"
      , consortia: false
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ],
    [
      name: "app-dcb"
      , branch: "master"
      , consortia: false
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ],
    [
      name: "app-edge-complete"
      , branch: "snapshot"
      , consortia: false
      , core     : false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ],
    [
      name: "app-reading-room"
      , branch: "master"
      , consortia: false
      , core: false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ],
    [
      name: "app-erm-usage"
      , branch: "master"
      , consortia: false
      , core: false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ],
    [
      name: "app-inn-reach"
      , branch: "master"
      , consortia: false
      , core: false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ],
    [
      name: "app-requests-ecs"
      , branch: "master"
      , consortia: false
      , core: false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete", "app-dcb"]
    ],
    [
      name: "app-requests-mediated"
      , branch: "snapshot"
      , consortia: false
      , core: false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete", "app-requests-mediated-ui"]
    ],
    [
      name: "app-requests-mediated-ui"
      , branch: "master"
      , consortia: false
      , core: false
      , byDefault: true
      , dependsOn: ["app-platform-minimal", "app-platform-complete", "app-requests-mediated"]
    ]
    [
      name: "app-marc-migrations"
      , branch: "master"
      , consortia: false
      , core: false
      , byDefault: false
      , dependsOn: ["app-platform-minimal", "app-platform-complete"]
    ]
  ]

  public static final Map<String, List<Map<String, ?>>> APPLICATION_SETS = [
    'Complete': APPLICATION_COMPLETE
    , 'Full': APPLICATION_FULL
  ]

  static List APPLICATION_SETS_LIST = APPLICATION_SETS*.getKey()

  static final Map APPLICATION_SETS_APPLICATIONS =
    APPLICATION_SETS.collectEntries { appSet, appList ->
      [ appSet, appList.collect { it.name + (it.byDefault ? ":selected" : "") } ]
    }

  static final Map APPLICATION_BRANCH(String set, List appFilter = null) {
    return APPLICATION_SETS[set]
      .findAll{app -> (!appFilter && app.byDefault) || appFilter.contains(app.name)}
      .collectEntries { app -> [ app.name, app.branch ] }
  }
}
