# ─── Helm dependency update ──────────────────────────────────────────────────
# Ждём пока воркер-нода зарегистрируется и перейдёт в Ready,
# чтобы Helm не начал деплой до того как кластер полностью готов.

resource "null_resource" "helm_deps" {
  depends_on = [null_resource.k3s_worker_join]

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG=${path.module}/kubeconfig.yaml

      echo "Waiting for all nodes to be Ready..."
      until kubectl get nodes --no-headers 2>/dev/null | grep -v 'NotReady' | grep -q 'Ready'; do
        echo "  waiting for worker node..."
        sleep 5
      done
      echo "All nodes Ready."

      helm dependency update ${path.module}/../helm/auth-service
    EOT
  }
}
