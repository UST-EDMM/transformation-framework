package io.github.edmm.plugins.multi;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.core.plugin.AbstractLifecycle;
import io.github.edmm.core.plugin.PluginFileAccess;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.model.Property;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.DeploymentExecutor;
import io.github.edmm.plugins.multi.ansible.AnsibleAreaLifecycle;
import io.github.edmm.plugins.multi.ansible.AnsibleExecutor;
import io.github.edmm.plugins.multi.kubernetes.KubernetesAreaLifecycle;
import io.github.edmm.plugins.multi.kubernetes.KubernetesExecutorMulti;
import io.github.edmm.plugins.multi.model.ComponentResources;
import io.github.edmm.plugins.multi.model.Plan;
import io.github.edmm.plugins.multi.model.PlanStep;
import io.github.edmm.plugins.multi.model_extensions.groupingGraph.Group;
import io.github.edmm.plugins.multi.terraform.TerraformAreaLifecycle;
import io.github.edmm.plugins.multi.terraform.TerraformExecutor;

import com.google.gson.Gson;
import lombok.var;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiLifecycle extends AbstractLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MultiLifecycle.class);

    private final List<AbstractLifecycle> groupLifecycles;

    public MultiLifecycle(TransformationContext context) {

        super(context);
        groupLifecycles = new ArrayList<>();
    }

    public void preprareGroups(List<Group> sortedGroups) {

        EdgeReversedGraph<RootComponent, RootRelation> dependencyGraph = new EdgeReversedGraph<>(
                context.getModel().getTopology());

        for (int i = 0; i < sortedGroups.size(); i++) {
            var group = sortedGroups.get(i);

            // init contexts
            AbstractLifecycle grpLifecycle;
            String subDir = "step" + i + "_" + group.getTechnology().toString();
            File targetDir = new File(context.getTargetDirectory(), subDir);
            TransformationContext groupContext = new TransformationContext(subDir, context.getModel(),
                    context.getDeploymentTechnology(), context.getSourceDirectory(), targetDir, group);
            if (group.getTechnology() == Technology.ANSIBLE) {
                grpLifecycle = new AnsibleAreaLifecycle(groupContext);
            } else if (group.getTechnology() == Technology.TERRAFORM) {
                grpLifecycle = new TerraformAreaLifecycle(groupContext);
            } else if (group.getTechnology() == Technology.KUBERNETES) {
                grpLifecycle = new KubernetesAreaLifecycle(groupContext);
            } else {
                String error = String.format("could not find technology: %s for components %s", group.getTechnology(),
                        group);
                throw new IllegalArgumentException(error);
            }
            groupLifecycles.add(grpLifecycle);

            var subgraph = new AsSubgraph<>(dependencyGraph, group.getGroupComponents());

            group.setSubGraph(subgraph);

        }

    }

    public void createWorkflow(List<Group> sortedGroups) {

        Plan plan = new Plan();
        for (int i = 0; i < sortedGroups.size(); i++) {
            var group = sortedGroups.get(i);
            var step = new PlanStep(group.getTechnology());
            TopologicalOrderIterator<RootComponent, RootRelation> subIterator = new TopologicalOrderIterator<>(
                    sortedGroups.get(i).subGraph);
            while (subIterator.hasNext()) {
                RootComponent comp = subIterator.next();

                var propLists = TransformationHelper.collectRuntimeEnvInputOutput(context.getTopologyGraph(), comp);
                step.components
                        .add(new ComponentResources(comp.getName(), propLists.getFirst(), propLists.getSecond()));
            }

            plan.steps.add(step);
        }

        Writer writer = new StringWriter();
        context.getModel().getGraph().generateYamlOutput(writer);

        try {
            context.getFileAccess().write("state.yaml", writer.toString());
            context.getFileAccess().write("execution.plan.json", plan.toJson());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void transform() {
        logger.info("Begin transformation to Multi...");
        // new Groupprovisioning
        List<Group> sortedGroups = GroupProvisioning.determineProvisiongingOrder(context.getModel());
        preprareGroups(sortedGroups);
        // suboptimally do 3 lifecycle step for groups here
        for (int i = 0; i < sortedGroups.size(); i++) {
            groupLifecycles.get(i).prepare();
            groupLifecycles.get(i).transform();
            groupLifecycles.get(i).cleanup();
        }
        createWorkflow(sortedGroups);

        logger.info("Transformation to Multi successful");

        execute();
    }

    // this could be another lifecycle step, but is planned to be completely
    // independent in the future
    public void execute() {
        PluginFileAccess fileAccess = context.getFileAccess();
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter y to continue with orchestration");
        String input = scanner.next();

        if (!input.equals("y")) {
            return;
        }
        var gson = new Gson();
        try {

            Plan plan = gson.fromJson(context.getFileAccess().readToStringTargetDir("execution.plan.json"), Plan.class);

            logger.info("Begin orchestration ...");

            for (int i = 0; i < plan.steps.size(); i++) {
                List<RootComponent> components = new ArrayList<>();
                for (var c : plan.steps.get(i).components) {
                    Optional<RootComponent> comp = context.getModel().getComponent(c.getName());
                    comp.ifPresent(components::add);
                }
                Technology tech = plan.steps.get(i).tech;

                DeploymentExecutor visitorContext;
                ExecutionContext orchContext = new ExecutionContext(groupLifecycles.get(i).getTransformationContext());
                // at the moment they need access to the file access and graph(this could be
                // changed)
                logger.info("deployment_tool: {} ", tech);
                if (tech == Technology.ANSIBLE) {
                    visitorContext = new AnsibleExecutor(orchContext, null);
                } else if (tech == Technology.TERRAFORM) {
                    visitorContext = new TerraformExecutor(orchContext, null);
                } else if (tech == Technology.KUBERNETES) {
                    visitorContext = new KubernetesExecutorMulti(orchContext, null);
                } else {
                    String error = String.format("could not find technology: %s for component %s", tech, components);
                    throw new IllegalArgumentException(error);
                }
                logger.info("execute next tech");
                visitorContext.execute();

            }
            Writer writer = new StringWriter();
            context.getModel().getGraph().generateYamlOutput(writer);
            fileAccess.write("state.yaml", writer.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

        scanner.close();

    }

    private Map<String, Property> getComputedProperties(RootComponent component) {
        Map<String, Property> allProps = TopologyGraphHelper.resolveComponentStackProperties(context.getTopologyGraph(),
                component);
        Map<String, Property> computedProps = new HashMap<>();
        for (var prop : allProps.entrySet()) {
            if (prop.getValue().isComputed() || prop.getValue().getValue() == null
                    || prop.getValue().getValue().startsWith("$")) {
                computedProps.put(prop.getKey(), prop.getValue());
            }
        }
        return TopologyGraphHelper.resolvePropertyReferences(context.getTopologyGraph(), component, computedProps);
    }

}
