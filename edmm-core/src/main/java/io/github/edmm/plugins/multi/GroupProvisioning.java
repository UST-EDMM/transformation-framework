/*******************************************************************************
 * Copyright (c) 2017 University of Stuttgart.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and the Apache License 2.0 which both accompany this distribution,
 * and are available at http://www.eclipse.org/legal/epl-v10.html
 * and http://www.apache.org/licenses/LICENSE-2.0
 *
 * Contributors:
 *    Karoline Saatkamp - initial API and implementation and/or initial documentation
 *******************************************************************************/
package io.github.edmm.plugins.multi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.edmm.model.DeploymentModel;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.multi.model_extensions.groupingGraph.Group;
import io.github.edmm.plugins.multi.model_extensions.groupingGraph.GroupProvisioningOrderGraph;
import io.github.edmm.plugins.multi.model_extensions.groupingGraph.OrderRelation;

import org.jgrapht.Graph;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedAcyclicGraph;

public class GroupProvisioning {

    public static GroupProvisioningOrderGraph contractGroupsInGPOG(GroupProvisioningOrderGraph gPoG, Group mergingGroup, Group mergedGroup) {
        GroupProvisioningOrderGraph graph = new GroupProvisioningOrderGraph();
        gPoG.vertexSet().forEach(v -> graph.addVertex(v));
        gPoG.edgeSet().forEach(e -> graph.addEdge(e.getSource(), e.getTarget()));
        CycleDetector cycleDetector = new CycleDetector(graph);
        // Former target element is added to the source element group
        mergingGroup.addAllToGroupComponents(mergedGroup.groupComponents);

        // For each edge related to the former target element a new edge has to be added
        Set<OrderRelation> outgoingRelations = graph.outgoingEdgesOf(mergedGroup).stream().collect(Collectors.toSet());
        Set<OrderRelation> incomingRelations = graph.incomingEdgesOf(mergedGroup).stream()
            .filter(ir -> !ir.getSource().equals(mergingGroup)).collect(Collectors.toSet());
        outgoingRelations.forEach(or -> graph.addEdge(mergingGroup, or.getTarget()));
        incomingRelations.forEach(ir -> graph.addEdge(ir.getSource(), mergingGroup));

        // Old edges and the former target element are removed
        graph.removeAllEdges(outgoingRelations);
        graph.removeAllEdges(incomingRelations);
        Set<RootComponent> movedGroupSet = mergedGroup.getGroupComponents();
        graph.removeAllEdges(graph.getAllEdges(mergingGroup, mergedGroup));
        graph.removeAllEdges(graph.getAllEdges(mergedGroup, mergingGroup));
        graph.removeVertex(mergedGroup);

        if (cycleDetector.detectCycles()) {
            mergingGroup.getGroupComponents().removeAll(movedGroupSet);
            return null;
        }
        return graph;
    }

    /**
     * topological sorting based on Kahn's algorithm
     * TopologicalOrderIterator would work as well?
     *
     * @param deployment
     * @return
     */
    public static List<Group> determineProvisiongingOrder(DeploymentModel deployment) {
        Queue<Group> queue = new LinkedList<>();
        List<Group> visitNodes = new ArrayList<>();
        List<Group> topologcialSorting = new ArrayList<>();
        GroupProvisioningOrderGraph gPOG = initializeGPOG(deployment);
        GroupProvisioningOrderGraph compressedGPOG = compressGPOG(gPOG);
        GroupProvisioningOrderGraph workingCopyGPOG = new GroupProvisioningOrderGraph();
        compressedGPOG.vertexSet().forEach(v -> workingCopyGPOG.addVertex(v));
        compressedGPOG.edgeSet().forEach(e -> workingCopyGPOG.addEdge(e.getSource(), e.getTarget()));

        queue.addAll(workingCopyGPOG.vertexSet().stream().filter(v -> workingCopyGPOG.incomingEdgesOf(v).isEmpty()).collect(Collectors.toSet()));
        int i = 0;
        while (!queue.isEmpty()) {
            Group group = queue.poll();
            if (visitNodes.contains(group)) {
                continue;
            }
            visitNodes.add(group);
            topologcialSorting.add(i, group);
            Set<OrderRelation> outgoingRelations = new HashSet<>(workingCopyGPOG.outgoingEdgesOf(group));
            workingCopyGPOG.removeAllEdges(outgoingRelations);
            workingCopyGPOG.removeVertex(group);
            queue.addAll(workingCopyGPOG.vertexSet().stream().filter(v -> workingCopyGPOG.incomingEdgesOf(v).isEmpty())
                .filter(v -> !visitNodes.contains(v)).collect(Collectors.toSet()));
            i++;
        }

        return topologcialSorting;
    }

