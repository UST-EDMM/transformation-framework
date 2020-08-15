package io.github.edmm.core.transformation;

import java.io.File;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.JsonHelper;
import io.github.edmm.core.plugin.PluginFileAccess;
import io.github.edmm.model.DeploymentModel;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.parameters.UserInput;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.multi.model_extensions.groupingGraph.Group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.Graph;
import org.springframework.lang.Nullable;

@Data
@NoArgsConstructor
public final class TransformationContext {

    public static final String CONTEXT_FILENAME = "edmm.json";

    private String id;
    private File input;
    @JsonIgnore
    private String inputAsString;
    private DeploymentModel model;
    private DeploymentTechnology deploymentTechnology;
    private File sourceDirectory;
    private File targetDirectory;
    private Timestamp timestamp;
    private State state = State.READY;

    @Getter
    private Group group;

    private Set<UserInput> userInputs;
    private Map<String, Object> values;

    public TransformationContext(@NonNull DeploymentModel model, @NonNull DeploymentTechnology deploymentTechnology) {
        this(UUID.randomUUID().toString(), model, deploymentTechnology, null, null, null);
    }

    public TransformationContext(@NonNull DeploymentModel model, @NonNull DeploymentTechnology deploymentTechnology,
                                 @Nullable File sourceDirectory, @Nullable File targetDirectory) {
        this(UUID.randomUUID().toString(), model, deploymentTechnology, sourceDirectory, targetDirectory, null);
    }

    public TransformationContext(@NonNull File input, @NonNull DeploymentTechnology deploymentTechnology,
                                 @Nullable File sourceDirectory, @Nullable File targetDirectory) {
        this(UUID.randomUUID().toString(), null, deploymentTechnology, sourceDirectory, targetDirectory, null);
        this.input = input;
    }

    public TransformationContext(@NonNull String inputAsString, @NonNull DeploymentTechnology deploymentTechnology,
                                 @Nullable File sourceDirectory, @Nullable File targetDirectory) {
        this(UUID.randomUUID().toString(), null, deploymentTechnology, sourceDirectory, targetDirectory, null);
        this.inputAsString = inputAsString;
    }

    public TransformationContext(String id, DeploymentModel model, DeploymentTechnology deploymentTechnology,
                                 @Nullable File sourceDirectory, @Nullable File targetDirectory, @Nullable Group group) {
        this.id = id;
        this.model = model;
        this.deploymentTechnology = deploymentTechnology;
        this.sourceDirectory = sourceDirectory;
        this.targetDirectory = targetDirectory;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.group = group;
    }

    public static TransformationContext of(File directory) {

        File file = new File(directory, CONTEXT_FILENAME);
        System.out.println(file.toString());
        if (!file.isFile() || !directory.canRead()) {
            throw new IllegalStateException(String.format("Cannot read context from file '%s'", file));
        }
        return JsonHelper.readValue(file, TransformationContext.class);
    }

    @JsonIgnore
    public DeploymentModel getModel() {
        if (model == null) {
            if (input == null && StringUtils.isBlank(inputAsString)) {
                throw new IllegalStateException("Context requires either an input as file or as string");
            }
            if (input != null) {
                model = DeploymentModel.of(input);
            } else {
                model = DeploymentModel.of(inputAsString);
            }
        }
        return model;
    }

    @JsonIgnore
    public Graph<RootComponent, RootRelation> getTopologyGraph() {
        return getModel().getTopology();
    }

    @JsonIgnore
    public PluginFileAccess getFileAccess() {
        return new PluginFileAccess(sourceDirectory, targetDirectory);
    }

    public void putValue(String name, Object value) {
        if (values == null) {
            values = new HashMap<>();
        }
        values.put(name, value);
    }

    public Object getValue(String name) {
        if (values == null) {
            return null;
        }
        return values.get(name);
    }

    public void setErrorState(Exception e) {
        this.state = State.ERROR;
        this.putValue("exception", e);
    }

    /**
     * This function will be called by Winery to re-throw the exception triggered during the
     * transformation and inform the user about that.
     */
    public void throwExceptionIfErrorState() throws Exception {
        Exception e = (Exception) this.getValue("exception");
        if (this.state == State.ERROR && e != null) {
            throw e;
        }
    }

    public enum State {
        READY,
        TRANSFORMING,
        DONE,
        ERROR
    }
}
