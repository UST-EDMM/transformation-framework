package io.github.edmm.plugins.multi.model_extensions.groupingGraph;

import org.jgrapht.graph.DefaultEdge;

public class OrderRelation extends DefaultEdge {

    public Group getSource() {
        return (Group) super.getSource();
    }

    public Group getTarget() {
        return (Group) super.getTarget();
    }
}