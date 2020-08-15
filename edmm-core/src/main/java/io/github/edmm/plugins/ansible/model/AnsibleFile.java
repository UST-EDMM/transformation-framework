package io.github.edmm.plugins.ansible.model;

import lombok.Value;

@Value
public class AnsibleFile {
    private String src;
    private String target;
}


