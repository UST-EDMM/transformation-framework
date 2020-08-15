package io.github.edmm.plugins.multi.model_extensions.groupingGraph;

import java.util.LinkedList;

import org.jgrapht.graph.DefaultDirectedGraph;

public class GroupProvisioningOrderGraph extends DefaultDirectedGraph<Group, OrderRelation> {

    public GroupProvisioningOrderGraph() {
        super(OrderRelation.class);
    }

    public void removeAllEdges(GroupProvisioningOrderGraph graph) {
        LinkedList<OrderRelation> copy = new LinkedList<OrderRelation>();
        for (OrderRelation e : graph.edgeSet()) {
            copy.add(e);
        }
        graph.removeAllEdges(copy);
    }

    public void clearGraph() {
        removeAllEdges(this);
        removeAllVertices(this);
    }

    public void removeAllVertices(GroupProvisioningOrderGraph graph) {
        LinkedList<Group> copy = new LinkedList<Group>();
        for (Group v : graph.vertexSet()) {
            copy.add(v);
        }
        graph.removeAllVertices(copy);

    }
}
