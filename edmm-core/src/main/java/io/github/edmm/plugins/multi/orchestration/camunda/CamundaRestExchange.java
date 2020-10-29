package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.plugins.multi.model.message.DeployRequest;
import io.github.edmm.plugins.multi.model.message.DeployResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public class CamundaRestExchange {

    public void triggerTechnology(String sourceComponent, String targetComponent,
                                  HashMap<String, String> variables) throws JsonProcessingException {

        RestTemplate restTemplate = new RestTemplate();
        String participant = "http://localhost:5000/orchestration/deploy";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        DeployRequest deployRequest = new DeployRequest();
        deployRequest.setModelId("123");
        ArrayList<String> array = new ArrayList<>();
        array.add("ubuntu_db");
        deployRequest.setComponents(array);
        ArrayList<ComponentProperties> array2 = new ArrayList<>();
        ComponentProperties properties = new ComponentProperties(null, null);
        array2.add(properties);
        deployRequest.setInputs(array2);

        HttpEntity <DeployRequest> entity = new HttpEntity<>(deployRequest, headers);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        /*ObjectWriter ow2 = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json2 = ow2.writeValueAsString(objectValue);
        System.out.println(json2);*/

        String object = restTemplate.exchange(participant, HttpMethod.POST, entity, String.class).getBody();

        System.out.println(object);

        DeployResult deployResult = objectMapper.readValue(object, DeployResult.class);
        System.out.println(deployResult);
        System.out.println(deployResult.getCorrelationId());
        System.out.println(deployResult.getOutput());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("DONE");
    }

}


