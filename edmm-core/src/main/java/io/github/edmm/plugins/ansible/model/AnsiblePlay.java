package io.github.edmm.plugins.ansible.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnsiblePlay {
    private String name;
    private String hosts;
    private boolean become;
    private String becomeUser;
    private Map<String, String> vars;
    private List<String> runtimeVars;
    private List<AnsibleTask> tasks;
    private List<AnsibleFile> files;
}
