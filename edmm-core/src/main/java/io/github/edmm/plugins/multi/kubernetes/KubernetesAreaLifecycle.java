package io.github.edmm.plugins.multi.kubernetes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.plugin.AbstractLifecycle;
import io.github.edmm.core.plugin.PluginFileAccess;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.docker.Container;
import io.github.edmm.docker.DependencyGraph;
import io.github.edmm.model.component.Compute;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.relation.ConnectsTo;
import io.github.edmm.model.relation.HostedOn;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.kubernetes.support.ImageMappingVisitor;

import org.jgrapht.Graph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.edmm.plugins.kubernetes.KubernetesPlugin.STACKS_ENTRY;

public class KubernetesAreaLifecycle extends AbstractLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesAreaLifecycle.class);

    protected final Graph<RootComponent, RootRelation> graph;

    protected List<Container> containers = new ArrayList<>();
    protected DependencyGraph dependencyGraph;

    public KubernetesAreaLifecycle(TransformationContext context) {
        super(context);
        this.graph = context.getTopologyGraph();
    }

    @Override
    public void prepare() {
        List<Compute> computeComponents = context.getGroup().getGroupComponents().stream()
                .filter((comp) -> (comp instanceof Compute)).map((comp) -> (Compute) comp).collect(Collectors.toList());

        for (Compute compute : computeComponents) {
            Container stack = new Container();
            stack.addComponent(compute);
            containers.add(stack);
            populateComponentStacks(graph, containers, stack, compute);
        }
        dependencyGraph = new DependencyGraph(containers, graph);
    }

    @Override
    public void transform() {
        logger.info("Begin transformation to Kubernetes...");
        PluginFileAccess fileAccess = context.getFileAccess();
        for (Container stack : containers) {
            resolveBaseImage(stack);
            buildDockerfile(stack, fileAccess);
        }
        for (Container stack : containers) {
            // Build Kubernetes resource files
            KubernetesResourceBuilder resourceBuilder = new KubernetesResourceBuilder(stack, graph, fileAccess);
            resourceBuilder.populateResources();
        }
        logger.info("Transformation to Kubernetes successful");
    }

    @Override
    public void cleanup() {
        TopologicalOrderIterator<Container, ConnectsTo> topologicalIterator = new TopologicalOrderIterator<>(
                dependencyGraph.getReversedGraph());
        List<String> stackNames = StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(topologicalIterator, Spliterator.ORDERED), false)
                .map(Container::getName).collect(Collectors.toList());
        // Save stack names for later use
        context.putValue(STACKS_ENTRY, stackNames);
    }

    protected void resolveBaseImage(Container stack) {
        ImageMappingVisitor imageMapper = new ImageMappingVisitor();
        stack.getComponents().forEach(component -> component.accept(imageMapper));
        stack.setBaseImage(imageMapper.getBaseImage());
    }

    protected void buildDockerfile(Container stack, PluginFileAccess fileAccess) {
        DockerfileBuildingVisitor dockerfileBuilder = new DockerfileBuildingVisitor(stack, fileAccess);
        dockerfileBuilder.populateDockerfile();
    }

    private void populateComponentStacks(Graph<RootComponent, RootRelation> graph, List<Container> stacks,
            Container stack, RootComponent component) {
        Set<RootComponent> sourceComponents = TopologyGraphHelper.getSourceComponents(graph, component, HostedOn.class);
        if (sourceComponents.size() == 1) {
            RootComponent source = sourceComponents.stream().findFirst().orElseThrow(IllegalStateException::new);
            stack.addComponent(source);
            populateComponentStacks(graph, stacks, stack, source);
        } else {
            for (RootComponent source : sourceComponents) {
                Container newStack = new Container(stack);
                newStack.addComponent(source);
                stacks.add(newStack);
                stacks.remove(stack);
                populateComponentStacks(graph, stacks, newStack, source);
            }
        }
    }
}
