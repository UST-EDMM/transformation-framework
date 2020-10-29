package io.github.edmm.plugins.multi.kubernetes;

import java.util.Arrays;
import java.util.Map;

import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.model.Property;
import io.github.edmm.plugins.kubernetes.model.KubernetesResource;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapBuilder;
import io.kubernetes.client.util.Yaml;
import lombok.var;

public final class ConfigMapResourceRuntime implements KubernetesResource {
    String[] blacklist = { "key_name", "public_key", "hostname" };

    private final String stackName;
    private final String namespace = "default";
    private final Map<String, Property> props;
    private V1ConfigMap configMap;

    public ConfigMapResourceRuntime(String stackName, Map<String, Property> props) {
        this.stackName = stackName;
        this.props = props;
    }

    @Override
    public void build() {

        var configMapBuilder = new V1ConfigMapBuilder().withApiVersion("v1").withKind("ConfigMap").
            withNewMetadata().withName(getName())
                .withNamespace(namespace).endMetadata();

        for (var prop : props.entrySet()) {

            if (Arrays.asList(blacklist).contains(prop.getKey())) {
                continue;
            }
            configMapBuilder = configMapBuilder.addToData(prop.getKey().toUpperCase(), prop.getValue().getValue());

        }
        configMap = configMapBuilder.build();
    }

    @Override
    public String toYaml() {
        if (configMap == null) {
            throw new TransformationException("Resource not yet built, call build() first");
        }

        return Yaml.dump(configMap);
    }

    public V1ConfigMap getConfigMap() {
        return configMap;
    }

    @Override
    public String getName() {
        String label = stackName.replace("_", "-");
        return label + "-config";
    }
}
