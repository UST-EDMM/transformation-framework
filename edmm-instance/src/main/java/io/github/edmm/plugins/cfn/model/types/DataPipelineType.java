package io.github.edmm.plugins.cfn.model.types;

import io.github.edmm.model.edimm.ComponentType;

public enum DataPipelineType {
    Pipeline(ComponentType.Software_Component);

    ComponentType componentType;

    DataPipelineType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public ComponentType toComponentType() {
        return this.componentType;
    }
}
