package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.Collections;
import java.util.HashMap;

import io.github.edmm.plugins.multi.model.message.CamundaMessage;
import io.github.edmm.plugins.multi.model.message.ProcessVariables;
import io.github.edmm.plugins.multi.model.message.Trigger;
import io.github.edmm.plugins.multi.model.message.Variables;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public class CamundaRestExchange {

    public void completeTask(String componentName, HashMap<String, String> variables) {
        RestTemplate restTemplate = new RestTemplate();
        String participant = "http://localhost:5000/engine-rest/message";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        CamundaMessage camundaMessage = setCamundaMessage(componentName, variables);
        HttpEntity <CamundaMessage> entity = new HttpEntity<>(camundaMessage, headers);

        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            String json = ow.writeValueAsString(camundaMessage);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        restTemplate.exchange(participant, HttpMethod.POST, entity, String.class).getBody();
    }

    public void triggerTechnology(String sourceComponent, String targetComponent,
                                  HashMap<String, String> variables) {

        System.out.println("5");

        RestTemplate restTemplate = new RestTemplate();
        String participant = "http://localhost:5000/orchestration/trigger";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Trigger trigger = new Trigger();
        trigger.setMultiId("123");
        trigger.setSourceComponent(sourceComponent);
        trigger.setTargetComponent(targetComponent);
        trigger.setEnvironmentVariables(variables);

        System.out.println("5");
        System.out.println(trigger);

        HttpEntity <Trigger> entity = new HttpEntity<>(trigger, headers);
        restTemplate.exchange(participant, HttpMethod.POST, entity, String.class).getBody();
    }

    public void sendVariables() {

    }

    private CamundaMessage setCamundaMessage(String componentName, HashMap<String, String> variables) {
        CamundaMessage camundaMessage = new CamundaMessage();
        ProcessVariables processVariables = new ProcessVariables();
        Variables messageVariables = new Variables();

        // Adds messageVariables to processVariables
        messageVariables.setValue(variables);
        processVariables.setVariables(messageVariables);

        // Creates CamundaMessage
        camundaMessage.setMessageName(componentName);
        camundaMessage.setTenantId("12345");
        camundaMessage.setProcessVariables(processVariables);

        return camundaMessage;
    }
}


