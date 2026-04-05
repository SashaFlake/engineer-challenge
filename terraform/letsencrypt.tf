# ClusterIssuer для Let's Encrypt
#
# Отключён пока нет домена. Чтобы включить — задай DNS-имя в terraform.tfvars
# и убери count = 0 ниже.

resource "null_resource" "letsencrypt_issuer" {
  count      = 0
  depends_on = [helm_release.cert_manager]

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG=${path.module}/kubeconfig.yaml

      echo "Waiting for cert-manager CRD to register..."
      until kubectl get crd clusterissuers.cert-manager.io 2>/dev/null; do
        echo "  waiting for clusterissuers CRD..."
        sleep 5
      done

      echo "CRD ready. Waiting for cert-manager webhook to become available..."
      kubectl rollout status deployment/cert-manager-webhook -n cert-manager --timeout=120s

      echo "Applying ClusterIssuer..."
      kubectl apply -f - <<'EOF'
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ${var.letsencrypt_email}
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            class: nginx
EOF

      echo "ClusterIssuer applied successfully."
    EOT
  }
}
