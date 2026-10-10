module "data" {
  source = "../../modules/data"

  name_prefix            = var.name_prefix
  point_in_time_recovery = false
  deletion_protection    = false
}

module "messaging" {
  source = "../../modules/messaging"

  name_prefix = var.name_prefix
}
