package io.github.edmm.plugins.multi.orchestration.camunda;

import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper Class for common Camunda JavaDelegate implementations.
 */
public class DelegateHelper {

    private static final Logger logger = LoggerFactory.getLogger(DelegateHelper.class);

    /**
     * Retrieves all properties (<camunda:properties>) with the same property name from the BPMN file.
     * This can be for instance "partcipant" with multiple host instances
     *
     * @param propertyName Property name
     * @param delegateExecution Current DelegateExecution
     * @return
     */
    protected static ArrayList<String> retrieveBPMNProperties(String propertyName, DelegateExecution delegateExecution) {
        ArrayList<String> bpmnProperties = new ArrayList<>();
        ServiceTask serviceTask = (ServiceTask) delegateExecution.getBpmnModelElementInstance();
        CamundaProperties camProperties = serviceTask.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).singleResult();

        for (CamundaProperty camundaProperty : camProperties.getCamundaProperties()) {
            if (camundaProperty.getCamundaName().equals(propertyName)) {
                bpmnProperties.add(camundaProperty.getCamundaValue());
            }
        }
        logger.info("Retrieving BPMN properties");
        System.out.println(bpmnProperties);
        return bpmnProperties;
    }

    /**
     * Retrieves a specific BPMN property (<camunda:property>) from the BPMN file.
     *
     * @param property Property name
     * @param delegateExecution Current DelegateExecution
     * @return
     */
    protected static String retrieveBPMNProperty(String property, DelegateExecution delegateExecution) {
        String propertyValue = null;

        ServiceTask serviceTask = (ServiceTask) delegateExecution.getBpmnModelElementInstance();
        CamundaProperties camProperties = serviceTask.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).singleResult();

        for (CamundaProperty camundaProperty : camProperties.getCamundaProperties()) {
            if (camundaProperty.getCamundaName().equals(property)) {
                propertyValue = camundaProperty.getCamundaValue();
            }
        }
        logger.info("Retrieving BPMN property");
        System.out.println(propertyValue);
        return propertyValue;
    }

    protected static String parseObjectToJSON(Object object) {
        String json = "";
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            json = ow.writeValueAsString(object);
            logger.info("Object parsed to JSON");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

}
