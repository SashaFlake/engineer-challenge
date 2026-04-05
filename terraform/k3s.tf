# ─── Скачиваем k3s бинарник локально и копируем на VM ───────────────────────────
locals {
  k3s_binary  = "/tmp/k3s"
  k3s_version = "v1.32.3+k3s1"
  k3s_url     = "https://github.com/k3s-io/k3s/releases/download/${local.k3s_version}/k3s"
}

# Скачиваем k3s локально перед заливкой
resource "null_resource" "download_k3s" {
  provisioner "local-exec" {
    command = <<-EOT
      if [ ! -f ${local.k3s_binary} ]; then
        echo "Downloading k3s ${local.k3s_version}..."
        curl -Lo ${local.k3s_binary} ${local.k3s_url}
        chmod +x ${local.k3s_binary}
      else
        echo "k3s binary already exists at ${local.k3s_binary}"
      fi
    EOT
  }
}

# ─── k3s: мастер-нода ─────────────────────────────────────────────────────────────────────
resource "null_resource" "k3s_master" {
  depends_on = [null_resource.download_k3s]

  connection {
    type        = "ssh"
    host        = var.master_ip
    user        = var.ssh_user
    private_key = file(var.ssh_private_key_path)
  }

  # Заливаем бинарник k3s на мастер
  provisioner "file" {
    source      = local.k3s_binary
    destination = "/tmp/k3s"
  }

  provisioner "remote-exec" {
    inline = [
      "sudo install -o root -g root -m 0755 /tmp/k3s /usr/local/bin/k3s",

      # Создаём systemd unit для k3s server
      "sudo tee /etc/systemd/system/k3s.service > /dev/null <<'UNIT'\n[Unit]\nDescription=Lightweight Kubernetes\nDocumentation=https://k3s.io\nAfter=network-online.target\n\n[Service]\nType=notify\nEnvironmentFile=-/etc/default/k3s\nExecStartPre=/bin/sh -xc '! /usr/bin/systemctl is-enabled --quiet nm-cloud-setup.service'\nExecStart=/usr/local/bin/k3s server --disable traefik --disable servicelb --bind-address=0.0.0.0 --tls-san ${var.master_ip}\nKillMode=process\nDelegate=yes\nLimitNOFILE=1048576\nLimitNPROC=infinity\nLimitCORE=infinity\nTasksMax=infinity\nTimeoutStartSec=0\nRestart=always\nRestartSec=5s\n\n[Install]\nWantedBy=multi-user.target\nUNIT",

      "sudo systemctl daemon-reload",
      "sudo systemctl enable k3s",
      "sudo systemctl start k3s",

      # Ждём пока k3s не запустится
      "until sudo k3s kubectl get nodes 2>/dev/null; do echo 'waiting for k3s...'; sleep 5; done",

      "sudo chmod 644 /etc/rancher/k3s/k3s.yaml",

      # ─── NAT: воркер выходит в интернет через мастер ─────────────────────────
      "sudo sysctl -w net.ipv4.ip_forward=1",
      "echo 'net.ipv4.ip_forward=1' | sudo tee /etc/sysctl.d/99-ip-forward.conf",
      "IFACE=$(ip route | awk '/^default/ {print $5; exit}')",
      "sudo iptables -t nat -C POSTROUTING -s 10.0.1.0/24 -o $IFACE -j MASQUERADE 2>/dev/null || sudo iptables -t nat -A POSTROUTING -s 10.0.1.0/24 -o $IFACE -j MASQUERADE",
      "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent",
      "sudo netfilter-persistent save",

      # ─── tinyproxy: HTTP/HTTPS proxy для воркера ──────────────────────────────
      # OpenStack port security (anti-spoofing) блокирует пакеты от воркера
      # с dst вне 10.0.0.0/8 на уровне виртуального свитча.
      # MASQUERADE на мастере не помогает — пакеты дропаются ДО того как
      # достигают мастера. Решение: воркер использует мастер как HTTP proxy.
      "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y tinyproxy",
      "grep -qxF 'Allow 10.0.1.0/24' /etc/tinyproxy/tinyproxy.conf || echo 'Allow 10.0.1.0/24' | sudo tee -a /etc/tinyproxy/tinyproxy.conf",
      "sudo systemctl enable --now tinyproxy",
      "sudo systemctl restart tinyproxy",
    ]
  }
}

