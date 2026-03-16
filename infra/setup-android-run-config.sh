#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
KMP_DIR="${REPO_ROOT}/kmp-app"
RUN_CFG_DIR="${KMP_DIR}/.idea/runConfigurations"

if [[ ! -d "${KMP_DIR}" ]]; then
  echo "kmp-app directory not found at: ${KMP_DIR}"
  exit 1
fi

mkdir -p "${RUN_CFG_DIR}"

write_config() {
  local file_path="$1"
  local config_name="$2"
  local module_name="$3"

  cat > "${file_path}" <<EOF
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="${config_name}" type="AndroidRunConfigurationType" factoryName="Android App">
    <module name="${module_name}" />
    <option name="DEPLOY" value="true" />
    <option name="OPEN_EDITORS" value="false" />
    <option name="SKIP_NOOP_APK_INSTALLATIONS" value="true" />
    <option name="FORCE_STOP_RUNNING_APP" value="true" />
    <option name="MODE" value="default_activity" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
EOF
}

# Create multiple module variants so at least one matches IDE module naming.
write_config "${RUN_CFG_DIR}/NeetBuddy_composeApp.xml" "NeetBuddy composeApp" "composeApp"
write_config "${RUN_CFG_DIR}/NeetBuddy_composeApp_androidMain.xml" "NeetBuddy composeApp.androidMain" "composeApp.androidMain"
write_config "${RUN_CFG_DIR}/NeetBuddy_kmp_composeApp_androidMain.xml" "NeetBuddy kmp composeApp.androidMain" "neet-live-buddy-kmp.composeApp.androidMain"

echo "Created run configurations in:"
echo "  ${RUN_CFG_DIR}"
echo ""
echo "Next steps in Android Studio:"
echo "1) Open the kmp-app project folder."
echo "2) File > Sync Project with Gradle Files."
echo "3) Select one of:"
echo "   - NeetBuddy composeApp"
echo "   - NeetBuddy composeApp.androidMain"
echo "   - NeetBuddy kmp composeApp.androidMain"
echo "4) Choose emulator/device and click Run."
