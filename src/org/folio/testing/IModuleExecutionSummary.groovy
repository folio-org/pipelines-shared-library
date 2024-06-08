package org.folio.testing

interface IModuleExecutionSummary extends IExecutionSummary {
  String getModuleName()
  int getFeaturesPassedCount()
  int getFeaturesFailedCount()
  int getFeaturesSkippedCount()
  int getFeaturesTotalCount()
  int getFeaturesPassRate()
}