# Получаем kubeconfig и node-token с мастера
resource "null_resource" "fetch_kubeconfig" {
  depends_on = [null_resource.k3s_master]

  provisioner "local-exec" {
    command = <<-EOT
      ssh -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} \
        ${var.ssh_user}@${var.master_ip} \
        'sudo cat /etc/rancher/k3s/k3s.yaml' \
        | sed 's/127.0.0.1/${var.master_ip}/g' \
        > ${path.module}/kubeconfig.yaml

      ssh -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} \
        ${var.ssh_user}@${var.master_ip} \
        'sudo cat /var/lib/rancher/k3s/server/node-token' \
        > ${path.module}/node-token.txt
    EOT
  }
}

# Примечание: local_file.kubeconfig намеренно убран.
# kubeconfig.yaml пишется напрямую через local-exec в fetch_kubeconfig.
# Ресурс local_file перезаписывал бы его пустой строкой после записи.

# ─── SSH ключ на мастере для доступа к воркеру ────────────────────────────────────
resource "null_resource" "copy_key_to_master" {
  depends_on = [null_resource.k3s_master]

  provisioner "local-exec" {
    command = <<-EOT
      scp -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} \
        ${var.ssh_private_key_path} \
        ${var.ssh_user}@${var.master_ip}:/home/${var.ssh_user}/.ssh/id_rsa_worker
      ssh -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} \
        ${var.ssh_user}@${var.master_ip} \
        'chmod 600 /home/${var.ssh_user}/.ssh/id_rsa_worker'
    EOT
  }
}

# ─── node-token на воркер через мастер ───────────────────────────────────────────────────
resource "null_resource" "copy_token_to_worker" {
  depends_on = [null_resource.fetch_kubeconfig, null_resource.copy_key_to_master]

  provisioner "local-exec" {
    command = <<-EOT
      scp -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} \
        ${path.module}/node-token.txt \
        ${var.ssh_user}@${var.master_ip}:/tmp/node-token

      ssh -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} \
        ${var.ssh_user}@${var.master_ip} \
        "scp -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa_worker /tmp/node-token ${var.ssh_user}@${var.worker_ip}:/tmp/node-token"
    EOT
  }
}

# ─── Заливаем k3s на воркер через ProxyJump ──────────────────────────────────────────
resource "null_resource" "upload_k3s_to_worker" {
  depends_on = [null_resource.copy_token_to_worker]

  provisioner "local-exec" {
    command = <<-EOT
      scp -o StrictHostKeyChecking=no \
        -o ProxyCommand="ssh -W %h:%p -o StrictHostKeyChecking=no -i ${var.ssh_private_key_path} ${var.ssh_user}@${var.master_ip}" \
        -i ${var.ssh_private_key_path} \
        ${local.k3s_binary} \
        ${var.ssh_user}@${var.worker_ip}:/tmp/k3s
    EOT
  }
}

