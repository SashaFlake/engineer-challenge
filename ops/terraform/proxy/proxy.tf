# Fix for OpenStack port security (anti-spoofing):
# Worker node (10.0.1.4) cannot send packets to external IPs directly —
# the cloud virtual switch drops them because the worker MAC is only
# allowed to carry its own IP. Solution: tinyproxy on master acts as
# HTTP/HTTPS proxy; k3s-agent on worker uses it for image pulls.

terraform {
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = ">= 3.0"
    }
  }
}

# --- Master: install and configure tinyproxy ---
resource "null_resource" "tinyproxy_master" {
  connection {
    type        = "ssh"
    host        = var.master_host
    user        = var.ssh_user
    private_key = file(var.master_ssh_key)
  }

  provisioner "remote-exec" {
    inline = [
      "sudo apt-get install -y tinyproxy",
      # Idempotent: add Allow rule only if not already present
      "grep -qxF 'Allow ${var.internal_subnet}' /etc/tinyproxy/tinyproxy.conf || echo 'Allow ${var.internal_subnet}' | sudo tee -a /etc/tinyproxy/tinyproxy.conf",
      "sudo systemctl enable --now tinyproxy",
      "sudo systemctl restart tinyproxy",
    ]
  }
}

# --- Worker: configure k3s-agent to use tinyproxy ---
resource "null_resource" "k3s_agent_proxy_worker" {
  depends_on = [null_resource.tinyproxy_master]

  connection {
    type                = "ssh"
    host                = var.worker_host
    user                = var.ssh_user
    private_key         = file(var.worker_ssh_key)
    bastion_host        = var.master_host
    bastion_user        = var.ssh_user
    bastion_private_key = file(var.master_ssh_key)
  }

  provisioner "file" {
    content     = <<-EOF
      [Service]
      Environment="HTTP_PROXY=http://${var.master_internal_ip}:${var.proxy_port}"
      Environment="HTTPS_PROXY=http://${var.master_internal_ip}:${var.proxy_port}"
      Environment="NO_PROXY=${var.no_proxy}"
    EOF
    destination = "/tmp/http-proxy.conf"
  }

  provisioner "remote-exec" {
    inline = [
      "sudo mkdir -p /etc/systemd/system/k3s-agent.service.d",
      "sudo mv /tmp/http-proxy.conf /etc/systemd/system/k3s-agent.service.d/http-proxy.conf",
      "sudo systemctl daemon-reload",
      "sudo systemctl restart k3s-agent",
    ]
  }
}
