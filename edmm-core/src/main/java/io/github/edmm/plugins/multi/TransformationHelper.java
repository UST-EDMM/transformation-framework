package io.github.edmm.plugins.multi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.model.Property;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.relation.RootRelation;

import lombok.var;
import org.apache.commons.io.FilenameUtils;
import org.jgrapht.Graph;
import org.jgrapht.alg.util.Pair;

public class TransformationHelper {

    static boolean matchesBlacklist(Map.Entry<String, Property> prop) {
        String[] blacklist = {"*key_name*", "*public_key*"};
        for (var blacklistVal : blacklist) {
            if (FilenameUtils.wildcardMatch(prop.getKey(), blacklistVal)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> collectEnvVars(Graph<RootComponent, RootRelation> graph,
                                                     RootComponent component) {
        Map<String, String> envVars = new HashMap<>();
        var allProps = TopologyGraphHelper.resolveComponentStackProperties(graph, component);

        for (var prop : allProps.entrySet()) {
            if (matchesBlacklist(prop)) {
                continue;
            }

            if (prop.getValue().isComputed() || prop.getValue().getValue() == null
                || prop.getValue().getValue().startsWith("$")) {
                continue;
            }
            envVars.put(prop.getKey().toUpperCase(), prop.getValue().getValue());
        }

        return envVars;
    }

    public static List<String> collectRuntimeEnvVars(Graph<RootComponent, RootRelation> graph,
                                                     RootComponent component) {
        // runtime vars
        List<String> envVars = new ArrayList<>();
        var allProps = TopologyGraphHelper.resolveComponentStackProperties(graph, component);

        for (var prop : allProps.entrySet()) {

            if (matchesBlacklist(prop)) {
                continue;
            }
            if (prop.getValue().isComputed() || prop.getValue().getValue() == null
                || prop.getValue().getValue().startsWith("$")) {
                envVars.add(prop.getKey().toUpperCase());
            }
        }
        return envVars;
    }

    public static Pair<List<String>, List<String>> collectRuntimeEnvInputOutput(
        Graph<RootComponent, RootRelation> graph, RootComponent component) {
        // runtime vars
        List<String> inputVars = new ArrayList<>();
        List<String> outputVars = new ArrayList<>();
        var allProps = TopologyGraphHelper.resolveComponentStackProperties(graph, component);

        for (var prop : allProps.entrySet()) {

            if (matchesBlacklist(prop)) {
                continue;
            }
            // are not set already and originate by this component
            else if (prop.getValue().isComputed() && component.getProperty(prop.getKey()).isPresent()) {
                outputVars.add(prop.getKey().toUpperCase());
            }
            // are referenced from another component
            else if (prop.getValue().isComputed() || prop.getValue().getValue() == null
                || prop.getValue().getValue().startsWith("$")) {
                inputVars.add(prop.getKey().toUpperCase());
            }
        }
        return new Pair(inputVars, outputVars);
    }
}
