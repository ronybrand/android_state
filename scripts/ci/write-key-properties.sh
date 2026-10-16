#!/usr/bin/env bash
# Writes key.properties from environment variables, for CI. The values come from GitHub secrets (or, in the signing
# check, from a throwaway key): nothing here is ever printed.
set -euo pipefail

: "${KEYSTORE_PATH:?KEYSTORE_PATH is required}"
: "${KEYSTORE_PASSWORD:?KEYSTORE_PASSWORD is required}"
: "${KEY_PASSWORD:?KEY_PASSWORD is required}"
: "${KEY_ALIAS:?KEY_ALIAS is required}"

umask 077
cat > key.properties <<PROPS
storePassword=${KEYSTORE_PASSWORD}
keyPassword=${KEY_PASSWORD}
keyAlias=${KEY_ALIAS}
storeFile=${KEYSTORE_PATH}
PROPS
