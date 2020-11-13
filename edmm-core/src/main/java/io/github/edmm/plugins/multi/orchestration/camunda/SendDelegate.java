package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.inject.Named;

import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.plugins.multi.model.message.CamundaMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Named
public class SendDelegate implements JavaDelegate {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean successfulStatusCode = false;

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {

        delegateExecution.setVariable("modelId", "12345");

        ComponentProperties[] inputsList = objectMapper.convertValue(
            delegateExecution.getVariable("properties"), ComponentProperties[].class);

        HashMap<String, Object> processVariables = new HashMap<>();
        HashMap<String, ArrayList<ComponentProperties>> value = new HashMap<>();

        //prepareInputProperties(inputsList, delegateExecution);

        //value.put("value", new ArrayList<>(Arrays.asList(inputsList)));
        value.put("value", new ArrayList<>(prepareInputProperties(inputsList, delegateExecution)));

        processVariables.put("properties", value);

        CamundaMessage camundaMessage = new CamundaMessage(
            retrieveBPMNProperty("component", delegateExecution),
            delegateExecution.getVariable("modelId").toString(),
            processVariables
        );

        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            String json = ow.writeValueAsString(camundaMessage);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        do {
            System.out.println("START 8000");
            startRESTCall(camundaMessage, delegateExecution);
            Thread.sleep(10000);
        } while (!this.successfulStatusCode);

        System.out.println("END 8000");

    }

    public void startRESTCall(CamundaMessage camundaMessage, DelegateExecution delegateExecution) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        String participant = retrieveBPMNProperty("participant", delegateExecution) + "engine-rest/message";

        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<CamundaMessage> entity = new HttpEntity<>(camundaMessage, headers);

        try {
            int statusCodeValue = restTemplate.exchange(participant, HttpMethod.POST, entity, String.class).getStatusCodeValue();

            if (statusCodeValue == 204) {
                this.successfulStatusCode = true;
            }

        } catch (HttpClientErrorException clientErrorException) {
            System.out.println(clientErrorException.toString());
            System.out.println("ERROR");
        }

    }

    public ArrayList<ComponentProperties> prepareInputProperties(ComponentProperties[] inputsList, DelegateExecution delegateExecution) {

        ArrayList<ComponentProperties> updatedComponentProperties = new ArrayList<>();

        System.out.println("PREPARE INPUT");
        for (ComponentProperties componentProperties : inputsList) {
            if (retrieveBPMNProperty("component", delegateExecution)
                .equals(componentProperties.getComponent())) {

                ComponentProperties componentProperty = new ComponentProperties();
                HashMap<String, String> propertyValues = new HashMap<>();

                retrieveBPMNProperties(delegateExecution).forEach(inputProperty -> {
                    String formattedInputProperty = "";

                    if (inputProperty.contains("_")) {
                        String splitInputProperty[] = inputProperty.split("_");
                        formattedInputProperty = splitInputProperty[splitInputProperty.length - 1];
                    } else {
                        formattedInputProperty = inputProperty;
                    }

                    String finalFormattedInputProperty = formattedInputProperty;
                    componentProperties.getProperties().forEach((property, value) -> {

                        if (finalFormattedInputProperty.equals(property)) {
                            propertyValues.put(property, value);
                        }
                    });

                });

                componentProperty.setComponent(componentProperties.getComponent());
                componentProperty.setProperties(propertyValues);

                updatedComponentProperties.add(componentProperty);

                System.out.println(componentProperties.getComponent());
                System.out.println(componentProperties.getProperties());

            }
        }
        return updatedComponentProperties;
    }

    /**
     * TODO: Change to Helper Method
     * @param delegateExecution
     * @return
     */
    public ArrayList<String> retrieveBPMNProperties(DelegateExecution delegateExecution) {
        ArrayList<String> deployComponents = new ArrayList<>();
        ServiceTask serviceTask = (ServiceTask) delegateExecution.getBpmnModelElementInstance();
        CamundaProperties camProperties = serviceTask.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).singleResult();

        for (CamundaProperty camundaProperty : camProperties.getCamundaProperties()) {
            if (camundaProperty.getCamundaName().equals("input")) {
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
