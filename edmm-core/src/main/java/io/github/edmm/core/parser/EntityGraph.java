package io.github.edmm.core.parser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.edmm.core.parser.support.GraphHelper;
import io.github.edmm.core.parser.support.GraphNormalizer;

import lombok.Getter;
import lombok.NonNull;
import lombok.var;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Represents the content of a template as a graph.
 */
public class EntityGraph extends SimpleDirectedGraph<Entity, EntityGraph.Edge> {

    public static final EntityId ROOT = new EntityId("0");
    public static final EntityId MULTI_ID = ROOT.extend("multi_id");
    public static final EntityId OWNER = ROOT.extend("owner");
    public static final EntityId COMPONENTS = ROOT.extend("components");
    public static final EntityId COMPONENT_TYPES = ROOT.extend("component_types");
    public static final EntityId RELATION_TYPES = ROOT.extend("relation_types");
    public static final EntityId ORCHESTRATION_TECHNOLOGY = ROOT.extend("orchestration_technology");
    public static final EntityId PARTICIPANTS = ROOT.extend("participants");


    public EntityGraph() {
        // the edge supplier isn't needed if we use always the function
        // addEdge(V sourceVertex, V targetVertex, E e) of the AbstractBaseClass
        super(null, null, false);
        addVertex(new MappingEntity(ROOT, this));
    }

    public EntityGraph(InputStream is) {
        this();
        Node snakeNode = new Yaml().compose(new InputStreamReader(is));
        populateGraph(snakeNode, ROOT);
        GraphNormalizer.normalize(this);
    }

    public Optional<Entity> getEntity(EntityId id) {
        return this.vertexSet().stream()
            .filter(entity -> id.equals(entity.getId()))
            .findFirst();
    }

    public Optional<Entity> getOrchestrationTechnologyEntity() {
        return GraphHelper.findMappingEntity(this, ORCHESTRATION_TECHNOLOGY.getName(), ROOT);
    }

    public Optional<Entity> getParticipantsEntity() {
        return GraphHelper.findMappingEntity(this, PARTICIPANTS.getName(), ROOT);
    }

    public String getParticipantEndpoint(String participant) {

        for (var p : getParticipantsEntity().get().getChildren()) {
            if (p.getName().equals(participant)) {
                for (var partner : p.getChildren()) {
                    if (partner.getName().equals("endpoint")) {
                        ScalarEntity scalarEntity = (ScalarEntity) partner;
                         return scalarEntity.getValue();
                    }
                }
            }
        }
        throw new RuntimeException("No participant with endpoint available");
    }

    public HashMap<String, String> getParticipants() {

        HashMap<String, String> participants = new HashMap<>();

        for (var p : getParticipantsEntity().get().getChildren()) {
            for (var partner : p.getChildren()) {
                if (partner.getName().equals("endpoint")) {
                    ScalarEntity scalarEntity = (ScalarEntity) partner;
                        participants.put(p.getName(), scalarEntity.getValue());
                }
            }
        }
        return participants;
    }

    public String getParticipantFromComponentName(String componentName) {

        for (var p : getParticipantsEntity().get().getChildren()) {
            for (var partner : p.getChildren()) {
                if (partner.getName().equals("components")) {
                    SequenceEntity sequenceEntity = (SequenceEntity) partner;
                    for (var comp : sequenceEntity.getChildren()) {
                        ScalarEntity scalarEntity = (ScalarEntity) comp;
                        if (scalarEntity.getValue().equals(componentName)) {
                            return p.getName();
                        }
                    }
                }
            }
        }
        throw new RuntimeException("No participant with component name available");
    }

    public String getMultiId() {
        ScalarEntity scalarEntity = (ScalarEntity) getEntity(MULTI_ID).get();
        return scalarEntity.getValue();
    }

    public String getOwner() {
        ScalarEntity scalarEntity = (ScalarEntity) getEntity(OWNER).get();
        return scalarEntity.getValue();
    }

    public Optional<Entity> getEntity(List<String> path) {
        return getEntity(new EntityId(path));
    }

    public void addEntity(Entity entity) {
        if (!entity.getParent().isPresent()) {
            return;
        }
        Entity parent = entity.getParent().get();
        boolean added = addVertex(entity);
        if (added) {
            addEdge(parent, entity, new Edge(entity.getName(), parent, entity));
        }
    }

    public void removeEntity(Entity entity) {
        // Remove edges from and to this entity
        Stream<Edge> incomingEdges = incomingEdgesOf(entity).stream();
        Stream<Edge> outgoingEdges = outgoingEdgesOf(entity).stream();
        Stream.concat(incomingEdges, outgoingEdges).forEach(this::removeEdge);
        // Remove vertex
        removeVertex(entity);
    }

