#!/usr/bin/env bash
# Wrapper: grant complimentary / scholarship tier in Firestore accounts/{email}.
# Usage:
#   export GOOGLE_CLOUD_PROJECT=your-project-id
#   ./grant-complimentary-tier.sh user@school.edu pro
#   ./grant-complimentary-tier.sh --dry-run user@school.edu ultimate
#   ./grant-complimentary-tier.sh user@school.edu pro --reason "Scholarship 2026 Q1"
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${SCRIPT_DIR}/grant_complimentary_tier.py" "$@"
