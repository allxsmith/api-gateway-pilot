# RDS PostgreSQL backing the services. Single-AZ db.t4g.micro — the cheapest
# instance class and free-tier eligible.

resource "random_password" "db" {
  length  = 24
  special = false
}

# The database password is kept in SSM Parameter Store (free tier) and injected
# into the ECS tasks as an environment variable.
resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/${var.environment}/db-password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db"
  subnet_ids = aws_subnet.public[*].id
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az            = false
  publicly_accessible = false

  # The prototype is torn down and recreated freely; Flyway re-seeds demo data.
  skip_final_snapshot = true
  deletion_protection = false
  apply_immediately   = true

  tags = { Name = "${local.name}-db" }
}
