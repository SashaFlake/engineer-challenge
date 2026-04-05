terraform {
  required_version = ">= 1.7"

  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.13"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Используй remote backend для хранения state (S3-совместимое хранилище Cloud.ru)
  # backend "s3" {
  #   bucket                      = "your-tf-state-bucket"
  #   key                         = "engineer-challenge/terraform.tfstate"
  #   region                      = "ru-central1"
  #   endpoint                    = "https://storage.cloud.ru"
  #   skip_credentials_validation = true
  #   skip_metadata_api_check     = true
  #   skip_region_validation      = true
  #   force_path_style            = true
  # }
}
