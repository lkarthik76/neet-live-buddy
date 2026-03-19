#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GOOGLE_CLOUD_PROJECT:-}" ]]; then
  echo "GOOGLE_CLOUD_PROJECT is required"
  exit 1
fi

if [[ -z "${NOTIFICATION_CHANNEL_ID:-}" ]]; then
  echo "NOTIFICATION_CHANNEL_ID is required"
  echo "Example: projects/${GOOGLE_CLOUD_PROJECT}/notificationChannels/1234567890"
  exit 1
fi

create_policy() {
  local display_name="$1"
  local metric_type="$2"
  local threshold="$3"
  local duration="$4"

  local temp_file
  temp_file="$(mktemp)"
  cat >"${temp_file}" <<EOF
{
  "displayName": "${display_name}",
  "combiner": "OR",
  "conditions": [
    {
      "displayName": "${display_name} condition",
      "conditionThreshold": {
        "filter": "metric.type=\\"${metric_type}\\" resource.type=\\"global\\"",
        "comparison": "COMPARISON_GT",
        "thresholdValue": ${threshold},
        "duration": "${duration}",
        "aggregations": [
          {
            "alignmentPeriod": "300s",
            "perSeriesAligner": "ALIGN_RATE"
          }
        ],
        "trigger": {
          "count": 1
        }
      }
    }
  ],
  "notificationChannels": [
    "${NOTIFICATION_CHANNEL_ID}"
  ],
  "enabled": true
}
EOF

  gcloud monitoring policies create --policy-from-file="${temp_file}"
  rm -f "${temp_file}"
}

echo "Creating monitoring alert policies..."
create_policy "Purchase Verification Failures Spike" "logging.googleapis.com/user/purchase_verification_failed_count" "0.05" "300s"
create_policy "RTDN Unauthorized Spike" "logging.googleapis.com/user/rtdn_unauthorized_count" "0.05" "300s"
create_policy "Reconcile Downgrade Spike" "logging.googleapis.com/user/reconcile_downgraded_count" "0.01" "300s"
echo "Done."
