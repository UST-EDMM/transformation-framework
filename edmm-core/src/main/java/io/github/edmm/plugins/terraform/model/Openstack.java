package io.github.edmm.plugins.terraform.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

public class Openstack {

    @Data
    public static class Instance {

        private String name;
        private String instanceType;
        private String privKeyFile;
        private String keyName;
        private List<String> ingressPorts = new ArrayList<>();
        private List<FileProvisioner> fileProvisioners = new ArrayList<>();
        private List<RemoteExecProvisioner> remoteExecProvisioners = new ArrayList<>();
        private List<String> dependencies = new ArrayList<>();
        private Map<String, String> envVars = new HashMap<>();
        private List<String> runtimeVars = new ArrayList<>();

        @Builder
        public Instance(String name, String instanceType, String privKeyFile, String keyName) {
            this.name = name;
            this.instanceType = instanceType;
            this.privKeyFile = privKeyFile;
            this.keyName = keyName;
        }

        public void addIngressPort(String port) {
            ingressPorts.add(port);
        }

        public void addFileProvisioner(FileProvisioner provisioner) {
            fileProvisioners.add(provisioner);
        }

        public void addRemoteExecProvisioner(RemoteExecProvisioner provisioner) {
            remoteExecProvisioners.add(provisioner);
        }

        public void addDependency(String dependency) {
            dependencies.add(dependency);
        }

        public void addEnvVar(String name, String value) {
            envVars.put(name, value);
        }

        public void addRuntimeVar(String name) {
            runtimeVars.add(name);
        }
    }


    // the compute instance is deployed with another technology
    // just connect and execute this stuff
    @Data
    public static class SoftwareStack {
        private String privKeyFile;
        private String keyName;
        private List<String> ingressPorts = new ArrayList<>();
        private List<FileProvisioner> fileProvisioners = new ArrayList<>();
        private List<RemoteExecProvisioner> remoteExecProvisioners = new ArrayList<>();
        private List<String> dependencies = new ArrayList<>();
        private Map<String, String> envVars = new HashMap<>();
        private List<String> runtimeVars = new ArrayList<>();
    }
}