# ec-2 не имеет публичного IP — подключаемся через мастер (ec-1) как бастион
resource "null_resource" "k3s_worker_join" {
  depends_on = [null_resource.upload_k3s_to_worker]

  connection {
    type        = "ssh"
    host        = var.worker_ip
    user        = var.ssh_user
    private_key = file(var.ssh_private_key_path)

    bastion_host        = var.master_ip
    bastion_user        = var.ssh_user
    bastion_private_key = file(var.ssh_private_key_path)
  }

  provisioner "remote-exec" {
    inline = [
      "sudo install -o root -g root -m 0755 /tmp/k3s /usr/local/bin/k3s",

      # systemd unit для k3s agent
      "sudo tee /etc/systemd/system/k3s-agent.service > /dev/null <<'UNIT'\n[Unit]\nDescription=Lightweight Kubernetes Node\nDocumentation=https://k3s.io\nAfter=network-online.target\n\n[Service]\nType=exec\nEnvironmentFile=-/etc/default/k3s\nExecStart=/usr/local/bin/k3s agent\nKillMode=process\nDelegate=yes\nLimitNOFILE=1048576\nLimitNPROC=infinity\nLimitCORE=infinity\nTasksMax=infinity\nTimeoutStartSec=0\nRestart=always\nRestartSec=5s\n\n[Install]\nWantedBy=multi-user.target\nUNIT",

      "echo 'K3S_URL=https://${var.master_private_ip}:6443' | sudo tee /etc/default/k3s",
      "echo 'K3S_TOKEN='$(cat /tmp/node-token) | sudo tee -a /etc/default/k3s",

      # ─── Маршрутизация: внешний трафик через мастер ───────────────────────────────────
      # Облачной шлюз 10.0.1.1 блокирует исходящий интернет с воркера.
      # Два /1 маршрута перекрывают default route и направляют
      # весь внешний трафик через мастер MASQUERADE NAT.
      "sudo ip route replace 0.0.0.0/1 via ${var.master_private_ip} 2>/dev/null || sudo ip route add 0.0.0.0/1 via ${var.master_private_ip}",
      "sudo ip route replace 128.0.0.0/1 via ${var.master_private_ip} 2>/dev/null || sudo ip route add 128.0.0.0/1 via ${var.master_private_ip}",

      # Персистентность маршрутов через /etc/networkd-dispatcher (Ubuntu 18.04+)
      # Выполняется при поднятии интерфейса (routable state)
      "sudo mkdir -p /etc/networkd-dispatcher/routable.d",
      "sudo tee /etc/networkd-dispatcher/routable.d/50-nat-routes > /dev/null <<'SCRIPT'\n#!/bin/sh\nip route replace 0.0.0.0/1 via ${var.master_private_ip} 2>/dev/null || ip route add 0.0.0.0/1 via ${var.master_private_ip}\nip route replace 128.0.0.0/1 via ${var.master_private_ip} 2>/dev/null || ip route add 128.0.0.0/1 via ${var.master_private_ip}\nSCRIPT",
      "sudo chmod +x /etc/networkd-dispatcher/routable.d/50-nat-routes",

      # ─── DNS ───────────────────────────────────────────────────────────────────────────────
      "sudo mkdir -p /etc/systemd/resolved.conf.d",
      "printf '[Resolve]\\nDNS=8.8.8.8 8.8.4.4\\nFallbackDNS=1.1.1.1\\n' | sudo tee /etc/systemd/resolved.conf.d/99-upstream.conf",
      "sudo systemctl restart systemd-resolved 2>/dev/null || true",
      "sudo chattr -i /etc/resolv.conf 2>/dev/null || true",
      "printf 'nameserver 8.8.8.8\\nnameserver 8.8.4.4\\n' | sudo tee /etc/resolv.conf",

      # ─── HTTP proxy для k3s-agent (containerd) ────────────────────────────────
      # OpenStack port security блокирует прямой выход воркера в интернет.
      # k3s-agent наследует переменные окружения в свой встроенный containerd.
      "sudo mkdir -p /etc/systemd/system/k3s-agent.service.d",
      "sudo tee /etc/systemd/system/k3s-agent.service.d/http-proxy.conf > /dev/null <<'EOF'\n[Service]\nEnvironment=\"HTTP_PROXY=http://${var.master_private_ip}:8888\"\nEnvironment=\"HTTPS_PROXY=http://${var.master_private_ip}:8888\"\nEnvironment=\"NO_PROXY=10.0.0.0/8,127.0.0.1,localhost,10.42.0.0/16\"\nEOF",

      # Проверяем что proxy доступен перед запуском k3s-agent
      "until curl -s --connect-timeout 5 --proxy http://${var.master_private_ip}:8888 https://registry-1.docker.io/v2/ -o /dev/null -w '%%{http_code}' | grep -qE '401|200'; do echo 'waiting for proxy on master...'; sleep 5; done",
      "echo 'Proxy OK'",

      "sudo systemctl daemon-reload",
      "sudo systemctl enable k3s-agent",
      "sudo systemctl start k3s-agent",

      # Pre-pull pause image — без него поды зависают при первом старте
      "until sudo k3s ctr images pull docker.io/rancher/mirrored-pause:3.6 2>/dev/null; do echo 'waiting for pause image...'; sleep 5; done",
    ]
  }
}
