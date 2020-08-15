package io.github.edmm.plugins.ansible.model;

import lombok.Value;

@Value
public class AnsibleHost {
    private String name;
    private String privKeyFile;
}
