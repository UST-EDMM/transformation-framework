# Terraform
Only compute on openstack is supported at the moment.
For software components every command needs to be executed as sudo to be semantically the same as ansible.
This is not possible with the Remote Exec argument scripts.

The supported openstack instance requires:
- openstack-provider infos
- priv_key_path
- key_name

it produces:
- hostname
## transformation lifecyle
`io/github/edmm/plugins/multi/terraform/TerraformAreaLifecycle.java`
Creates a terraform file to deploy a openstack compute instance.
This is similar to the terraform plugin. Only difference Openstack instead of AWS.
A difference is in the model that will output the ip address to a json file.



## executor
`io/github/edmm/plugins/multi/kubernetes/TerraformExecutor.java`
For this to work it needs provider infos. That are submitted through the `provider` artifact at the compute instance at the moment.

First the vars for openstack are made available. For this the provider.json is read and the content transformed to Terraform environment variables.
The commands `terraform init` and then `terraform apply` are executed. 
After execution a json should exist with the output infos. In this case the `hostname`
This is injected back into the model. For this step some awareness of the model is necessary.


## future
- better provider info handling
