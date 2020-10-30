package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import javax.inject.Named;

import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.plugins.multi.model.message.DeployRequest;
import io.github.edmm.plugins.multi.model.message.DeployResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@Named
public class DeployDelegate implements JavaDelegate {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {

        deploy(delegateExecution);
        System.out.println("Trigger is finished!");
    }

    @SuppressWarnings("checkstyle:EmptyLineSeparator")
    public void deploy(DelegateExecution delegateExecution) {

        System.out.println("1!");

        String modelId = (String) delegateExecution.getVariable("modelId");
        String correlationId = (String) delegateExecution.getVariable("correlationId");
        ComponentProperties[] inputsList = objectMapper.convertValue(
            delegateExecution.getVariable("properties"), ComponentProperties[].class);

        System.out.println("2!");

        DeployRequest deployRequest = new DeployRequest(
            modelId,
            correlationId == null ? null : UUID.fromString(correlationId),
            retrieveDeployComponents(delegateExecution),
            new ArrayList<>(Arrays.asList(inputsList))
        );

        startRESTCall(deployRequest, delegateExecution);

    }

    public void startRESTCall(DeployRequest deployRequest, DelegateExecution delegateExecution) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        String participant = retrieveBPMNProperty("participant", delegateExecution) + "orchestration/deploy";
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<DeployRequest> entity = new HttpEntity<>(deployRequest, headers);

        String result = restTemplate.exchange(participant, HttpMethod.POST, entity, String.class).getBody();

        try {
            delegateExecution.removeVariables();
            DeployResult deployResult = objectMapper.readValue(result, DeployResult.class);
            setCamundaProperties(deployResult, delegateExecution);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void setCamundaProperties(DeployResult deployResult, DelegateExecution delegateExecution) {
        delegateExecution.setVariable("modelId", deployResult.getModelId());
        delegateExecution.setVariable("correlationId", deployResult.getCorrelationId().toString());
        delegateExecution.setVariable("properties", deployResult.getOutput());

        System.out.println(delegateExecution.getVariable("modelId"));
        System.out.println(delegateExecution.getVariable("correlationId"));
        System.out.println(delegateExecution.getVariable("properties"));

    }

    public ArrayList<String> retrieveDeployComponents(DelegateExecution delegateExecution) {
        ArrayList<String> deployComponents = new ArrayList<>();
        ServiceTask serviceTask = (ServiceTask) delegateExecution.getBpmnModelElementInstance();
        CamundaProperties camProperties = serviceTask.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).singleResult();

        for (CamundaProperty camundaProperty : camProperties.getCamundaProperties()) {
            if (camundaProperty.getCamundaName().equals("component")) {
                deployComponents.add(camundaProperty.getCamundaValue());
            }
        }
        return deployComponents;
    }

    public String retrieveBPMNProperty(String property, DelegateExecution delegateExecution) {
        String propertyValue = null;
        ServiceTask serviceTask = (ServiceTask) delegateExecution.getBpmnModelElementInstance();
        CamundaProperties camProperties = serviceTask.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).singleResult();

        for (CamundaProperty camundaProperty : camProperties.getCamundaProperties()) {
            if (camundaProperty.getCamundaName().equals(property)) {
                propertyValue = camundaProperty.getCamundaValue();
            }
        }
        return propertyValue;
    }
}
