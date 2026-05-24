locals {
  name = "${var.project}-${var.environment}"

  # Two availability zones for the public subnets.
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  services = ["auth-server", "resource-api", "nginx"]
}
