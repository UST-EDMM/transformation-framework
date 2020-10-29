package io.github.edmm.plugins.multi.orchestration.camunda;

import javax.inject.Named;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

@Named
public class SendDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        CamundaRestExchange camundaRestExchange = new CamundaRestExchange();
        camundaRestExchange.sendVariables();

    }
}
