/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.eventregistry.spring.configurator.test;

import java.util.Random;

import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.eventregistry.api.EventDeployment;
import org.flowable.eventregistry.api.EventRegistry;
import org.flowable.eventregistry.api.EventRepositoryService;
import org.flowable.eventregistry.api.InboundEventChannelAdapter;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tijs Rademakers
 */
@ContextConfiguration("classpath:flowable-context.xml")
public class EventWithSpringBeanTest extends SpringEventFlowableTestCase {
    
    protected TestInboundEventChannelAdapter inboundEventChannelAdapter;
    
    @BeforeEach
    protected void setUp() {
        inboundEventChannelAdapter = setupTestChannel();
    }
    
    @AfterEach
    protected void tearDown() {
        EventRegistryEngineConfiguration eventEngineConfiguration = (EventRegistryEngineConfiguration) processEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_EVENT_REGISTRY_CONFIG);
        eventEngineConfiguration.getEventRegistry().removeChannelDefinition("test-channel");
    }
    
    protected TestInboundEventChannelAdapter setupTestChannel() {
        TestInboundEventChannelAdapter inboundEventChannelAdapter = new TestInboundEventChannelAdapter();

        EventRegistryEngineConfiguration eventEngineConfiguration = (EventRegistryEngineConfiguration) processEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_EVENT_REGISTRY_CONFIG);
        
        eventEngineConfiguration.getEventRegistry().newInboundChannelDefinition()
            .key("test-channel")
            .channelAdapter(inboundEventChannelAdapter)
            .jsonDeserializer()
            .detectEventKeyUsingJsonField("type")
            .jsonFieldsMapDirectlyToPayload()
            .register();

        return inboundEventChannelAdapter;
    }

    @Test
    @Deployment(resources = { "org/flowable/eventregistry/spring/configurator/test/taskWithEventProcess.bpmn20.xml",
        "org/flowable/eventregistry/spring/configurator/test/simpleEvent.event" })
    public void testEventOnUserTask() {
        
        EventRegistryEngineConfiguration eventEngineConfiguration = (EventRegistryEngineConfiguration) processEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_EVENT_REGISTRY_CONFIG);
        
        EventDeployment eventDeployment = eventEngineConfiguration.getEventRepositoryService().createDeploymentQuery().singleResult();
        assertNotNull(eventDeployment);
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("taskWithEventProcess");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            EventSubscription eventSubscription = runtimeService.createEventSubscriptionQuery().activityId("eventBoundary").singleResult();
            assertNotNull(eventSubscription);
            assertEquals(eventSubscription.getProcessInstanceId(), processInstance.getId());
            assertEquals("myEvent", eventSubscription.getEventType());

            inboundEventChannelAdapter.triggerTestEvent();
            Task afterTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals("theTaskAfter", afterTask.getTaskDefinitionKey());
            
            eventSubscription = runtimeService.createEventSubscriptionQuery().activityId("eventBoundary").singleResult();
            assertNull(eventSubscription);
            
            taskService.complete(afterTask.getId());
    
            assertProcessEnded(processInstance.getId());
            
        } finally {
            eventEngineConfiguration.getEventRepositoryService().deleteDeployment(eventDeployment.getId());
        }
    }
    
    @Test
    @Deployment(resources = { "org/flowable/eventregistry/spring/configurator/test/taskWithEventProcess.bpmn20.xml"})
    public void testEventOnUserTaskWithoutVariablesSeparateDeployments() {
        
        EventRegistryEngineConfiguration eventEngineConfiguration = (EventRegistryEngineConfiguration) processEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_EVENT_REGISTRY_CONFIG);
        
        EventRepositoryService eventRepositoryService = eventEngineConfiguration.getEventRepositoryService();
        EventDeployment eventDeployment = eventRepositoryService.createDeployment().addClasspathResource("org/flowable/eventregistry/spring/configurator/test/simpleEvent.event").deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("taskWithEventProcess");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            EventSubscription eventSubscription = runtimeService.createEventSubscriptionQuery().activityId("eventBoundary").singleResult();
            assertNotNull(eventSubscription);
            assertEquals(eventSubscription.getProcessInstanceId(), processInstance.getId());
            assertEquals("myEvent", eventSubscription.getEventType());

            inboundEventChannelAdapter.triggerTestEvent();
            Task afterTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals("theTaskAfter", afterTask.getTaskDefinitionKey());
            
            eventSubscription = runtimeService.createEventSubscriptionQuery().activityId("eventBoundary").singleResult();
            assertNull(eventSubscription);
            
            taskService.complete(afterTask.getId());
    
            assertProcessEnded(processInstance.getId());
            
        } finally {
            eventRepositoryService.deleteDeployment(eventDeployment.getId());
        }
    }

    private static class TestInboundEventChannelAdapter implements InboundEventChannelAdapter {

        public String channelKey;
        public EventRegistry eventRegistry;

        @Override
        public void setChannelKey(String channelKey) {
            this.channelKey = channelKey;
        }

        @Override
        public void setEventRegistry(EventRegistry eventRegistry) {
            this.eventRegistry = eventRegistry;
        }

        public void triggerTestEvent() {
            triggerTestEvent(null);
        }

        public void triggerTestEvent(String customerId) {
            triggerTestEvent(customerId, null);
        }

        public void triggerOrderTestEvent(String orderId) {
            triggerTestEvent(null, orderId);
        }

        public void triggerTestEvent(String customerId, String orderId) {
            ObjectMapper objectMapper = new ObjectMapper();

            ObjectNode json = objectMapper.createObjectNode();
            json.put("type", "myEvent");
            if (customerId != null) {
                json.put("customerId", customerId);
            }

            if (orderId != null) {
                json.put("orderId", orderId);
            }
            json.put("payload1", "Hello World");
            json.put("payload2", new Random().nextInt());
            try {
                eventRegistry.eventReceived(channelKey, objectMapper.writeValueAsString(json));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
