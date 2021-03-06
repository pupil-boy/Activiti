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
package org.activiti5.engine.impl.bpmn.parser.handler;

import org.activiti.bpmn.constants.BpmnXMLConstants;
import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.DataAssociation;
import org.activiti.bpmn.model.ImplementationType;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.engine.impl.bpmn.data.AbstractDataAssociation;
import org.activiti.engine.impl.bpmn.data.IOSpecification;
import org.activiti5.engine.impl.bpmn.behavior.WebServiceActivityBehavior;
import org.activiti5.engine.impl.bpmn.parser.BpmnParse;
import org.activiti5.engine.impl.pvm.process.ActivityImpl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joram Barrez
 */
public class ServiceTaskParseHandler extends AbstractExternalInvocationBpmnParseHandler<ServiceTask> {
	
	private static Logger logger = LoggerFactory.getLogger(ServiceTaskParseHandler.class);
  
  public Class< ? extends BaseElement> getHandledType() {
    return ServiceTask.class;
  }
  
  protected void executeParse(BpmnParse bpmnParse, ServiceTask serviceTask) {
    ActivityImpl activity = createActivityOnCurrentScope(bpmnParse, serviceTask, BpmnXMLConstants.ELEMENT_TASK_SERVICE);
    activity.setAsync(serviceTask.isAsynchronous());
    activity.setFailedJobRetryTimeCycleValue(serviceTask.getFailedJobRetryTimeCycleValue());
    activity.setExclusive(!serviceTask.isNotExclusive());

    // Email, Mule and Shell service tasks
    if (StringUtils.isNotEmpty(serviceTask.getType())) {
      createActivityBehaviorForServiceTaskType(activity, bpmnParse, serviceTask);
      // activiti:class
    } else if (ImplementationType.IMPLEMENTATION_TYPE_CLASS.equalsIgnoreCase(serviceTask.getImplementationType())) {
      createClassDelegateServiceTask(activity, bpmnParse, serviceTask);
      // activiti:delegateExpression
    } else if (ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equalsIgnoreCase(serviceTask.getImplementationType())) {
      createServiceTaskDelegateExpressionActivityBehavior(activity, bpmnParse, serviceTask);
      // activiti:expression      
    } else if (ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION.equalsIgnoreCase(serviceTask.getImplementationType())) {
      createServiceTaskExpressionActivityBehavior(activity, bpmnParse, serviceTask);
      // Webservice   
    } else if (ImplementationType.IMPLEMENTATION_TYPE_WEBSERVICE.equalsIgnoreCase(serviceTask.getImplementationType()) && 
            StringUtils.isNotEmpty(serviceTask.getOperationRef())) {
      createWebServiceActivityBehavior(activity, bpmnParse, serviceTask);
    } else {
      createDefaultServiceTaskActivityBehavior(activity, bpmnParse, serviceTask);
    }
  }

  protected void createActivityBehaviorForServiceTaskType(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    if (serviceTask.getType().equalsIgnoreCase("mail")) {
      createMailActivityBehavior(activity, bpmnParse, serviceTask);
    } else if (serviceTask.getType().equalsIgnoreCase("mule")) {
      createMuleActivityBehavior(activity, bpmnParse, serviceTask);
    } else if (serviceTask.getType().equalsIgnoreCase("camel")) {
      createCamelActivityBehavior(activity, bpmnParse, serviceTask);
    } else if (serviceTask.getType().equalsIgnoreCase("shell")) {
      createShellActivityBehavior(activity, bpmnParse, serviceTask);
    } else {
      createActivityBehaviorForCustomServiceTaskType(activity, bpmnParse, serviceTask);
    }
  }

  protected void createMailActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createMailActivityBehavior(serviceTask));
  }

  protected void createMuleActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createMuleActivityBehavior(serviceTask, bpmnParse.getBpmnModel()));
  }

  protected void createCamelActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createCamelActivityBehavior(serviceTask, bpmnParse.getBpmnModel()));
  }

  protected void createShellActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createShellActivityBehavior(serviceTask));
  }
  
  protected void createActivityBehaviorForCustomServiceTaskType(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    logger.warn("Invalid service task type: '" + serviceTask.getType() + "' " + " for service task " + serviceTask.getId());
  }

  protected void createClassDelegateServiceTask(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createClassDelegateServiceTask(serviceTask));
  }

  protected void createServiceTaskDelegateExpressionActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createServiceTaskDelegateExpressionActivityBehavior(serviceTask));
  }

  protected void createServiceTaskExpressionActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    activity.setActivityBehavior(bpmnParse.getActivityBehaviorFactory().createServiceTaskExpressionActivityBehavior(serviceTask));
  }

  protected void createWebServiceActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    if (!bpmnParse.getOperations().containsKey(serviceTask.getOperationRef())) {
      logger.warn(serviceTask.getOperationRef() + " does not exist for service task " + serviceTask.getId());
    } else {

      WebServiceActivityBehavior webServiceActivityBehavior = bpmnParse.getActivityBehaviorFactory().createWebServiceActivityBehavior(serviceTask);
      webServiceActivityBehavior.setOperation(bpmnParse.getOperations().get(serviceTask.getOperationRef()));

      if (serviceTask.getIoSpecification() != null) {
        IOSpecification ioSpecification = createIOSpecification(bpmnParse, serviceTask.getIoSpecification());
        webServiceActivityBehavior.setIoSpecification(ioSpecification);
      }

      for (DataAssociation dataAssociationElement : serviceTask.getDataInputAssociations()) {
        AbstractDataAssociation dataAssociation = createDataInputAssociation(bpmnParse, dataAssociationElement);
        webServiceActivityBehavior.addDataInputAssociation(dataAssociation);
      }

      for (DataAssociation dataAssociationElement : serviceTask.getDataOutputAssociations()) {
        AbstractDataAssociation dataAssociation = createDataOutputAssociation(bpmnParse, dataAssociationElement);
        webServiceActivityBehavior.addDataOutputAssociation(dataAssociation);
      }

      activity.setActivityBehavior(webServiceActivityBehavior);
    }
  }

  protected void createDefaultServiceTaskActivityBehavior(ActivityImpl activity, BpmnParse bpmnParse, ServiceTask serviceTask) {
    logger.warn("One of the attributes 'class', 'delegateExpression', 'type', 'operation', or 'expression' is mandatory on serviceTask " + serviceTask.getId());
  }
}

