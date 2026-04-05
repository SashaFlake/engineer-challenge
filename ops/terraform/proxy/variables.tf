variable "master_host" {
  description = "Public IP of the master node"
  type        = string
  default     = "213.171.30.170"
}

variable "master_internal_ip" {
  description = "Internal IP of the master node (used as proxy address)"
  type        = string
  default     = "10.0.1.5"
}

variable "worker_host" {
  description = "Internal IP of the worker node"
  type        = string
  default     = "10.0.1.4"
}

variable "ssh_user" {
  description = "SSH user for both nodes"
  type        = string
  default     = "user1"
}

variable "master_ssh_key" {
  description = "Path to SSH private key for master (public-facing)"
  type        = string
  default     = "~/.ssh/cloudruce"
}

variable "worker_ssh_key" {
  description = "Path to SSH private key for worker"
  type        = string
  default     = "~/.ssh/id_rsa_worker"
}

variable "proxy_port" {
  description = "tinyproxy port on master"
  type        = number
  default     = 8888
}

variable "internal_subnet" {
  description = "Internal subnet CIDR (allowed by tinyproxy)"
  type        = string
  default     = "10.0.1.0/24"
}

variable "no_proxy" {
  description = "Comma-separated list of hosts/CIDRs to bypass proxy"
  type        = string
  default     = "10.0.0.0/8,127.0.0.1,localhost,10.42.0.0/16"
}
