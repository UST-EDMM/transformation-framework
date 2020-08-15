package io.github.edmm.plugins.multi;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.core.plugin.ExecutionPlugin;
import io.github.edmm.core.plugin.TransformationPlugin;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.model.parameters.InputParameter;

import com.google.common.collect.Sets;

import static io.github.edmm.model.parameters.ParameterType.NAME;

public class MultiPlugin extends TransformationPlugin<MultiLifecycle> implements ExecutionPlugin {

    public static final String TARGET_NAMESPACE = "target-namespace";
    public static final DeploymentTechnology MULTI = DeploymentTechnology.builder()
        .id("multi")
        .name("multi")
        .deploymentSupported(true)
        .executionParameters(Sets.newHashSet(
            InputParameter.of(TARGET_NAMESPACE, NAME, "default", "Multi namespace for the deployment")
        ))
        .build();

    public MultiPlugin() {
        super(MULTI);
    }

    @Override
    public MultiLifecycle getLifecycle(TransformationContext context) {
        return new MultiLifecycle(context);
    }

    @Override
    public void execute(ExecutionContext context) throws Exception {

    }

    @Override
    public void destroy(ExecutionContext context) throws Exception {

    }
}
