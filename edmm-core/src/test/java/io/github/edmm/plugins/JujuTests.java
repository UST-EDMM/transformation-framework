package io.github.edmm.plugins;

import io.github.edmm.core.transformation.Transformation;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.model.DeploymentModel;
import io.github.edmm.plugins.juju.JujuPlugin;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JujuTests extends PluginTest {

    private static final Logger logger = LoggerFactory.getLogger(JujuTests.class);

    private TransformationContext context;

    public JujuTests() throws Exception {
        super(Files.createTempDirectory("juju-").toFile());
    }

    @Before
    public void init() throws Exception {
        ClassPathResource sourceResource = new ClassPathResource("templates");
        ClassPathResource templateResource = new ClassPathResource("templates/scenario_iaas_single_compute.yml");
        DeploymentModel model = DeploymentModel.of(templateResource.getFile());
        Transformation transformation = mock(Transformation.class);
        when(transformation.getModel()).thenReturn(model);
        logger.info("Source directory is '{}'", sourceResource.getFile());
        logger.info("Target directory is '{}'", targetDirectory);
        context = new TransformationContext(transformation, sourceResource.getFile(), targetDirectory);
    }

    @Test
    public void testLifecycleExecution() {
        executeLifecycle(new JujuPlugin(), context);
    }
}
