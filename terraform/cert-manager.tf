# ─── cert-manager ────────────────────────────────────────────────────────────

resource "helm_release" "cert_manager" {
  depends_on       = [helm_release.ingress_nginx]
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  version          = "v1.14.5"
  namespace        = "cert-manager"
  create_namespace = true

  set {
    name  = "crds.enabled"
    value = "true"
  }
}
