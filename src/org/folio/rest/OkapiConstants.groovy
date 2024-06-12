package org.folio.rest

class OkapiConstants {

  public static final List DESCRIPTORS_REPOSITORIES = ['https://folio-registry.dev.folio.org']

  public static final String RAW_GITHUB_URL = 'https://raw.githubusercontent.com/folio-org'

  public static final String EBSCO_API_URL = "https://api.ebsco.io"

  public static final String EBSCO_CUSTOMER_ID = "apidvcorp"

  public static final ArrayList CONFIGURATIONS = [
    "email_from.json.template",
    "email_password.json.template",
    "email_smtp_host.json.template",
    "email_smtp_port.json.template",
    "email_username.json.template",
    "usersbl_reset.json.template"
  ]

  public static final ArrayList OKAPI_SUPER_USER_PERMISSIONS = [
    "perms.users.assign.immutable",
    "perms.users.assign.mutable",
    "perms.users.assign.okapi",
    "perms.all",
    "okapi.all",
    "okapi.proxy.pull.modules.post",
    "login.all",
    "users.all"
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

  public static final Map EDGE_SERVICE_USERS = [
    "edge-rtac"     : [
      defaultUser: true,
      permissions: ["rtac.all"]
    ],
    "edge-oai-pmh"  : [
      defaultUser: true,
      permissions: ["oai-pmh.all"]
    ],
    "edge-patron"   : [
      defaultUser: true,
      permissions: ["patron.all", "users.collection.get"]
    ],
    "edge-orders"   : [
      defaultUser: true,
      permissions: ["gobi.all", "ebsconet.all"]
    ],
    "edge-ncip"     : [
      defaultUser: true,
      permissions: ["ncip.all"]
    ],
    "edge-dematic"  : [
      defaultUser: false,
      username   : "stagingDirector",
      firstName  : "stagingDirector",
      lastName   : "SYSTEM",
      permissions: ["remote-storage.all"]
    ],
    "edge-caiasoft" : [
      defaultUser: false,
      username   : "caiaSoftClient",
      firstName  : "caiaSoftClient",
      lastName   : "SYSTEM",
      permissions: ["remote-storage.all"]
    ],
    "edge-inn-reach": [
      defaultUser: false,
      username   : "innreachClient",
      firstName  : "innreachClient",
      lastName   : "SYSTEM",
      permissions: ["inn-reach.all"]
    ],
    "edge-connexion": [
      defaultUser: true,
      permissions: ["copycat.all"]
    ]
  ]
}
