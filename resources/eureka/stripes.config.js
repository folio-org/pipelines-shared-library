//const modules = require("./stripes.modules");

module.exports = {
  okapi: {
    // application gateway
    'url': '${kongUrl}',
    'uiUrl': '${tenantUrl}',
    'tenant': '${tenantId}',
    // authentication details: url, secret, clientId
    'authnUrl': '${keycloakUrl}',
    'clientId': '${clientId}'
  },
  config: {
    hasAllPerms: ${hasAllPerms},
    useSecureTokens: true,
    idleSessionWarningSeconds: 60,
    logCategories: 'core,path,action,xhr',
    logPrefix: '--',
    maxUnpagedResourceCount: 2000,
    showPerms: false,
    isSingleTenant: ${isSingleTenant},
    tenantOptions: ${tenantOptions}
  },
  modules: {
    '@folio/acquisition-units': {},
    '@folio/agreements': {},
    '@folio/authorization-roles': {},
    '@folio/authorization-policies': {},
    '@folio/bulk-edit': {},
    '@folio/calendar' : {},
    '@folio/checkin' : {},
    '@folio/checkout' : {},
    '@folio/circulation' : {},
    '@folio/circulation-log' : {},
    '@folio/courses' : {},
    '@folio/dashboard': {},
    '@folio/data-export' : {},
    '@folio/data-import' : {},
    '@folio/developer' : {},
    '@folio/eholdings' : {},
    '@folio/erm-comparisons': {},
    '@folio/erm-usage': {},
    '@folio/export-manager': {},
    '@folio/finance' : {},
    '@folio/gobi-settings': {},
    '@folio/handler-stripes-registry': {},
    '@folio/inventory' : {},
    '@folio/invoice': {},
    '@folio/ldp': {},
    '@folio/licenses': {},
    '@folio/lists': {},
    '@folio/local-kb-admin': {},
    '@folio/marc-authorities': {},
    '@folio/myprofile' : {},
    '@folio/notes' : {},
    '@folio/oai-pmh' : {},
    '@folio/orders': {},
    '@folio/organizations' : {},
    '@folio/plugin-bursar-export': {},
    '@folio/plugin-create-inventory-records' : {},
    '@folio/plugin-eusage-reports': {},
    '@folio/plugin-find-agreement': {},
    '@folio/plugin-find-authority' : {},
    '@folio/plugin-find-contact': {},
    '@folio/plugin-find-erm-usage-data-provider': {},
    '@folio/plugin-find-eresource': {},
    '@folio/plugin-find-fund': {},
    '@folio/plugin-find-import-profile' : {},
    '@folio/plugin-find-instance' : {},
    '@folio/plugin-find-interface' : {},
    '@folio/plugin-find-license': {},
    '@folio/plugin-find-organization': {},
    '@folio/plugin-find-package-title': {},
    '@folio/plugin-find-po-line': {},
    '@folio/plugin-find-user' : {},
    '@folio/plugin-query-builder': {},
    '@folio/quick-marc': {},
    '@folio/receiving' : {},
    '@folio/remote-storage' : {},
    '@folio/requests' : {},
    '@folio/serials-management' : {},
    '@folio/service-interaction': {},
    '@folio/servicepoints' : {},
    '@folio/stripes-acq-components' : {},
    '@folio/stripes-authority-components' : {},
    '@folio/stripes-erm-components' : {},
    '@folio/stripes-marc-components' : {},
    '@folio/tags': {},
    '@folio/tenant-settings' : {},
    '@folio/users' : {}
    , '@folio/plugin-select-application' : {}
    , '@folio/consortia-settings': {}
  },
  branding: {
    logo: {
      src: './tenant-assets/opentown-libraries-logo.png',
      alt: '${tenantId}'
    },
    favicon: {
      src: './tenant-assets/opentown-libraries-favicon.png'
    },
  }
};
