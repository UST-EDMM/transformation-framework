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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@Named
public class DeployDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(SendDelegate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        deploy(delegateExecution);
    }

    @SuppressWarnings("checkstyle:EmptyLineSeparator")
    public void deploy(DelegateExecution delegateExecution) {

        /* Sets up the parameters for the DeployRequest that has to be sent to EDMM Web
         * Sample shown below
         * {
         *     "modelId": "123",
         *     "correlationId": "eb0fbb4b-d564-4aa8-a7e3-cec5aba9414f",
         *     "components": [
         *         "pet_clinic"
         *     ],
         *     "inputs": [
         *         {
         *             "component": "db",
         *             "properties": {
         *                 "hostname": "123.123.123
         *             }
         *         }
         *     ]
         * }
         */
        delegateExecution.setVariable("modelId", delegateExecution.getTenantId());
        String modelId = (String) delegateExecution.getVariable("modelId");
        String correlationId = (String) delegateExecution.getVariable("correlationId");
        ComponentProperties[] inputsList = {};

        if (delegateExecution.getVariable("properties") != null) {
            inputsList = objectMapper.convertValue(
                delegateExecution.getVariable("properties"), ComponentProperties[].class);
        }

        // Sets DeployRequest parameters
        DeployRequest deployRequest = new DeployRequest(
            modelId,
            correlationId == null ? null : UUID.fromString(correlationId),
            DelegateHelper.retrieveBPMNProperties("component", delegateExecution),
            new ArrayList<>(Arrays.asList(inputsList))
        );

        startRESTCall(deployRequest, delegateExecution);
    }

    public void startRESTCall(DeployRequest deployRequest, DelegateExecution delegateExecution) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        String participant = DelegateHelper.retrieveBPMNProperty("participant", delegateExecution) + "orchestration/deploy";
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<DeployRequest> entity = new HttpEntity<>(deployRequest, headers);

        logger.info("Starting REST call with following JSON body:");
        System.out.println(DelegateHelper.parseObjectToJSON(deployRequest));
        String result = restTemplate.exchange(participant, HttpMethod.POST, entity, String.class).getBody();

        try {
            delegateExecution.removeVariables();
            DeployResult deployResult = objectMapper.readValue(result, DeployResult.class);
            logger.info("Reading out deployed result parameters");
            System.out.println(DelegateHelper.parseObjectToJSON(deployResult));

            // Sets returned deployed result parameters
            setCamundaProperties(deployResult, delegateExecution);
        } catch (JsonProcessingException e) {
            logger.info("Reading results failed");
            e.printStackTrace();
        }
    }

    public void setCamundaProperties(DeployResult deployResult, DelegateExecution delegateExecution) {
        delegateExecution.setVariable("modelId", deployResult.getModelId());
        delegateExecution.setVariable("correlationId", deployResult.getCorrelationId().toString());
        delegateExecution.setVariable("properties", deployResult.getOutput());
        logger.info("New Camunda process variables set");
    }
}
