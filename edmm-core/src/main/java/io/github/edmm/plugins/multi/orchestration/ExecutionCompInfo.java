package io.github.edmm.plugins.multi.orchestration;

import java.util.Map;

import io.github.edmm.model.Property;
import io.github.edmm.model.component.RootComponent;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecutionCompInfo {

    RootComponent component;
    Map<String, Property> properties;


}