    public void replaceEntity(Entity source, Entity target) {
        // Collect incoming and outgoing edges of the source
        Set<Pair<Entity, String>> predecessors = incomingEdgesOf(source).stream()
            .map(c -> Pair.of(c.getSource(), c.getName())).collect(Collectors.toSet());
        Set<Pair<Entity, String>> successors = outgoingEdgesOf(source).stream()
            .map(c -> Pair.of(c.getTarget(), c.getName())).collect(Collectors.toSet());
        removeVertex(source);
        addVertex(target);
        // Redirect existing edges to target entity
        predecessors.forEach(p -> addEdge(p.getLeft(), target, new Edge(p.getRight(), p.getLeft(), target)));
        successors.forEach(p -> addEdge(target, p.getLeft(), new Edge(p.getRight(), target, p.getLeft())));
    }

    public Set<Entity> getChildren(EntityId id) {
        Optional<Entity> entity = getEntity(id);
        if (entity.isPresent()) {
            return entity.get().getChildren();
        } else {
            return Collections.emptySet();
        }
    }

    public void addEdge(Entity source, Entity target, String name) {
        addEdge(source, target, new Edge(name, source, target));
    }

    private void populateGraph(Node node, EntityId id) {
        if (node instanceof MappingNode) {
            MappingNode mappingNode = (MappingNode) node;
            MappingEntity mappingEntity = new MappingEntity(id, this);
            addEntity(mappingEntity);
            for (NodeTuple tuple : mappingNode.getValue()) {
                String key = ((ScalarNode) tuple.getKeyNode()).getValue();
                Node childNode = tuple.getValueNode();
                EntityId childId = id.extend(key);
                populateGraph(childNode, childId);
            }
        } else if (node instanceof SequenceNode) {
            SequenceNode sequenceNode = (SequenceNode) node;
            SequenceEntity sequenceEntity = new SequenceEntity(id, this);
            addEntity(sequenceEntity);
            for (int i = 0; i < sequenceNode.getValue().size(); i++) {
                Node childNode = sequenceNode.getValue().get(i);
                String childName;
                if (childNode instanceof MappingNode) {
                    NodeTuple childTuple = ((MappingNode) childNode).getValue().get(0);
                    childName = ((ScalarNode) childTuple.getKeyNode()).getValue();
                    childNode = childTuple.getValueNode();
                } else {
                    childName = String.valueOf(i);
                }
                EntityId childId = id.extend(childName);
                populateGraph(childNode, childId);
            }
        } else if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            String value = scalarNode.getValue();
            if (Stream.of("~", "null").collect(Collectors.toSet()).contains(value)) {
                value = null;
            }
            ScalarEntity scalarEntity = new ScalarEntity(value, id, this);
            addEntity(scalarEntity);
        }
    }

    public void generateYamlOutput(Writer writer) {
        Optional<Entity> root = this.getEntity(ROOT);
        if (root.isPresent()) {
            Map<String, Object> graphAsMap = createMapFromGraph(root.get());
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setExplicitStart(true);
            new Yaml(options).dump(graphAsMap, writer);
        } else {
            throw new IllegalStateException("No ROOT element defined");
        }
    }

    private Map<String, Object> createMapFromGraph(Entity entity) {
        HashMap<String, Object> map = new HashMap<>();
        entity.getDirectChildren().forEach(child -> {
            if (child instanceof MappingEntity) {
                Map<String, Object> childMap = createMapFromGraph(child);
                map.put(child.getName(), childMap.isEmpty() ? null : childMap);
            } else if (child instanceof SequenceEntity) {
                List<Map> list = new ArrayList<>();
                child.getDirectChildren().forEach(grandChild -> {
                    HashMap<String, Object> localMap = new HashMap<>();
                    if (grandChild instanceof ScalarEntity) {
                        localMap.put(grandChild.getName(), ((ScalarEntity) grandChild).getValue());
                    } else {
                        Map<String, Object> grandChildMap = createMapFromGraph(grandChild);
                        localMap.put(grandChild.getName(), grandChildMap.isEmpty() ? null : grandChildMap);
                    }
                    list.add(localMap);
                });
                if (!list.isEmpty()) {
                    map.put(child.getName(), list);
                }
            } else if (child instanceof ScalarEntity) {
                ScalarEntity scalar = (ScalarEntity) child;
                map.put(child.getName(), scalar.getValue());
            }
        });
        return map;
    }

    @Getter
    public static class Edge {

        private final String name;
        private final Entity source;
        private final Entity target;

        public Edge(@NonNull String name, @NonNull Entity source, @NonNull Entity target) {
            this.name = name;
            this.source = source;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return Objects.equals(source, edge.source) &&
                Objects.equals(target, edge.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target);
        }

        @Override
        public String toString() {
            return String.format("%s =%s=> %s", source.getId(), name, target.getId());
        }
    }
}