    /**
     * @param gPoG
     * @return compressed GPOG with a reduced number of Provisioning Groups
     */
    public static GroupProvisioningOrderGraph compressGPOG(GroupProvisioningOrderGraph gPoG) {
        TransitiveClosure TRANSITIVECLOSURE = TransitiveClosure.INSTANCE;
        TransitiveReduction TRANSITIVEREDUCTION = TransitiveReduction.INSTANCE;

        GroupProvisioningOrderGraph tempGPOG;
        TRANSITIVEREDUCTION.reduce(gPoG);
        List<OrderRelation> orderRelationsQueue = gPoG.edgeSet().stream()
            .filter(e -> e.getSource().getTechnology().equals(e.getTarget().getTechnology())).collect(Collectors.toList());

        while (!orderRelationsQueue.isEmpty()) {
            OrderRelation relation = orderRelationsQueue.get(0);
            tempGPOG = contractGroupsInGPOG(gPoG, relation.getSource(), relation.getTarget());
            if (tempGPOG != null) {
                Group compressedGroup = tempGPOG.vertexSet().stream()
                    .filter(v -> v.getGroupComponents().containsAll(relation.getSource().getGroupComponents())).findFirst().get();

                orderRelationsQueue.remove(relation);
                List<OrderRelation> replacedOutgoingRelations = orderRelationsQueue.stream()
                    .filter(r -> compressedGroup.getGroupComponents().containsAll(r.getSource().getGroupComponents())).collect(Collectors.toList());
                List<OrderRelation> replacingOutgoingRelations = tempGPOG.outgoingEdgesOf(compressedGroup).stream()
                    .filter(r -> replacedOutgoingRelations.stream().anyMatch(or -> or.getTarget().equals(r.getTarget())))
                    .collect(Collectors.toList());
                orderRelationsQueue.removeAll(replacedOutgoingRelations);
                orderRelationsQueue.addAll(replacingOutgoingRelations);

                List<OrderRelation> replacedIncomingRelations = orderRelationsQueue.stream()
                    .filter(r -> r.getTarget().equals(relation.getTarget())).collect(Collectors.toList());
                List<OrderRelation> replacingIncomingRelations = tempGPOG.incomingEdgesOf(compressedGroup).stream()
                    .filter(r -> replacedIncomingRelations.stream().anyMatch(or -> or.getSource().equals(r.getSource())))
                    .collect(Collectors.toList());
                orderRelationsQueue.removeAll(replacedIncomingRelations);
                orderRelationsQueue.addAll(replacingIncomingRelations);

                gPoG.clearGraph();
                tempGPOG.vertexSet().forEach(v -> gPoG.addVertex(v));
                tempGPOG.edgeSet().forEach(e -> gPoG.addEdge(e.getSource(), e.getTarget()));
                tempGPOG.clearGraph();
            }
            orderRelationsQueue.remove(relation);
        }
        Set<Technology> labels = new HashSet<>();
        gPoG.vertexSet().forEach(v -> labels.add(v.getTechnology()));
        DirectedAcyclicGraph<Group, OrderRelation> transitiveClosure = new DirectedAcyclicGraph<>(OrderRelation.class);
        gPoG.vertexSet().forEach(v -> transitiveClosure.addVertex(v));
        gPoG.edgeSet().forEach(e -> transitiveClosure.addEdge(e.getSource(), e.getTarget()));
        TRANSITIVECLOSURE.closeDirectedAcyclicGraph(transitiveClosure);
        gPoG.clearGraph();
        transitiveClosure.vertexSet().forEach(v -> gPoG.addVertex(v));
        transitiveClosure.edgeSet().forEach(e -> gPoG.addEdge(e.getSource(), e.getTarget()));

        for (Technology label : labels) {
            List<Group> groupsWithSameLabel = gPoG.vertexSet().stream()
                .filter(v -> v.getTechnology().equals(label)).collect(Collectors.toList());

            if (groupsWithSameLabel.size() > 1) {
                Queue<Group> groupQueue = new LinkedList<>();
                groupQueue.addAll(groupsWithSameLabel);

                while (!groupQueue.isEmpty()) {
                    Group group = groupQueue.poll();
                    List<Group> connectedGroups = new ArrayList<>();
                    gPoG.outgoingEdgesOf(group).stream().forEach(o -> connectedGroups.add(o.getTarget()));
                    gPoG.incomingEdgesOf(group).stream().forEach(o -> connectedGroups.add(o.getSource()));

                    List<Group> transitiveIndependentGroups = groupsWithSameLabel.stream().filter(g -> !g.equals(group))
                        .filter(g -> !connectedGroups.contains(g)).collect(Collectors.toList());
                    if (!transitiveIndependentGroups.isEmpty()) {
                        tempGPOG = contractGroupsInGPOG(gPoG, transitiveIndependentGroups.get(0), group);
                        if (tempGPOG != null) {
                            groupsWithSameLabel.remove(group);
                            gPoG.clearGraph();
                            tempGPOG.vertexSet().forEach(v -> gPoG.addVertex(v));
                            tempGPOG.edgeSet().forEach(e -> gPoG.addEdge(e.getSource(), e.getTarget()));
                            tempGPOG.clearGraph();
                        }
                    }
                }
            }
        }
        return gPoG;
    }

    /**
     * Creates a Provisioning Order Graph from a given Topology Template. Each source and target of the edges
     * in the GPOG are the other way around as the relationships in the Topology Template. Each Node Template is
     * contained in a separated Group
     *
     * @param model
     * @return Group Provisioning Order Graph with edges for the provisioning dependencies
     */
    public static GroupProvisioningOrderGraph initializeGPOG(DeploymentModel model) {
        final Graph<RootComponent, RootRelation> graph = model.getTopology();
        GroupProvisioningOrderGraph gPOG = new GroupProvisioningOrderGraph();
        Map<String, Group> compSets = new HashMap<>();

        // init nodes
        graph.vertexSet().forEach((component) -> {
            Group group = new Group(model.getTechnology(component));
            group.addToGroupComponents(component);
            compSets.put(component.getName(), group);

            gPOG.addVertex(group);
        });

        //init edges
        for (Group sourceComponent : gPOG.vertexSet()) {
            for (RootRelation relation : sourceComponent.getGroupComponents().stream().findFirst().get().getRelations()) {
                Group targetComponent = compSets.get(relation.getTarget());
                gPOG.addEdge(targetComponent, sourceComponent);
            }
        }
        return gPOG;
    }

}
