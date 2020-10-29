package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.inject.Named;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;

@Named
public class TriggerDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        CamundaRestExchange camundaRestExchange = new CamundaRestExchange();
        HashMap<String, String> variablesMap = new HashMap<>();
        String sourceComponent = null;
        String targetComponent = null;

        System.out.println(delegateExecution.getVariable("variables"));

        ServiceTask serviceTask = (ServiceTask) delegateExecution.getBpmnModelElementInstance();
        CamundaProperties camProperties = serviceTask.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).singleResult();

        for (CamundaProperty camProperty : camProperties.getCamundaProperties())
        {
            String name = camProperty.getCamundaName();
            String value = camProperty.getCamundaValue();

            if (camProperty.getCamundaName().equals("sourceComponent")) {
                sourceComponent = camProperty.getCamundaValue();
            } else if (camProperty.getCamundaName().equals("targetComponent")) {
                targetComponent = camProperty.getCamundaValue();
            }
        }

        if (delegateExecution.getVariable("variables") != null &&
        !delegateExecution.getVariable("variables").toString().isEmpty()) {
            String variables = delegateExecution.getVariable("variables").toString().replaceAll("[{}\\s]", "");

            List<String> formattedVariables = new ArrayList<>(Arrays.asList(variables.split(",")));

            formattedVariables.forEach(variable -> {
                System.out.println(variable);
                String[] parts = variable.split("=");
                System.out.println("Key " + parts[0]);
                System.out.println("Value " + parts[1]);
                variablesMap.put(parts[0], parts[1]);
            });
        }

        System.out.println(sourceComponent);
        System.out.println(targetComponent);

        camundaRestExchange.triggerTechnology(sourceComponent, targetComponent, variablesMap);
    }
}
