package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.Collections;
import java.util.HashMap;

import javax.inject.Named;

import io.github.edmm.plugins.multi.model.message.InitiateRequest;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Named
public class InitiateDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(InitiateDelegate.class);

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {

        final String modelId = "modelId";
        final String tenantId = delegateExecution.getTenantId();

        delegateExecution.setVariable(modelId, tenantId);
        initiateRESTCall(delegateExecution);

    }

    public void initiateRESTCall(DelegateExecution delegateExecution) {

        /* Sets up parameters for InitiateRequest
         * Sample shown below
         * {
         *   "variables" : {
         *     "initiator" : {
         *       "value" : "false"
         *     }
         *   }
         * }
         */
        HashMap<String, Object> variables = new HashMap<>();
        HashMap<String, String> value = new HashMap<>();
        value.put("value", "false");
        variables.put("initiator", value);

        // Sets InitiateRequest parameters
        InitiateRequest initiateRequest = new InitiateRequest();
        initiateRequest.setVariables(variables);

        RestTemplate restTemplate = new RestTemplate();
        ((SimpleClientHttpRequestFactory)restTemplate.getRequestFactory()).setReadTimeout(2000);
        HttpHeaders headers = new HttpHeaders();

        DelegateHelper.retrieveBPMNProperties("participant", delegateExecution).forEach(participant -> {
            final String tenantId = delegateExecution.getTenantId();
            final String participantURI =
                participant + "/engine-rest/process-definition/key/workflow/tenant-id/" + tenantId + "/start";
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<InitiateRequest> entity = new HttpEntity<>(initiateRequest, headers);

            try {
                logger.info("Starting REST call with following JSON body:");
                System.out.println(DelegateHelper.parseObjectToJSON(initiateRequest));
                restTemplate.exchange(participantURI, HttpMethod.POST, entity, String.class);
            } catch (ResourceAccessException e) {
                System.out.println(e);
            }
        });
    }

}
