package io.github.edmm.plugins.multi.workflows;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.edmm.core.TemplateHelper;
import io.github.edmm.core.plugin.PluginFileAccess;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.multi.MultiPlugin;
import io.github.edmm.plugins.multi.Technology;
import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.plugins.multi.model.ComponentResources;
import io.github.edmm.plugins.multi.model.Plan;
import io.github.edmm.plugins.multi.model.PlanStep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import freemarker.template.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Templating {

    private static final Logger logger = LoggerFactory.getLogger(Templating.class);
    protected final TransformationContext context;
    protected final Configuration cfg = TemplateHelper.forClasspath(MultiPlugin.class, "/plugins/choreo");
    private final Map<String, Object> data = new HashMap<>();

    public Templating(TransformationContext context) {
        this.context = context;
    }

    public void createInitiate(HashMap<String, String> participantMap) {
        PluginFileAccess fileAccess = context.getFileAccess();
        List<ComponentProperties> component = new ArrayList<>();
        List<String> participants = new ArrayList<>();

        participantMap.forEach((key, value) -> {
            if (!context.getModel().getOwner().equals(key)) {
                participants.add(value);
            }
        });

        data.put("components", component);
        data.put("participants", participants);

        try {
            fileAccess.write("bpmn/StartEvent.txt", TemplateHelper.toString(cfg, "StartEvent.txt", data));
        } catch (IOException e) {
            logger.error("Failed to write StartEvent file", e);
            throw new TransformationException(e);
        }
    }

    public BPMNSteps createSendStep(PlanStep currentStep, PlanStep nextStep) {

        BPMNSteps bpmnSteps = new BPMNSteps();

        // Last component
        String relatedComponent = currentStep.getComponents().get(currentStep.getComponents().size() - 1).getName();

        for (ComponentResources component : nextStep.getComponents()) {
            context.getModel().getComponent(component.getName()).get().getRelations().forEach(relatedUndefinedComponent -> {
                    if (relatedUndefinedComponent.getTarget().equals(relatedComponent)) {

                        String componentName = context.getModel().getParticipantFromComponentName(component.getName());

                        bpmnSteps.setStep(nextStep.getStep());
                        bpmnSteps.setComponent(relatedUndefinedComponent.getTarget());
                        bpmnSteps.setInput(component.getRuntimeInputParams());
                        bpmnSteps.setParticipant(context.getModel().getParticipantEndpoint(componentName));

                    }
                }
            );
        }
        return bpmnSteps;
    }

    public BPMNSteps createReceiveStep(PlanStep currentStep, PlanStep lastStep, PlanStep nextStep) {

        BPMNSteps bpmnSteps = new BPMNSteps();
        String relatedComponent;

        if (lastStep == null) {

            if (nextStep.tech.equals(Technology.KUBERNETES)) {
                relatedComponent = nextStep.getComponents().get(nextStep.getComponents().size() - 1).getName();
            } else {
                relatedComponent = nextStep.getComponents().stream().findFirst().get().getName();
            }

            for (RootRelation relation : context.getModel().getComponent(relatedComponent).get().getRelations()) {
                for (ComponentResources component : currentStep.getComponents()) {
                    if (component.getName().equals(relation.getTarget())) {
                        bpmnSteps.setComponent(component.getName());
                        bpmnSteps.setStep(currentStep.getStep());
                    }
                }
            }

        } else if (nextStep == null) {

            if (currentStep.tech.equals(Technology.KUBERNETES)) {
                relatedComponent = currentStep.getComponents().get(currentStep.getComponents().size() - 1).getName();
            } else {
                relatedComponent = currentStep.getComponents().stream().findFirst().get().getName();
            }

            for (RootRelation relation : context.getModel().getComponent(relatedComponent).get().getRelations()) {
                for (ComponentResources component : lastStep.getComponents()) {
                    if (component.getName().equals(relation.getTarget())) {

                        bpmnSteps.setComponent(component.getName());
                        bpmnSteps.setStep(currentStep.getStep());
                    }
                }
            }
        }

        return bpmnSteps;
    }

    public List<BPMNSteps> createPlan(Plan plan) {
        System.out.println("INSIDE SKELETON");

        List<BPMNSteps> bpmnStepsList = new ArrayList<>();
        List<String> taskSequence = new ArrayList<>();

        int i = 0;
        int adjustedSteps = 0;
        for (PlanStep step : plan.steps) {
            BPMNSteps bpmnSteps = new BPMNSteps();
            bpmnSteps.setStep(i + adjustedSteps);
            bpmnSteps.setTech(step.tech);
            bpmnSteps.setParticipantEndpoint(step.getParticipantEndpoint());
            bpmnSteps.setComponents(step.getComponents());

            if (i == 0) {

                if (step.tech.equals(Technology.UNDEFINED)) {

                    // RECEIVE
                    BPMNSteps intermediateBPMNStep = createReceiveStep(step, null, plan.steps.get(i + 1));
                    System.out.println("RECEIVE, i IS 0");
                    intermediateBPMNStep.setTaskType(BPMNSteps.TaskType.RECEIVE);
                    taskSequence.add("RECEIVE");
                    bpmnStepsList.add(intermediateBPMNStep);

                } else  {

                    if (plan.steps.get(i + 1).tech.equals(Technology.UNDEFINED)) {

                        // DEPLOY
                        System.out.println("DEPLOY, i IS 0");
                        bpmnSteps.setTaskType(BPMNSteps.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepsList.add(bpmnSteps);
                        adjustedSteps++;

                        BPMNSteps intermediateBPMNStep = createSendStep(step, plan.steps.get(i + 1));
                        intermediateBPMNStep.setStep(i + adjustedSteps);
                        System.out.println("SEND, i IS 0");
                        intermediateBPMNStep.setTaskType(BPMNSteps.TaskType.SEND);
                        taskSequence.add("SEND");
                        bpmnStepsList.add(intermediateBPMNStep);

                    } else {
                        // DEPLOY
                        System.out.println("DEPLOY, i IS 0ss");
                        bpmnSteps.setTaskType(BPMNSteps.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepsList.add(bpmnSteps);
                    }
                }
            }

            if (i > 0 && i < plan.steps.size() - 1) {

                if (step.tech.equals(Technology.UNDEFINED)) {

                    BPMNSteps intermediateBPMNStep = createReceiveStep(step, null, plan.steps.get(i + 1));
                    intermediateBPMNStep.setStep(i + adjustedSteps);
                    System.out.println("RECEIVE, MID");
                    intermediateBPMNStep.setTaskType(BPMNSteps.TaskType.RECEIVE);
                    taskSequence.add("RECEIVE");
                    bpmnStepsList.add(intermediateBPMNStep);

                } else {

                    if (plan.steps.get(i - 1).tech.equals(Technology.UNDEFINED)) {

                       if (!taskSequence.get(taskSequence.size() - 1).equals("RECEIVE")) {
                           BPMNSteps bpmnSteps1 = createReceiveStep(step, null, plan.steps.get(i + 1));
                           System.out.println("RECEIVE, MID MID");
                           bpmnSteps1.setTaskType(BPMNSteps.TaskType.RECEIVE);
                           taskSequence.add("RECEIVE");
                           bpmnStepsList.add(bpmnSteps1);
                       }

                        System.out.println("DEPLOY, MID MID");
                        bpmnSteps.setTaskType(BPMNSteps.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepsList.add(bpmnSteps);

                    } else if (plan.steps.get(i + 1).tech.equals(Technology.UNDEFINED)) {

                        // DEPLOY
                        System.out.println("DEPLOY, MID MID 2");
                        bpmnSteps.setTaskType(BPMNSteps.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepsList.add(bpmnSteps);
                        adjustedSteps++;

                        BPMNSteps intermediateBPMNStep = createSendStep(step, plan.steps.get(i + 1));
                        intermediateBPMNStep.setStep(i + adjustedSteps);
                        System.out.println("SEND, MID MID");
                        intermediateBPMNStep.setTaskType(BPMNSteps.TaskType.SEND);
                        taskSequence.add("SEND");
                        bpmnStepsList.add(intermediateBPMNStep);
                    } else {
                        // DEPLOY
                        System.out.println("DEPLOY, i IS MID MID");
                        bpmnSteps.setTaskType(BPMNSteps.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepsList.add(bpmnSteps);
                    }
                }
            }

            if (i == plan.steps.size() - 1) {

                if (step.tech.equals(Technology.UNDEFINED)) {
                    // SEND
                    if (!taskSequence.get(taskSequence.size() - 1).equals("SEND")) {

                        BPMNSteps intermediateBPMNStep = createSendStep(plan.steps.get(i - 1), step);
                        System.out.println("SEND, i IS list");
                        intermediateBPMNStep.setTaskType(BPMNSteps.TaskType.SEND);
                        taskSequence.add("SEND");
                        bpmnStepsList.add(intermediateBPMNStep);
                    }

                } else {
                    // DEPLOY
                    System.out.println("DEPLOY, i IS list");
                    bpmnSteps.setTaskType(BPMNSteps.TaskType.DEPLOY);
                    taskSequence.add("DEPLOY");
                    bpmnStepsList.add(bpmnSteps);
                }

            }

            System.out.println(taskSequence);
            i++;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            context.getFileAccess().write("executions.plan.json", gson.toJson(bpmnStepsList));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bpmnStepsList;
    }

    public void createBPMNFromPlan(List<BPMNSteps> bpmnSteps) {

        PluginFileAccess fileAccess = context.getFileAccess();
        List<String> owner = new ArrayList<>();
        List<BPMNSteps> deployTasks = new ArrayList<>();
        List<BPMNSteps> sendTasks = new ArrayList<>();
        List<BPMNSteps> receiveTasks = new ArrayList<>();

        for (BPMNSteps bpmnStep : bpmnSteps) {

            switch (bpmnStep.getTaskType()) {
                case DEPLOY: deployTasks.add(bpmnStep);
                    break;
                case SEND: sendTasks.add(bpmnStep);
                    break;
                case RECEIVE: receiveTasks.add(bpmnStep);
                    break;
                default: logger.info("Task Type can not be found.");
            }
        }

        owner.add(context.getModel().getParticipantEndpoint(context.getModel().getOwner()));

        data.put("deployTasks", deployTasks);
        data.put("sendTasks", sendTasks);
        data.put("receiveTasks", receiveTasks);
        data.put("owner", owner);

        try {
            fileAccess.write("bpmn/MainEvent.txt", TemplateHelper.toString(cfg, "MainEvent.txt", data));
        } catch (IOException e) {
            logger.error("Failed to write MainEvent file", e);
            throw new TransformationException(e);
        }
    }

    public void mergeFiles() throws IOException {

        File finalWorkflow = new File( context.getTargetDirectory() +  "/bpmn/Workflow.bpmn");
        finalWorkflow.delete();

        File file1 = new File(context.getTargetDirectory() + "/bpmn/StartEvent.txt");
        File file2 = new File(context.getTargetDirectory() + "/bpmn/MainEvent.txt");

        File[] files = new File[2];
        files[0] = file1;
        files[1] = file2;

        File mergedFile = finalWorkflow;

        FileWriter fileWriter = null;
        BufferedWriter bufferedWriter = null;

        try {
            fileWriter = new FileWriter(mergedFile, true);
            bufferedWriter = new BufferedWriter(fileWriter);
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        for (File f : files) {
            logger.info("Merging files");
            FileInputStream fis;

            try {
                fis = new FileInputStream(f);
                BufferedReader in = new BufferedReader(new InputStreamReader(fis));

                String aLine;
                while ((aLine = in.readLine()) != null) {
                    if (bufferedWriter != null) {
                        bufferedWriter.write(aLine);
                    }
                    if (bufferedWriter != null) {
                        bufferedWriter.newLine();
                    }
                }

                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
