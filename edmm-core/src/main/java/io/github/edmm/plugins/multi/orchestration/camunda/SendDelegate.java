package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.inject.Named;

import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.plugins.multi.model.message.CamundaMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Named
public class SendDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(SendDelegate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean successfulStatusCode = false;

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {

        final String modelId = "modelId";
        final String properties = "properties";
        final String component = "component";

        delegateExecution.setVariable(modelId, delegateExecution.getTenantId());

        /* Constructs parameters for outgoing request as a CamundaMessage
         * Sample shown below
         * {
         *   "messageName" : "db",
         *   "tenantId" : "123",
         *   "processVariables" : {
         *     "properties" : {
         *       "value" : [ {
         *         "component" : "db",
         *         "properties" : {
         *           "hostname" : "123.123.123"
         *         }
         *       } ]
         *     }
         *   }
         * }
         */
        ComponentProperties[] inputsList = objectMapper.convertValue(
            delegateExecution.getVariable(properties), ComponentProperties[].class);
        HashMap<String, Object> processVariables = new HashMap<>();
        HashMap<String, ArrayList<ComponentProperties>> value = new HashMap<>();
        value.put("value", new ArrayList<>(prepareInputProperties(inputsList, delegateExecution)));
        processVariables.put(properties, value);

        // Sets CamundaMessage parameters
        CamundaMessage camundaMessage = new CamundaMessage(
            DelegateHelper.retrieveBPMNProperty(component, delegateExecution),
            delegateExecution.getVariable(modelId).toString(),
            processVariables
        );

        do {
            logger.info("Starting REST call with following JSON body:");
            System.out.println(DelegateHelper.parseObjectToJSON(camundaMessage));
            startRESTCall(camundaMessage, delegateExecution);
            Thread.sleep(10000);
        } while (!this.successfulStatusCode);
    }

    public void startRESTCall(CamundaMessage camundaMessage, DelegateExecution delegateExecution) {

        final String participant = "participant";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        String participantURI = DelegateHelper.retrieveBPMNProperty(participant, delegateExecution) + "engine-rest/message";
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<CamundaMessage> entity = new HttpEntity<>(camundaMessage, headers);

        try {
            int statusCodeValue = restTemplate.exchange(participantURI, HttpMethod.POST, entity, String.class).getStatusCodeValue();
            if (statusCodeValue == 204) {
                this.successfulStatusCode = true;
            }
        } catch (HttpClientErrorException clientErrorException) {
            logger.info("SEND not possible, endpoint not available yet");
        }
    }

    public ArrayList<ComponentProperties> prepareInputProperties(ComponentProperties[] inputsList, DelegateExecution delegateExecution) {

        final String component = "component";
        final String bpmnProperty = "input";

        ArrayList<ComponentProperties> updatedComponentProperties = new ArrayList<>();

        for (ComponentProperties componentProperties : inputsList) {
            if (DelegateHelper.retrieveBPMNProperty(component, delegateExecution)
                .equals(componentProperties.getComponent())) {

                ComponentProperties componentProperty = new ComponentProperties();
                HashMap<String, String> propertyValues = new HashMap<>();

                DelegateHelper.retrieveBPMNProperties(bpmnProperty, delegateExecution).forEach(inputProperty -> {
                    String formattedInputProperty = "";

                    if (inputProperty.contains("_")) {
                        String[] splitInputProperty = inputProperty.split("_");
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
            }
        }
        return updatedComponentProperties;
    }
}
