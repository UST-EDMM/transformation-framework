## Kubernetes Visitor



### transformation lifecycle
`edmm-core/src/main/java/io/github/edmm/plugins/multi/kubernetes/KubernetesAreaLifecycle.java`
This works similar to the kubernetes plugin.
The difference is in the addition of runtime attributes. These are then added es envVar parameters that reference a configmap.
All components are collected in the stack with hosted_on are collected. After that it's the same procedure as with the kubernetes plugin.
Furthermore chmod+x was added to the Dockerfile scripts to get rid of problems with permission denied.
     
In the prepare phase the stacks that belong to the group are determined.
Then all properties and components are added to these stacks. At the end fitting deployments and services are created.
The configmap can only be crreated during execution time, becase some values may not be known.

    
2. later option(?):
  - secrets could be added to secretmap


### executor
`edmm-core/src/main/java/io/github/edmm/plugins/multi/kubernetes/KubernetesOrchestratorVisitor.java`


General info: All yamls are deployed with the kubernetes-client because fabric8 didn't work for me.

The idea is to only deploy when the top component of a stack is visited. Then the following steps are executed
>create configmap named \<componentname>-config.yaml, then deploy configmap

For the configmap all environment variables that are recognized to be from a connected component or only known during runtime
need to be known now. They are collected and added to this map.

>deploy deployment

Because we know that a `<componentname>-deployment.yaml` exists through the transformation step. We deploy this next.
All runtime references to the configmap are fulfilled now.

> deploy service

At last the service is deployed to expose ports.

> wait for it to come online

At the moment this is only implemented with waiting 20 seconds. Could be improved with polling the status.

> read runtime vars

After everything is deployed interesting variables can be read out. At the moment this is the clusterIP that is 
simulating some future external loadbalancer-hostname or something similar. This is injected in the model as `hostname

### special cases
  what if 2 components are deployed in same cluster -> check and use service name?
  
## future work
- improve check model step
At the moment something in the middle of a stack could be another deployment tech. Doesn't work with kubernetes.

