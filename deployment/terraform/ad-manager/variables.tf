# Docker
variable "image" {}
variable "image_pull_policy" {
  default = "IfNotPresent"
}

# Kubernetes
variable "namespace" {}
variable "enabled" {}
variable "replicas" {}
variable "cpu_limit" {}
variable "cpu_request" {}
variable "memory_limit" {}
variable "memory_request" {}
variable "node_selector_label" {}
variable "kubectl_executable_name" {}
variable "kubectl_context_name" {}

# Environment
variable "jvm_memory_limit" {}
variable "graphite_hostname" {}
variable "graphite_port" {}
variable "graphite_enabled" {}
variable "graphite_prefix" {
  default = ""
}
variable "env_vars" {}

# App
variable "metric_consumer_bootstrap_servers" {}
variable "metric_consumer_group_id" {}
variable "metric_consumer_topic" {}
variable "metric_consumer_key_deserializer" {}
variable "metric_consumer_value_deserializer" {}
variable "anomaly_producer_bootstrap_servers" {}
variable "anomaly_producer_client_id" {}
variable "anomaly_producer_outlier_topic" {}
variable "anomaly_producer_breakout_topic" {}
variable "anomaly_producer_key_serializer" {}
variable "anomaly_producer_value_serializer" {}
variable "modelservice_base_uri" {}
variable "graphite_base_uri" {}
variable "graphite_data_retrieval_key" {}
variable "throttle_gate_likelihood" {}
variable "tracing_status" {}
variable "ad_manager_tracing_apikey" {}
variable "ad_manager_tracing_clientid" {}
variable "haystack_collector_endpoint" {}
variable "haystack_tracer_queue_size" {}
variable "haystack_tracer_flush_interval" {}
variable "haystack_tracer_shutdown_timeout" {}
variable "haystack_tracer_thread_count" {}
variable "detector_refresh_period" {
  default = 5
}
