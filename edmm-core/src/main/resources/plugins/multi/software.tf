variable "compute_${compute}_address" {

}

variable "compute_${compute}_private_key_file" {

}


resource "null_resource" "cluster" {


  connection {
    user = "ubuntu"
    host = var.compute_${compute}_address
private_key = file(var.compute_${compute}_private_key_file)
}

<#list files as file>
provisioner "file" {
source = "${file.source}"
destination = "${file.destination}"
}
</#list>

<#if operations?size != 0>
provisioner "remote-exec" {
scripts = [
<#list operations.scripts as script>
"${script}"<#sep>,</#sep>
</#list>
]
</#if>
}
}
