package io.github.edmm.plugins.multi;

import io.github.edmm.model.visitor.ComponentVisitor;

public interface MultiVisitor extends ComponentVisitor {
    default void populate() {
    }
}
