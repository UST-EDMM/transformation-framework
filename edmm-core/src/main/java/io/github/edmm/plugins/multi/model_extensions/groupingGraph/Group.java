package io.github.edmm.plugins.multi.model_extensions.groupingGraph;

import java.util.HashSet;
import java.util.Set;

import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.multi.Technology;

import org.jgrapht.Graph;

public class Group {

    public Graph<RootComponent, RootRelation> subGraph;
    public Set<RootComponent> groupComponents;
    private Technology tech;


    public Group(Technology tech) {
        this.tech = tech;
        groupComponents = new HashSet<>();
    }

    public Set<RootComponent> getGroupComponents() {
        return this.groupComponents;
    }

    public void setGroupComponents(Set<RootComponent> groupComponents) {
        this.groupComponents = groupComponents;
    }

    public void addToGroupComponents(RootComponent nodeTemplate) {
        this.groupComponents.add(nodeTemplate);
    }

    public void addAllToGroupComponents(Set<RootComponent> components) {
        this.groupComponents.addAll(components);
    }

    public void clearGroupComponents() {
        this.groupComponents.clear();
    }

    public Technology getTechnology() {
        return tech;
    }

    public void setTechnology(Technology tech) {
        this.tech = tech;
    }

    public Graph<RootComponent, RootRelation> getSubGraph() {
        return subGraph;
    }

    public void setSubGraph(Graph<RootComponent, RootRelation> subGraph) {
        this.subGraph = subGraph;
    }

    public String toString() {

        String s = String.format("Used Technology: %s\t", tech.toString());
        s += groupComponents.toString();
        return s;
    }

}
