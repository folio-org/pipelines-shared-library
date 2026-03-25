resource "aws_s3_bucket" "this" {
  bucket = var.bucket_name

  tags = merge(
    var.tags,
    {
      Name = var.bucket_name
    }
  )
}

resource "aws_s3_bucket_versioning" "this" {
  bucket = aws_s3_bucket.this.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "metrics-expiration"
    status = "Enabled"

    expiration {
      days = var.metrics_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.metrics_retention_days
    }
  }
}

resource "aws_ssm_parameter" "this" {
  name  = var.bucket_name
  type  = "String"
  value = aws_s3_bucket.this.bucket

  tags = merge(
    var.tags,
    {
      Name = "${var.bucket_name}-parameters"
    }
  )
}
