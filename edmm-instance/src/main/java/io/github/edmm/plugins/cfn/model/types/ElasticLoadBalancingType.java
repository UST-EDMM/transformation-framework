package io.github.edmm.plugins.cfn.model.types;

import io.github.edmm.model.edimm.ComponentType;

public enum ElasticLoadBalancingType {
    LoadBalancer(ComponentType.Compute);

    ComponentType componentType;

    ElasticLoadBalancingType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public ComponentType toComponentType() {
        return this.componentType;
    }
}
