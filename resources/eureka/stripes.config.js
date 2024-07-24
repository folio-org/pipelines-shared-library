const modules = require("./stripes.modules");

module.exports = {
  okapi: {
    // application gateway
    'url': '${kongUrl}',
    'uiUrl': '${tenantUrl}',
    'tenant': '${tenantId}',
    // authentication details: url, secret, clientId
    'authnUrl': '${keycloakUrl}',
    'clientId': '${clientId}',
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
  modules, // Populated by stripes.modules.js
  branding: {
    logo: {
      src: './tenant-assets/opentown-libraries-logo.png',
      alt: 'diku'
    },
    favicon: {
      src: './tenant-assets/opentown-libraries-favicon.png'
    },
  }
};
