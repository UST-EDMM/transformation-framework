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
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Map<String, TransformationContext> store = new ConcurrentHashMap<>();
    private static List<AbstractLifecycle> groupLifecycles = null;

    public MultiLifecycle(TransformationContext context) {

        super(context);
        groupLifecycles = new ArrayList<>();
    }

    public MultiLifecycle(String id) {
        super(store.get(id));
    }

    public Boolean isTransformationContextAvailable(String id) {
        return store.containsKey(id);
    }

    public void putContext() {
        store.put(context.getId(), context);
        System.out.println(context.getId());
    }

    public void getContext() {
        System.out.println(context.getId());
        System.out.println(groupLifecycles);
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

            System.out.println(group.groupComponents.stream().findFirst());

            var step = new PlanStep(group.getTechnology());
            TopologicalOrderIterator<RootComponent, RootRelation> subIterator = new TopologicalOrderIterator<>(
                sortedGroups.get(i).subGraph);
            while (subIterator.hasNext()) {
                RootComponent comp = subIterator.next();

                var propLists = TransformationHelper.collectRuntimeEnvInputOutput(context.getTopologyGraph(), comp);
                step.components
                    .add(new ComponentResources(comp.getName(), propLists.getFirst(), propLists.getSecond()));

                System.out.println(comp.getName());

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

        store.put(context.getId(), context);

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
        System.out.println(groupLifecycles);
        logger.info("Transformation to Multi successful");

        //execute();
    }

    /**
     * Assigns the sent process variables by Camunda to the specific lifecycle graphs
     *
     * @param sourceComponent Source component that has to be updated
     * @param targetComponent Target component that has to be updated
     * @param variables Sent process variables by Camunda
     */
    public void assignRuntimeVariablesToLifecycles(String sourceComponent, String targetComponent,
                                                   Map<String, String> variables) {

        updateSourceComponent(sourceComponent, variables);
        updateTargetComponentAndExecute(targetComponent, variables);

    }

    /**
     * Updates the target component by the sent process variables and executes
     * the specified technology of the target component
     *
     * @param targetComponent Target component that has to be updated
     * @param variables Sent process variables by Camunda
     */
    public void updateTargetComponentAndExecute(String targetComponent, Map<String, String> variables) {

        AbstractLifecycle targetLifecycle = null;

        // Looks up the lifecycle of the target component and assigns them to targetLifecycle
        for (AbstractLifecycle groupLifecycle : groupLifecycles) {
            for (var component : groupLifecycle.getTransformationContext().getGroup().groupComponents) {
                if (component.getName().equals(targetComponent)) {
                    targetLifecycle = groupLifecycle;
                }
            }
        }

        // If targetLifecycle is available, the target component is queried and updated with the sent process variables
        if (targetLifecycle != null) {
            Technology technology = targetLifecycle.getTransformationContext().getGroup().getTechnology();
            targetLifecycle.getTransformationContext().getGroup().groupComponents.forEach(component -> {

                //updateVariables(component, variables);
            });

            executeTechnology(targetLifecycle, technology);
        }
    }

    /**
     * Executes the technology with given lifecycle and technology
     *
     * @param lifecycle Specific lifecycle of the technology created by the workflow generation
     * @param technology Specific technology that has to be executed with given lifecycle
     */
    public void executeTechnology(AbstractLifecycle lifecycle, Technology technology) {

        DeploymentExecutor visitorContext;
        ExecutionContext orchContext = new ExecutionContext(lifecycle.getTransformationContext());

        if (technology == Technology.ANSIBLE) {
            visitorContext = new AnsibleExecutor(orchContext, null);
        } else if (technology == Technology.TERRAFORM) {
            visitorContext = new TerraformExecutor(orchContext, null);
        } else if (technology == Technology.KUBERNETES) {
            visitorContext = new KubernetesExecutorMulti(orchContext, null);
        } else {
            throw new IllegalArgumentException("Technology could not be found.");
        }
        logger.info("execute next tech");
        try {
            System.out.println(visitorContext.executeWithOutputProperty());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Updates the source component by the sent process variables
     *
     * @param sourceComponent Source component that has to be updated
     * @param variables Sent process variables by Camunda
     */
    public void updateSourceComponent(String sourceComponent, Map<String, String> variables) {

        // Determine the source component and update the graph
        for (AbstractLifecycle groupLifecycle : groupLifecycles) {
            groupLifecycle.getTransformationContext().getGroup().groupComponents.forEach(component -> {

                if (component.getName().equals(sourceComponent)) {
                    updateVariables(component, variables);
                }
            });
        }
    }

    /**
     * Updates the properties/variables by the sent process variables
     *
     * @param component Component that has to be updated by the process variables
     * @param variables Sent process variables by Camunda
     */
    public void updateVariables(RootComponent component, Map<String, String> variables) {
        Map<String, Property> properties = component.getProperties();

        variables.forEach((variablesKey, variablesValue) -> {
            if (component.getProperty(variablesKey).isPresent() &&
                (component.getProperty(variablesKey).get().getValue() == null ||
                component.getProperty(variablesKey).get().getValue().isEmpty())) {

                Property property = properties.get(variablesKey);
                property.setValue(variablesValue);
                logger.info("Updating property from component");
            }

        });
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
