package io.github.edmm.plugins.terraform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.plugins.DeploymentExecutor;
import io.github.edmm.plugins.multi.model.OutputProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraformExecutor extends DeploymentExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TerraformExecutor.class);

    private static final String TERRAFORM_CMD = "terraform";
    private static final String TERRAFORM_INIT_CMD = "init";
    private static final String TERRAFORM_DEPLOY_CMD = "apply";
    private static final String TERRAFORM_DESTROY_CMD = "destroy";

    private static final String AUTO_APPROVE_OPTION = "-auto-approve";
    private static final String INPUT_VAR_OPTION = "-var";

    public TerraformExecutor(ExecutionContext context, DeploymentTechnology deploymentTechnology) {
        super(context, deploymentTechnology);
    }

    public void execute() throws InterruptedException, IOException, TimeoutException {
        executeProcess(TERRAFORM_CMD, TERRAFORM_INIT_CMD);
        executeProcess(resolveInputParams(TERRAFORM_CMD, TERRAFORM_DEPLOY_CMD, AUTO_APPROVE_OPTION));
    }

    @Override
    public List<OutputProperties> executeWithOutputProperty() throws Exception {
        return null;
    }

    public void destroy() throws InterruptedException, IOException, TimeoutException {
        executeProcess(resolveInputParams(TERRAFORM_CMD, TERRAFORM_DESTROY_CMD, AUTO_APPROVE_OPTION));
    }

    private String[] resolveInputParams(String... inputs) {
        List<String> inputVars = new ArrayList<>(Arrays.asList(inputs));
        context.getUserInputs().forEach(userInput ->
            inputVars.add(INPUT_VAR_OPTION + "=\"" + userInput.getName() + "=" + userInput.getValue() + "\"")
        );
        return inputVars.toArray(new String[0]);
    }
}
