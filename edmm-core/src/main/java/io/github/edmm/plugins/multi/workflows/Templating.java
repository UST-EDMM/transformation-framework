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

/**
 * Templating class for the creation of a PlanSequence and transformation of PlanSequence to BPMN file
 */
public class Templating {

    private static final Logger logger = LoggerFactory.getLogger(Templating.class);
    protected final TransformationContext context;
    protected final Configuration cfg = TemplateHelper.forClasspath(MultiPlugin.class, "/plugins/choreo");
    private final Map<String, Object> data = new HashMap<>();
    private List<List<BPMNStep>> multiReceiveStepList = new ArrayList<>();
    private List<List<BPMNStep>> multiSendStepList = new ArrayList<>();



    public Templating(TransformationContext context) {
        this.context = context;
    }

    /**
     * Creates the Initiate Sequence as a text file
     * @param participantMap Map of all participants of EDMM model
     */
    public void createBPMNInitiateSequence(HashMap<String, String> participantMap) {
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
            fileAccess.write("bpmn/StartingSequence.txt", TemplateHelper.toString(cfg, "StartingSequence.txt", data));
        } catch (IOException e) {
            logger.error("Failed to write StartingSequence file", e);
            throw new TransformationException(e);
        }
    }

    /**
     * Creates a Send Step for the Plan Sequence. This is done by comparing the current step and the next step in the Plan.
     * If the next step is UNDEFINED e.g. (P1-STEP1: ANSIBLE, P2-STEP2: UNDEFINED), the relation between the current step (STEP1)
     * and the next step (STEP2) is determined and Send Sequence is created. A relation for instance would be ${db.hostname}
     * value from P1 that is needed to deploy the UNDEFINED step in P2.
     *
     */
    public List<BPMNStep> createSendStep(PlanStep currentStep, PlanStep nextStep) {

        List<BPMNStep> bpmnSteps = new ArrayList<>();
        BPMNStep bpmnStep = new BPMNStep();

        // Retrieves the component from the current step that is related to the next step
        String relatedComponent = currentStep.getComponents().get(currentStep.getComponents().size() - 1).getName();

        // Loops through all components from next step and compares it with the current step; If a match is found,
        // the Send Sequence with the relation is created
        for (ComponentResources component : nextStep.getComponents()) {
            context.getModel().getComponent(component.getName()).get().getRelations().forEach(relatedUndefinedComponent -> {
                    if (relatedUndefinedComponent.getTarget().equals(relatedComponent)) {

                        String componentName = context.getModel().getParticipantFromComponentName(component.getName());

                        bpmnStep.setStep(nextStep.getStep());
                        bpmnStep.setComponent(relatedUndefinedComponent.getTarget());
                        bpmnStep.setInput(component.getRuntimeInputParams());
                        bpmnStep.setParticipant(context.getModel().getParticipantEndpoint(componentName));

                        bpmnSteps.add(bpmnStep);
                    }
                }
            );
        }
        return bpmnSteps;
    }

    /**
     * Creates a Receive Step for the Plan Sequence. This is done by comparing the current step and the next step in the Plan.
     * If the current step contains an UNDEFINED technology and the next step has a technology set, e.g. (P1-STEP1: UNDEFINED,
     * P2-STEP2: ANSIBLE), a Receive Sequence is created by determining the relation between STEP1 and STEP2.
     */
    public List<BPMNStep> createReceiveStep(PlanStep currentStep, PlanStep nextStep) {

        List<BPMNStep> bpmnSteps = new ArrayList<>();
        BPMNStep bpmnStep = new BPMNStep();
        String relatedComponent;

        if (nextStep != null) {

            // Retrieves the component of the next step that has a relation with the current step
            if (nextStep.tech.equals(Technology.KUBERNETES)) {
                relatedComponent = nextStep.getComponents().get(nextStep.getComponents().size() - 1).getName();
            } else {
                relatedComponent = nextStep.getComponents().stream().findFirst().get().getName();
            }

            // Loops through all components of current step and finds a match between the current and next step
            for (RootRelation relation : context.getModel().getComponent(relatedComponent).get().getRelations()) {
                for (ComponentResources component : currentStep.getComponents()) {
                    if (component.getName().equals(relation.getTarget())) {
                        bpmnStep.setComponent(component.getName());
                        bpmnStep.setStep(currentStep.getStep());
                        bpmnSteps.add(bpmnStep);
                    }
                }
            }
        }
        return bpmnSteps;
    }

    /**
     * Transforms a Plan to a Execution Plan Sequence, which includes and creates a list of BPMN steps.
     * This is done by comparing the current step of the plan with previous and next steps and determining the
     * specific case for the sequence.
     */
    public List<BPMNStep> createExecutionPlanSequence(Plan plan) {

        List<BPMNStep> bpmnStepList = new ArrayList<>();
        // Task Sequence is used and added to determine the Task Sequence order (DEPLOY, SEND, RECEIVE)
        List<String> taskSequence = new ArrayList<>();

        int i = 0;
        int adjustedSteps = 0;
        for (PlanStep step : plan.steps) {

            // Creates BPMN step for the current looped step
            BPMNStep bpmnStep = new BPMNStep();
            // Adjusts the current looped step, if a new step is added in between (RECEIVE, DEPLOY)
            bpmnStep.setStep(i + adjustedSteps);
            bpmnStep.setTech(step.tech);
            bpmnStep.setParticipantEndpoint(step.getParticipantEndpoint());
            bpmnStep.setComponents(step.getComponents());

            if (i == 0) {

                // If the current step is UNDEFINED (No technology provided)
                if (step.tech.equals(Technology.UNDEFINED)) {

                    // RECEIVE
                    List<BPMNStep> bpmnSteps = createReceiveStep(step, plan.steps.get(i + 1));

                    if (bpmnSteps.size() == 1) {
                        BPMNStep intermediateBPMNStep = bpmnSteps.get(0);
                        intermediateBPMNStep.setTaskType(BPMNStep.TaskType.RECEIVE);
                        taskSequence.add("RECEIVE");
                        bpmnStepList.add(intermediateBPMNStep);
                        logger.info("CREATING RECEIVE; STEP 0; UNDEFINED TECHNOLOGY");
                    } else {
                        // If components has relation to multiple sources, so that multiple receives are possible
                        adjustedSteps = modifyMultiReceiveSteps(taskSequence, i, adjustedSteps, bpmnSteps);
                    }

                } else  {

                    if (plan.steps.get(i + 1).tech.equals(Technology.UNDEFINED)) {

                        // DEPLOY
                        bpmnStep.setTaskType(BPMNStep.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepList.add(bpmnStep);
                        adjustedSteps++;
                        logger.info("CREATING DEPLOY; STEP 0; NEXT STEP UNDEFINED TECHNOLOGY");

                        List<BPMNStep> bpmnSteps = createSendStep(step, plan.steps.get(i + 1));

                        if (bpmnSteps.size() == 1) {
                            BPMNStep intermediateBPMNStep = bpmnSteps.get(0);
                            // Adjusts the steps for intermediate BPMN step
                            intermediateBPMNStep.setStep(i + adjustedSteps);
                            intermediateBPMNStep.setTaskType(BPMNStep.TaskType.SEND);
                            taskSequence.add("SEND");
                            bpmnStepList.add(intermediateBPMNStep);
                            logger.info("CREATING INTERMEDIATE SEND; STEP 0; NEXT STEP UNDEFINED TECHNOLOGY");
                        } else {
                            adjustedSteps = modifyMultiSendSteps(taskSequence, i, adjustedSteps, bpmnSteps);
                        }

                    } else {

                        // DEPLOY
                        bpmnStep.setTaskType(BPMNStep.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepList.add(bpmnStep);
                        logger.info("CREATING DEPLOY; STEP 0; DEFINED TECHNOLOGY");
                    }
                }
            }

            if (i > 0 && i < plan.steps.size() - 1) {

                if (step.tech.equals(Technology.UNDEFINED)) {

                    // RECEIVE
                    List<BPMNStep> bpmnSteps = createReceiveStep(step, plan.steps.get(i + 1));

                    if (bpmnSteps.size() == 1) {
                        BPMNStep intermediateBPMNStep = bpmnSteps.get(0);
                        intermediateBPMNStep.setStep(i + adjustedSteps);
                        intermediateBPMNStep.setTaskType(BPMNStep.TaskType.RECEIVE);
                        taskSequence.add("RECEIVE");
                        bpmnStepList.add(intermediateBPMNStep);
                        logger.info("CREATING RECEIVE; STEP IN BETWEEN; UNDEFINED TECHNOLOGY");
                    } else {
                        // If components has relation to multiple sources, so that multiple receives are possible
                        adjustedSteps = modifyMultiReceiveSteps(taskSequence, i, adjustedSteps, bpmnSteps);
                    }

                } else {

                    if (plan.steps.get(i - 1).tech.equals(Technology.UNDEFINED)) {

                       if (!taskSequence.get(taskSequence.size() - 1).equals("RECEIVE")) {

                           List<BPMNStep> bpmnSteps = createReceiveStep(step, plan.steps.get(i + 1));

                           if (bpmnSteps.size() == 1) {
                               BPMNStep intermediateBPMNStep = bpmnSteps.get(0);
                               intermediateBPMNStep.setTaskType(BPMNStep.TaskType.RECEIVE);
                               taskSequence.add("RECEIVE");
                               bpmnStepList.add(intermediateBPMNStep);
                               logger.info("CREATING RECEIVE; STEP IN BETWEEN; PREVIOUS UNDEFINED TECHNOLOGY");
                           } else {
                               adjustedSteps = modifyMultiReceiveSteps(taskSequence, i, adjustedSteps, bpmnSteps);
                           }
                       }

                       // DEPLOY
                       bpmnStep.setTaskType(BPMNStep.TaskType.DEPLOY);
                       taskSequence.add("DEPLOY");
                       bpmnStepList.add(bpmnStep);
                       logger.info("CREATING DEPLOY; STEP IN BETWEEN; PREVIOUS UNDEFINED TECHNOLOGY");

                    } else if (plan.steps.get(i + 1).tech.equals(Technology.UNDEFINED)) {

                        // DEPLOY
                        bpmnStep.setTaskType(BPMNStep.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepList.add(bpmnStep);
                        adjustedSteps++;
                        logger.info("CREATING DEPLOY; STEP IN BETWEEN; NEXT STEP UNDEFINED TECHNOLOGY");

                        // SEND

                        List<BPMNStep> bpmnSteps = createSendStep(step, plan.steps.get(i + 1));

                        if (bpmnSteps.size() == 1) {
                            BPMNStep intermediateBPMNStep = bpmnSteps.get(0);
                            intermediateBPMNStep.setStep(i + adjustedSteps);
                            intermediateBPMNStep.setTaskType(BPMNStep.TaskType.SEND);
                            taskSequence.add("SEND");
                            bpmnStepList.add(intermediateBPMNStep);
                            logger.info("CREATING SEND; STEP IN BETWEEN; NEXT STEP UNDEFINED TECHNOLOGY");
                        } else {
                            adjustedSteps = modifyMultiSendSteps(taskSequence, i, adjustedSteps, bpmnSteps);
                        }

                    } else {

                        // DEPLOY
                        bpmnStep.setTaskType(BPMNStep.TaskType.DEPLOY);
                        taskSequence.add("DEPLOY");
                        bpmnStepList.add(bpmnStep);
                        logger.info("CREATING DEPLOY; STEP IN BETWEEN; DEFINED TECHNOLOGY");
                    }
                }
            }

            if (i == plan.steps.size() - 1) {

                if (step.tech.equals(Technology.UNDEFINED)) {

                    // SEND
                    if (!taskSequence.get(taskSequence.size() - 1).equals("SEND")) {

                        List<BPMNStep> bpmnSteps = createSendStep(plan.steps.get(i - 1), step);

                        if (bpmnSteps.size() == 1) {
                            BPMNStep intermediateBPMNStep = bpmnSteps.get(0);
                            intermediateBPMNStep.setTaskType(BPMNStep.TaskType.SEND);
                            taskSequence.add("SEND");
                            bpmnStepList.add(intermediateBPMNStep);
                            logger.info("CREATING SEND; LAST STEP; UNDEFINED TECHNOLOGY");
                        } else {
                            adjustedSteps = modifyMultiSendSteps(taskSequence, i, adjustedSteps, bpmnSteps);
                        }
                    }

                } else {
                    // DEPLOY
                    bpmnStep.setTaskType(BPMNStep.TaskType.DEPLOY);
                    taskSequence.add("DEPLOY");
                    bpmnStepList.add(bpmnStep);
                    logger.info("CREATING DEPLOY; LAST STEP; DEFINED TECHNOLOGY");
                }
            }

            logger.info("CURRENT TASK SEQUENCE " + taskSequence);
            i++;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            context.getFileAccess().write("bpmnExecution.plan.json", gson.toJson(bpmnStepList));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bpmnStepList;
    }

    private int modifyMultiSendSteps(List<String> taskSequence, int i, int adjustedSteps, List<BPMNStep> bpmnSteps) {
        for (BPMNStep steps : bpmnSteps) {
            steps.setStep(i + adjustedSteps);
            steps.setTaskType(BPMNStep.TaskType.SEND);
            taskSequence.add("SEND");
            adjustedSteps++;
            logger.info("CREATING MULTI INTERMEDIATE SEND; STEP 0; NEXT STEP UNDEFINED TECHNOLOGY");
        }
        multiSendStepList.add(bpmnSteps);
        return adjustedSteps;
    }

    private int modifyMultiReceiveSteps(List<String> taskSequence, int i, int adjustedSteps, List<BPMNStep> bpmnSteps) {
        for (BPMNStep steps : bpmnSteps) {
            steps.setStep(i + adjustedSteps);
            steps.setTaskType(BPMNStep.TaskType.RECEIVE);
            taskSequence.add("RECEIVE");
            adjustedSteps++;
            logger.info("CREATING MULTI RECEIVE; STEP 0; UNDEFINED TECHNOLOGY");
        }
        multiReceiveStepList.add(bpmnSteps);
        return adjustedSteps;
    }

    public void createBPMNFromExecutionPlanSequence(List<BPMNStep> bpmnSteps) {

        PluginFileAccess fileAccess = context.getFileAccess();
        List<String> owner = new ArrayList<>();
        List<BPMNStep> deployTasks = new ArrayList<>();
        List<BPMNStep> sendTasks = new ArrayList<>();
        List<BPMNStep> receiveTasks = new ArrayList<>();
        List<BPMNStep> endEvent = new ArrayList<>();

        for (BPMNStep bpmnStep : bpmnSteps) {

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

        endEvent.add(bpmnSteps.get(bpmnSteps.size() - 1));
        owner.add(context.getModel().getParticipantEndpoint(context.getModel().getOwner()));

        data.put("deployTasks", deployTasks);
        data.put("sendTasks", sendTasks);
        data.put("receiveTasks", receiveTasks);
        data.put("owner", owner);
        data.put("multiReceiveTasks", multiReceiveStepList);
        data.put("multiSendTasks", multiSendStepList);
        data.put("endEvent", endEvent);

        try {
            fileAccess.write("bpmn/MainSequence.txt", TemplateHelper.toString(cfg, "MainSequence.txt", data));
        } catch (IOException e) {
            logger.error("Failed to write MainSequence file", e);
            throw new TransformationException(e);
        }
    }

    /**
     * Merges the StartingSequence and MainEvent into one BPMN file
     */
    public void mergeFiles() throws IOException {

        File finalWorkflow = new File( context.getTargetDirectory() +  "/bpmn/Workflow.bpmn");
        finalWorkflow.delete();

        File file1 = new File(context.getTargetDirectory() + "/bpmn/StartingSequence.txt");
        File file2 = new File(context.getTargetDirectory() + "/bpmn/MainSequence.txt");

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
