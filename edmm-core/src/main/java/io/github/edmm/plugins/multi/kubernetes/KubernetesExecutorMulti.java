package io.github.edmm.plugins.multi.kubernetes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.core.execution.ExecutionException;
import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.model.Property;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.plugins.DeploymentExecutor;
import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.utils.Consts;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Deployment;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;
import lombok.var;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.edmm.plugins.kubernetes.KubernetesPlugin.STACKS_ENTRY;

public class KubernetesExecutorMulti extends DeploymentExecutor {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesExecutorMulti.class);
    private final List<ComponentProperties> properties = new ArrayList<>();
    private final List<String> stacks;

    public KubernetesExecutorMulti(ExecutionContext context, DeploymentTechnology deploymentTechnology) {
        super(context, deploymentTechnology);
        this.stacks = getStacks(context);
    }

    private static V1ConfigMap createConfigMap(String stackName, Map<String, Property> computedProps, File dir) {

        var config = new ConfigMapResourceRuntime(stackName, computedProps);
        config.build();
        try {
            File serviceYaml = new File(dir, stackName + "-config.yaml");
            FileUtils.writeStringToFile(serviceYaml, config.toYaml() + Consts.NL, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("Failed to create ConfigMap for stack '{}'", stackName, e);
            throw new TransformationException(e);
        }
        return config.getConfigMap();
    }

    public static Optional<V1Service> deployService(String stackName, File dir, CoreV1Api api) {
        V1Service result = null;
        try {
            // apply deployment
            File serviceYaml = new File(dir, stackName + "-service.yaml");
            V1Service service = (V1Service) Yaml.load(serviceYaml);
            // this throws an exception if already exists
            try {
                result = api.createNamespacedService(service.getMetadata().getNamespace(), service, true, null, null);
            } catch (ApiException e) {
                api.deleteNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace(), null,
                        null, null, null, null, null);
                result = api.createNamespacedService(service.getMetadata().getNamespace(), service, true, null, null);
            }
        } catch (IOException | ApiException e) {
            e.printStackTrace();
        }
        return Optional.of(result);

    }

    public static void deployDeployment(String stackName, File dir, AppsV1Api api) {
        try {
            // apply deployment
            File deployYaml = new File(dir, stackName + "-deployment.yaml");
            V1Deployment depl = (V1Deployment) Yaml.load(deployYaml);
            // this throws an exception if already exists
            try {
                api.createNamespacedDeployment(depl.getMetadata().getNamespace(), depl, true, null, null);
            } catch (ApiException e) {
                api.deleteNamespacedDeployment(depl.getMetadata().getName(), depl.getMetadata().getNamespace(), null,
                        null, null, null, null, null);
                api.createNamespacedDeployment(depl.getMetadata().getNamespace(), depl, true, null, null);
            }
        } catch (IOException | ApiException e) {
            logger.info("this happemns");
            e.printStackTrace();
        }

    }

    public static void deployConfigMap(String stackName, Map<String, Property> props, File dir, CoreV1Api api) {
        try {
            V1ConfigMap config = createConfigMap(stackName, props, dir);
            // this throws an exception if already exists
            try {
                api.createNamespacedConfigMap(config.getMetadata().getNamespace(), config, true, null, null);
            } catch (ApiException e) {
                api.deleteNamespacedConfigMap(config.getMetadata().getName(), config.getMetadata().getNamespace(), null,
                        null, null, null, null, null);
                api.createNamespacedConfigMap(config.getMetadata().getNamespace(), config, null, null, null);
            }

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    private String getLabel(String s) {
        return s.replace("_", "-");
    }

    private List<String> getStacks(ExecutionContext context) {
        Map<String, Object> values = context.getTransformation().getValues();
        return (List<String>) values.get(STACKS_ENTRY);
    }

    @Override
    public void execute() throws Exception {
        deploy();
    }

    public void deploy() {
        HashMap<String, String> outputVariables = new HashMap<>();
        File fileAccess = this.context.getDirectory();

        for (String stackName : this.stacks) {
            File compDir = new File(fileAccess, stackName);
            if (!compDir.exists()) {
                logger.warn("stack {} does not exist", stackName);
                continue;
            }

            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(compDir);
            pb.inheritIO();

            // hardcoded registry for now
            String registry = "localhost:32000/";
            // docker build &push
            try {
                pb.command("docker", "build", "-t", stackName + ":latest", ".");
                Process init = pb.start();
                init.waitFor();
                pb.command("docker", "tag", stackName + ":latest", registry + stackName);
                pb.start().waitFor();
                //pb.command("docker", "push", registry + stackName);
                //pb.start().waitFor();
            } catch (IOException | InterruptedException e) {
                logger.error("could not deploy comp: {}", stackName);
                e.printStackTrace();
            }

            try {
                ApiClient client = Config.defaultClient();
                AppsV1Api deploymentApi = new AppsV1Api();
                deploymentApi.setApiClient(client);
                CoreV1Api api = new CoreV1Api();
                api.setApiClient(client);

                // contains the runtime properties
                Optional<RootComponent> comp = context.getTransformation().getModel().getComponent(stackName);

                if (!comp.isPresent()) {
                    throw new ExecutionException("could not find stack " + stackName);
                }
                var resolvedVars = TopologyGraphHelper.resolvePropertyReferences(
                    context.getTransformation().getTopologyGraph(), comp.get(), comp.get().getProperties());
                deployConfigMap(stackName, resolvedVars, compDir, api);

                // deploy everything
                logger.info("deployed configMap for {}", stackName);
                deployDeployment(getLabel(stackName), compDir, deploymentApi);
                logger.info("deployed deployment for {}", stackName);
                Optional<V1Service> service = deployService(getLabel(stackName), compDir, api);
                logger.info("deployed service for {}", stackName);

                // read output
                for (var port : service.get().getSpec().getPorts()) {
                    outputVariables.put("nodeport", port.getNodePort().toString());
                    logger.info("the ’public’ nodeport is: {}", port.getNodePort().toString());

                }
                logger.info("the clusterIP is: {}", service.get().getSpec().getClusterIP());
                outputVariables.put("hostname", service.get().getSpec().getClusterIP());
                comp.get().addProperty("hostname", service.get().getSpec().getClusterIP());

                ComponentProperties propertiess = new ComponentProperties(
                    stackName,
                    outputVariables
                );

                properties.add(propertiess);

            } catch (IOException e) {
                logger.error("could not deploy comp: {}", stackName);
                e.printStackTrace();

            }

            try {
                logger.info("wait for component to start");
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        properties.forEach(x -> {
            System.out.println(x.getComponent());
            System.out.println(x.getProperties());
        });

    }

    public List<ComponentProperties> executeWithOutputProperty() {
        deploy();
        return properties;
    }

    @Override
    public void destroy() throws Exception {
        // TODO Auto-generated method stub

    }

}
