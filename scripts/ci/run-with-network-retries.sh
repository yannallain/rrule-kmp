#!/usr/bin/env bash

set -euo pipefail

if (( $# == 0 )); then
  echo "Usage: run-with-network-retries.sh <command> [arguments...]" >&2
  exit 64
fi

readonly maximum_attempts=3
readonly retry_delay_seconds="${NETWORK_RETRY_DELAY_SECONDS:-5}"

if [[ ! "$retry_delay_seconds" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "NETWORK_RETRY_DELAY_SECONDS must be a nonnegative number." >&2
  exit 64
fi

readonly network_failure_pattern="$(
  printf '%s' \
    'UnknownHostException|' \
    'SocketTimeoutException|' \
    'NoRouteToHostException|' \
    'SocketException: Connection reset( by peer)?|' \
    'ConnectException: (' \
    'Connection refused|Connection timed out|Connect timed out)|' \
    'Temporary failure in name resolution|' \
    'Name or service not known|' \
    'nodename nor servname provided|' \
    'Could not resolve host|' \
    'curl: \((6|7|18|28|35|52|56)\)|' \
    'curl: \(22\).*(502|503|504)|' \
    'Received status code (502|503|504) from server|' \
    'Server returned HTTP response code: (502|503|504)'
)"

retry_log_directory="$(mktemp -d)"
readonly retry_log_directory
trap 'rm -rf -- "$retry_log_directory"' EXIT

for (( attempt = 1; attempt <= maximum_attempts; attempt += 1 )); do
  attempt_log="$retry_log_directory/attempt-$attempt.log"

  set +e
  "$@" 2>&1 | tee "$attempt_log"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  command_status="${pipeline_status[0]}"
  tee_status="${pipeline_status[1]}"

  if (( tee_status != 0 )); then
    echo "Could not capture command output for network classification." >&2
    if (( command_status != 0 )); then
      exit "$command_status"
    fi
    exit "$tee_status"
  fi

  if (( command_status == 0 )); then
    exit 0
  fi

  if ! grep -Eiq "$network_failure_pattern" "$attempt_log"; then
    echo "Command failed without a recognized transient network error." >&2
    exit "$command_status"
  fi

  if (( attempt == maximum_attempts )); then
    echo \
      "Command still failed after $maximum_attempts network attempts." \
      >&2
    exit "$command_status"
  fi

  echo \
    "Transient network failure on attempt $attempt/$maximum_attempts; " \
    "retrying in ${retry_delay_seconds}s." \
    >&2
  sleep "$retry_delay_seconds"
done
