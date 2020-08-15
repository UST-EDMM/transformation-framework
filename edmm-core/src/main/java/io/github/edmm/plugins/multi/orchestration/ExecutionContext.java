package io.github.edmm.plugins.multi.orchestration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.edmm.model.DeploymentModel;
import io.github.edmm.utils.Consts;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionContext {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionContext.class);
    private final File directory;
    private final DeploymentModel deploymentModel;

    public ExecutionContext(File directory, DeploymentModel deploymentModel) {
        this.directory = directory;
        this.deploymentModel = deploymentModel;
    }

    public DeploymentModel getDeploymentModel() {
        return deploymentModel;
    }

    public File getDirAccess() {
        return directory;
    }

    public void write(String relativePath, String data) throws IOException {
        File file = new File(directory, relativePath);
        try {
            FileUtils.writeStringToFile(file, data + Consts.NL, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to write data to file '{}'", file);
            throw e;
        }
    }
}
