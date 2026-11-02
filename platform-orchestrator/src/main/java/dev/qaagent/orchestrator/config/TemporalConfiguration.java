package dev.qaagent.orchestrator.config;

import dev.qaagent.orchestrator.story.StoryActivities;
import dev.qaagent.orchestrator.story.StoryToTestAssetsWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TemporalProperties.class, PlatformProperties.class})
public class TemporalConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(TemporalProperties temporal) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(temporal.target()).build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    @Bean(destroyMethod = "shutdown")
    WorkerFactory workerFactory(WorkflowClient client, StoryActivities activities, TemporalProperties temporal) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(temporal.taskQueue());
        worker.registerWorkflowImplementationTypes(StoryToTestAssetsWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        log.info("Temporal worker listening on queue {} at {}", temporal.taskQueue(), temporal.target());
        return factory;
    }
}
