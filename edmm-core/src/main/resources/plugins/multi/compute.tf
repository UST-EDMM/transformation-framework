variable "region" {
  default = "eu-west-1"
}
variable "ssh_user" {
  default = "ubuntu"
}
variable "os_auth_url" {
}
variable "os_project_id" {
}
variable "os_user_domain_name" {
}
variable "os_username" {
}
variable "os_password" {
}
variable "os_region_name" {
}


provider "openstack" {
  user_name = var.os_username
  password = var.os_password
  auth_url = var.os_auth_url
  region = var.os_region_name
  user_domain_name = var.os_user_domain_name
  tenant_id = var.os_project_id
}

<#if instances??>
<#list instances as k, oci2>
data "tls_public_key" "priv_key" {
  private_key_pem = file("${oci2.privKeyFile}")
}


resource "openstack_compute_instance_v2" "${oci2.name}" {
name = "${oci2.name}"
image_name = "Ubuntu 18.04"
flavor_name = "m1.small"
key_pair = "${oci2.keyName}"
security_groups = [
"default"]

metadata = {
this = "that"
}

network {
name = "public-belwue"
}
/*
<#list oci2.fileProvisioners as provisioner>
provisioner "file" {
source = "${provisioner.source}"
destination = "${provisioner.destination}"
connection {
type = "ssh"
user = var.ssh_user
host=self.access_ip_v4
private_key=file("${oci2.privKeyFile}")
}
}
</#list>
*/
<#list oci2.remoteExecProvisioners as provisioner>
<#if provisioner.scripts?size != 0>
provisioner "remote-exec" {
scripts = [
<#list provisioner.scripts as script>
"${script}"<#sep>,</#sep>
</#list>
]
connection {
type = "ssh"
user = var.ssh_user
host=self.access_ip_v4
private_key=file("${oci2.privKeyFile}")

}
}
<#else>
</#if>
</#list>

}

resource "local_file" "compute_${oci2.name}" {
  content = jsonencode( {
      "hostname" = openstack_compute_instance_v2.${oci2.name}.access_ip_v4
  })
  filename = "${oci2.name}_computed_properties.json"
}
output "compute_${oci2.name}_address" {
  value = openstack_compute_instance_v2.${oci2.name}.access_ip_v4
}
</#list>
</#if>
