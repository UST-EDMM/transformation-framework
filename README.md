# EDMM Transformation and Deployment Framework

> Transformation framework for the Essential Deployment Metamodel.

## Example

```shell
edmm transform multi icsoc-demo/deployment-full.yaml
```

The generated Kubernetes resource files are located relative to the `edmm_model.yml` file inside a `multi` directory.
This will create a stack for the petclinic demo application including the underlying tomcat. 
Because kubernetes is chosen as the technology a configmap-,service- and deployment-file is added.
For the second "stack" first an openstack compute instance is deployed. Then the software is installed with ansible.
For this to work the openstack-provider infos have to be provided.

### infos
- compute instance deployed with terraform needs a provider.json
- localhost:32000 is hardcoded as image repo
- default kubernetes context is used

## Usage
The transformation can be started by using the `transform` command of the `edmm` tool: `edmm transform multi <input>`.
For a deployment with multiple technologies the transform target is always "multi". Which technology is used exactly can be specified in the deployment model.
The generated technology-specific deployment models will be stored relative to the YAML input file. Every orchestration step has its own folder.
After the files are created the deployment can be started when the command line shows 
`Enter y to continue with orchestration`. After pressing y it begins.

## What happens?

At first the multi plugin is called(should be lifted up maybe?). 
Here, two special phases are `transform` and `execution`.


The phases are called from `edmm-core/src/main/java/io/github/edmm/plugins/multi/MultiLifecycle.java`.
Most of the implementation is in `edmm-core/src/main/java/io/github/edmm/plugins/multi/`.


### Transform
`edmm-core/src/main/java/io/github/edmm/plugins/multi/MultiLifecycle.java`(transform)
The first step is to select components that use the same technology and no cycle exists when they are merged.
The result is a sorted list of groups.
As a next step a execution plan/worflow is created. Here the sorted groups with their components are written down `execution.plan.json` with a defined order.

As a next step for every group a lifecycle with a transformation context is created.
The context contains on one hand the whole deployment model and on the other handThe group that this call is responsible for
Additionally, the target directory where the technology specific models will be placed.
The access to the whole deployment is necessary, through relations other properties may be needed here.
Then all lifecycle phases are called. (modelcheck,prepare,transform,cleanup...).
This could be made cleaner?:


Valid for all techs:
- Environment variables from hosted_on are used transitively and keep their name
- Env vars from other connections are not used automatically and need to be called explicitly syntax see model
    - this can be changed with a few lines in 
This can be changed in `TopologyGraphHelper.findAllProperties`. 


[Kubernetes](kubernetes.md)
[Ansible](ansible.md)
[Terraform](terraform.md)



### Execution
`edmm-core/src/main/java/io/github/edmm/plugins/multi/MultiLifecycle.java`(execute)

The orchestration step reads the plan `execution.plan.json`. Here the groups and their technology are defined.
These are read and then the corresponing model is executed. 

The execution works as follows:
Loop over all groups in order
1. First collect the needed runtime properties 
2. call technology specific plugin with runtime properties as info
3. write back properties to model(done in plugins already)

After every step the `state.yaml` is updated to reflect the new infos.

[Kubernetes](kubernetes.md)
[Ansible](ansible.md)
[Terraform](terraform.md)

### remarks
some of the classes are copy pasted and changed for the needs of multi deployment without breaking everything else.
For example the Terraformvisitor

### possible future work
- decpouple execution from transformation
- new instance model after transformation
- improve plugin usage

    


## Build the project

We use Maven as our build tool:

```shell
./mvnw clean package
```
