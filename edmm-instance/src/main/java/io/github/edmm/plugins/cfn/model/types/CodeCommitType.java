package io.github.edmm.plugins.cfn.model.types;

import io.github.edmm.model.edimm.ComponentType;

public enum CodeCommitType {
    Repository(ComponentType.Software_Component);

    ComponentType componentType;

    CodeCommitType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public ComponentType toComponentType() {
        return this.componentType;
    }
}
