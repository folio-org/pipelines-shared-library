package org.folio

import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule

class SampleIntegrationTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule()

//  @See https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin
//  @Test
//  void testNexusArtifactsSearch() throws Exception {
//    WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
//    RuleBootstrapper.setup(rule)
//    workflowJob.definition = new CpsFlowDefinition('''
//             code
//    '''.stripIndent(), true)
//
//    QueueTaskFuture<WorkflowRun> workflowLocalLibraryRetrieverun = workflowJob.scheduleBuild2(0)
//
//    WorkflowRun run = rule.assertBuildStatusSuccess(workflowRun)
//  }

}
