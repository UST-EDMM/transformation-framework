package io.github.edmm.plugins;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.plugins.multi.model.ComponentProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

public abstract class DeploymentExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentExecutor.class);

    protected final ExecutionContext context;
    private final DeploymentTechnology deploymentTechnology;

    public DeploymentExecutor(ExecutionContext context, DeploymentTechnology deploymentTechnology) {
        this.context = context;
        this.deploymentTechnology = deploymentTechnology;
    }

    public abstract void execute() throws Exception;

    public abstract List<ComponentProperties> executeWithOutputProperty() throws Exception;

    public abstract void destroy() throws Exception;

    protected void executeProcess(String... commandParams)
        throws InterruptedException, TimeoutException, IOException {
        executeProcess(context.getDirectory(), commandParams);
    }

    protected void executeProcess(File directory, String... commandParams)
        throws InterruptedException, TimeoutException, IOException {
        ProcessExecutor processExecutor = new ProcessExecutor()
            .directory(directory)
            .command(commandParams)
            .redirectError(Slf4jStream.of(getClass()).asError())
            .redirectOutput(Slf4jStream.of(getClass()).asDebug())
            .readOutput(true);
        try {
            ProcessResult result = processExecutor.execute();

            if (result.getExitValue() > 0) {
                throw new IllegalStateException("Exit status was not 0!");
            }
        } catch (Exception e) {
            logger.error("Error executing command: {} while deploying the \"{}\" application using {}",
                processExecutor.getCommand().toString().replace(",", ""),
                context.getTransformation().getModel().getName(),
                deploymentTechnology.getName(),
                e);
            throw e;
        }
    }
}
