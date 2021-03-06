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
package org.flowable.cmmn.engine.impl.behavior.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.flowable.cmmn.converter.CmmnXmlConstants;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.cmmn.engine.impl.util.EventInstanceCmmnUtil;
import org.flowable.cmmn.model.ExtensionElement;
import org.flowable.cmmn.model.SendEventServiceTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.eventregistry.api.EventRegistry;
import org.flowable.eventregistry.api.runtime.EventPayloadInstance;
import org.flowable.eventregistry.impl.runtime.EventInstanceImpl;
import org.flowable.eventregistry.model.EventModel;

/**
 * @author Joram Barrez
 */
public class SendEventActivityBehavior extends TaskActivityBehavior{

    protected SendEventServiceTask serviceTask;

    public SendEventActivityBehavior(SendEventServiceTask serviceTask) {
        super(serviceTask.isBlocking(), serviceTask.getBlockingExpression());
        this.serviceTask = serviceTask;
    }

    @Override
    public void execute(CommandContext commandContext, PlanItemInstanceEntity planItemInstanceEntity) {

        String eventDefinitionKey = getEventKey();

        EventRegistry eventRegistry = CommandContextUtil.getEventRegistry();
        EventModel eventModel = eventRegistry.getEventModel(eventDefinitionKey);

        if (eventModel == null) {
            throw new FlowableException("No event model found for event key " + eventDefinitionKey);
        }

        EventInstanceImpl eventInstance = new EventInstanceImpl();
        eventInstance.setEventModel(eventModel);

        Collection<EventPayloadInstance> eventPayloadInstances = EventInstanceCmmnUtil
            .createEventPayloadInstances(planItemInstanceEntity, CommandContextUtil.getExpressionManager(commandContext), serviceTask, eventModel);
        eventInstance.setPayloadInstances(eventPayloadInstances);

        // TODO: always async? Send event in post-commit? Triggerable?

        eventRegistry.sendEventOutbound(eventInstance);

        CommandContextUtil.getAgenda(commandContext).planCompletePlanItemInstanceOperation(planItemInstanceEntity);
    }

    protected String getEventKey() {
        List<ExtensionElement> eventTypes = serviceTask.getExtensionElements().getOrDefault(CmmnXmlConstants.ELEMENT_EVENT_TYPE, Collections.emptyList());
        if (eventTypes.isEmpty()) {
            throw new FlowableException("No event key configured for " + serviceTask.getId());
        } else {
            return eventTypes.get(0).getElementText();
        }
    }

}
