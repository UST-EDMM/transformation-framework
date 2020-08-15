package io.github.edmm.plugins.multi.terraform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.github.edmm.core.BashScript;
import io.github.edmm.core.TemplateHelper;
import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.plugin.PluginFileAccess;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.model.Artifact;
import io.github.edmm.model.Operation;
import io.github.edmm.model.component.Compute;
import io.github.edmm.model.component.Database;
import io.github.edmm.model.component.Dbms;
import io.github.edmm.model.component.MysqlDatabase;
import io.github.edmm.model.component.MysqlDbms;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.component.Tomcat;
import io.github.edmm.model.component.WebApplication;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.model.visitor.ComponentVisitor;
import io.github.edmm.model.visitor.RelationVisitor;
import io.github.edmm.plugins.multi.MultiPlugin;
import io.github.edmm.plugins.terraform.model.FileProvisioner;
import io.github.edmm.plugins.terraform.model.Openstack;
import io.github.edmm.plugins.terraform.model.RemoteExecProvisioner;

import freemarker.template.Configuration;
import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraformVisitor implements ComponentVisitor, RelationVisitor {

    private static final Logger logger = LoggerFactory.getLogger(TerraformVisitor.class);
    protected final TransformationContext context;
    protected final Configuration cfg = TemplateHelper.forClasspath(MultiPlugin.class, "/plugins/multi");
    protected final Graph<RootComponent, RootRelation> graph;
    private final Map<Compute, Openstack.Instance> computeInstances = new HashMap<>();
    private final Map<Compute, Openstack.Instance> softwareStacks = new HashMap<>();

    public TerraformVisitor(TransformationContext context) {
        this.context = context;
        this.graph = context.getTopologyGraph();
    }

    private void copyFiles(RootComponent comp) {
        PluginFileAccess fileAccess = context.getFileAccess();
        for (Artifact artifact : comp.getArtifacts()) {
            try {
                // get basename
                String basename = FilenameUtils.getName(artifact.getValue());
                String newPath = "./files/" + comp.getNormalizedName() + "/" + basename;
                logger.info("old filepath {}", artifact.getValue());
                fileAccess.copy(artifact.getValue(), newPath);
            } catch (IOException e) {
                logger.warn("Failedss to copy file '{}'", artifact.getValue());
            }

        }
        List<String> operations = collectOperations(comp);

        for (String artifact : operations) {
            try {
                String basename = FilenameUtils.getName(artifact);
                String newPath = "./files/" + comp.getNormalizedName() + "/" + basename;
                fileAccess.copy(artifact, newPath);
            } catch (IOException e) {
                logger.warn("Failed to copy file '{}'", artifact);
            }

        }

    }

    @SneakyThrows
    @Override
    public void visit(Compute component) {

        copyFiles(component);

        String abolutePrivkeyPath;
        Path privKeyValue = Paths.get(component.getPrivateKeyPath().get());
        if (privKeyValue.isAbsolute()) {
            abolutePrivkeyPath = component.getPrivateKeyPath().get();
        } else {
            abolutePrivkeyPath = new File(context.getFileAccess().getSourceDirectory(),
                component.getPrivateKeyPath().get()).getAbsolutePath();
        }

        Openstack.Instance openstackInstance = Openstack.Instance.builder().name(component.getNormalizedName())
            .keyName(component.getKeyName().get()).privKeyFile(abolutePrivkeyPath).build();
        List<String> operations = collectOperations(component);
        openstackInstance.addRemoteExecProvisioner(new RemoteExecProvisioner(operations));
        openstackInstance.addFileProvisioner(new FileProvisioner("./env.sh", "/opt/env.sh"));
        computeInstances.put(component, openstackInstance);

        // add properties that are created by this componentent to announce for the
        // others
        component.addProperty("hostname", null);
        component.setTransformed(true);
    }

    private void collectFileProvisioners(RootComponent component) {
        Optional<Compute> optionalCompute = TopologyGraphHelper.resolveHostingComputeComponent(graph, component);
        if (optionalCompute.isPresent()) {
            Compute hostingCompute = optionalCompute.get();
            Openstack.Instance awsInstance = computeInstances.get(hostingCompute);
            for (Artifact artifact : component.getArtifacts()) {
                String destination = "/opt/" + component.getNormalizedName();
                logger.info("file provisioner" + artifact.getValue());
                awsInstance.addFileProvisioner(new FileProvisioner(artifact.getValue(), destination));
            }
        }
    }

    private void collectRemoteExecProvisioners(RootComponent component) {
        Optional<Compute> optionalCompute = TopologyGraphHelper.resolveHostingComputeComponent(graph, component);
        if (optionalCompute.isPresent()) {
            Compute hostingCompute = optionalCompute.get();
            Openstack.Instance awsInstance = computeInstances.get(hostingCompute);
            List<String> operations = collectOperations(component);
            awsInstance.addRemoteExecProvisioner(new RemoteExecProvisioner(operations));
        }
    }

    private void collectEnvVars(RootComponent component) {
        Optional<Compute> optionalCompute = TopologyGraphHelper.resolveHostingComputeComponent(graph, component);
        if (optionalCompute.isPresent()) {
            Compute hostingCompute = optionalCompute.get();
            Openstack.Instance openstackInstance = computeInstances.get(hostingCompute);
            String[] blacklist = {"key_name", "public_key"};
            component.getProperties().values().stream().filter(p -> !Arrays.asList(blacklist).contains(p.getName()))
                .forEach(p -> {
                    String name = (component.getNormalizedName() + "_" + p.getNormalizedName()).toUpperCase();
                    openstackInstance.addEnvVar(name, p.getValue());
                });
        }
    }

    @Override
    public void visit(RootComponent component) {
        collectFileProvisioners(component);
        collectRemoteExecProvisioners(component);
        collectEnvVars(component);
        component.setTransformed(true);
    }

    private List<String> collectOperations(RootComponent component) {
        List<String> operations = new ArrayList<>();
        Consumer<Operation> artifactsConsumer = op -> op.getArtifacts()
            .forEach(artifact -> operations.add(artifact.getValue()));
        component.getStandardLifecycle().getCreate().ifPresent(artifactsConsumer);
        component.getStandardLifecycle().getConfigure().ifPresent(artifactsConsumer);
        component.getStandardLifecycle().getStart().ifPresent(artifactsConsumer);
        return operations;
    }

    public void populate() {
        PluginFileAccess fileAccess = context.getFileAccess();
        Map<String, Object> data = new HashMap<>();

        data.put("instances", computeInstances);
        data.put("software_stacks", softwareStacks);
        try {
            fileAccess.write("compute.tf", TemplateHelper.toString(cfg, "compute.tf", data));
            // fileAccess.write("software.tf", TemplateHelper.toString(cfg, "software.tf",
            // data));
        } catch (IOException e) {
            logger.error("Failed to write Terraform file", e);
            throw new TransformationException(e);
        }

        for (Openstack.Instance openstackInstance : computeInstances.values()) {

            // Write env.sh script entries
            BashScript envScript = new BashScript(fileAccess, "env.sh");
            openstackInstance.getEnvVars().forEach((name, value) -> envScript.append("export " + name + "=" + value));
            // Copy artifacts to target directory
            // env is already there
            for (FileProvisioner provisioner : openstackInstance.getFileProvisioners()) {
                try {
                    fileAccess.copy(provisioner.getSource(), provisioner.getSource());
                } catch (IOException e) {
                    logger.warn("Failed to copy file '{}'", provisioner.getSource());
                }
            }
            // Copy operations to target directory
            List<String> operations = openstackInstance.getRemoteExecProvisioners().stream()
                .map(RemoteExecProvisioner::getScripts).flatMap(Collection::stream).collect(Collectors.toList());
            for (String op : operations) {
                try {
                    fileAccess.copy(op, op);
                } catch (IOException e) {
                    logger.warn("Failed to copy file '{}'", op);
                }
            }

        }
    }

    @Override
    public void visit(Tomcat component) {
        visit((RootComponent) component);
    }

    @Override
    public void visit(MysqlDbms component) {
        visit((RootComponent) component);
    }

    @Override
    public void visit(Database component) {
        visit((RootComponent) component);
    }

    @Override
    public void visit(Dbms component) {
        visit((RootComponent) component);
    }

    @Override
    public void visit(MysqlDatabase component) {
        visit((RootComponent) component);
    }

    @Override
    public void visit(WebApplication component) {
        visit((RootComponent) component);
    }

}
