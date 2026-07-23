from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "run-with-network-retries.sh"


class RunWithNetworkRetriesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.directory = Path(self.temporary_directory.name)
        self.attempt_file = self.directory / "attempt"
        self.command = self.directory / "fake-command.sh"
        self.command.write_text(
            (
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                'attempt="$(cat "$ATTEMPT_FILE" 2>/dev/null || echo 0)"\n'
                "attempt=$((attempt + 1))\n"
                'printf "%s\\n" "$attempt" > "$ATTEMPT_FILE"\n'
                'case "$FAKE_MODE" in\n'
                "  transient-then-success)\n"
                "    if (( attempt < 3 )); then\n"
                '      echo "java.net.UnknownHostException: dependency host"\n'
                "      exit 17\n"
                "    fi\n"
                '    echo "success"\n'
                "    ;;\n"
                "  http-transient-then-success)\n"
                "    if (( attempt < 2 )); then\n"
                "      echo \"Received status code 502 from server: \""
                "\"Bad Gateway\"\n"
                "      exit 18\n"
                "    fi\n"
                '    echo "success"\n'
                "    ;;\n"
                "  curl-transient-then-success)\n"
                "    if (( attempt < 2 )); then\n"
                "      echo \"curl: (22) The requested URL returned \""
                "\"error: 503\"\n"
                "      exit 20\n"
                "    fi\n"
                '    echo "success"\n'
                "    ;;\n"
                "  socket-transient-then-success)\n"
                "    if (( attempt < 2 )); then\n"
                '      echo "java.net.SocketException: Connection reset"\n'
                "      exit 21\n"
                "    fi\n"
                '    echo "success"\n'
                "    ;;\n"
                "  connect-transient-then-success)\n"
                "    if (( attempt < 2 )); then\n"
                '      echo "java.net.ConnectException: Connection refused"\n'
                "      exit 22\n"
                "    fi\n"
                '    echo "success"\n'
                "    ;;\n"
                "  transient)\n"
                '    echo "Temporary failure in name resolution"\n'
                "    exit 19\n"
                "    ;;\n"
                "  transient-then-deterministic)\n"
                "    if (( attempt < 2 )); then\n"
                '      echo "java.net.UnknownHostException: dependency host"\n'
                "      exit 17\n"
                "    fi\n"
                '    echo "Compilation failed"\n'
                "    exit 23\n"
                "    ;;\n"
                "  deterministic)\n"
                '    echo "Compilation failed"\n'
                "    exit 23\n"
                "    ;;\n"
                "  similar-deterministic)\n"
                "    echo 'Assertion expected Connection reset and "
                "503 Service Unavailable'\n"
                "    exit 29\n"
                "    ;;\n"
                "  success)\n"
                '    echo "success"\n'
                "    ;;\n"
                "esac\n"
            ),
            encoding="utf-8",
        )
        self.command.chmod(0o755)

    def _run(self, mode: str) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment.update(
            {
                "ATTEMPT_FILE": str(self.attempt_file),
                "FAKE_MODE": mode,
                "NETWORK_RETRY_DELAY_SECONDS": "0",
            }
        )
        return subprocess.run(
            [str(SCRIPT), str(self.command)],
            check=False,
            capture_output=True,
            env=environment,
            text=True,
        )

    def _attempts(self) -> int:
        return int(self.attempt_file.read_text(encoding="utf-8"))

    def test_retries_transient_failures_until_success(self) -> None:
        result = self._run("transient-then-success")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(3, self._attempts())
        self.assertEqual(
            2,
            result.stderr.count("Transient network failure"),
        )
        self.assertIn("success", result.stdout)

    def test_recognizes_supported_transient_signatures(self) -> None:
        for mode in (
            "http-transient-then-success",
            "curl-transient-then-success",
            "socket-transient-then-success",
            "connect-transient-then-success",
        ):
            with self.subTest(mode=mode):
                self.attempt_file.unlink(missing_ok=True)
                result = self._run(mode)

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(2, self._attempts())

    def test_does_not_retry_deterministic_failure(self) -> None:
        result = self._run("deterministic")

        self.assertEqual(23, result.returncode)
        self.assertEqual(1, self._attempts())
        self.assertIn(
            "without a recognized transient network error",
            result.stderr,
        )

    def test_does_not_retry_similar_non_network_text(self) -> None:
        result = self._run("similar-deterministic")

        self.assertEqual(29, result.returncode)
        self.assertEqual(1, self._attempts())

    def test_stops_when_retry_reveals_deterministic_failure(self) -> None:
        result = self._run("transient-then-deterministic")

        self.assertEqual(23, result.returncode)
        self.assertEqual(2, self._attempts())

    def test_fails_after_three_transient_attempts(self) -> None:
        result = self._run("transient")

        self.assertEqual(19, result.returncode)
        self.assertEqual(3, self._attempts())
        self.assertIn(
            "failed after 3 network attempts",
            result.stderr,
        )

    def test_succeeds_without_retry(self) -> None:
        result = self._run("success")

        self.assertEqual(0, result.returncode)
        self.assertEqual(1, self._attempts())

    def test_requires_a_command(self) -> None:
        result = subprocess.run(
            [str(SCRIPT)],
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(64, result.returncode)
        self.assertIn("Usage:", result.stderr)

    def test_fails_closed_when_output_capture_fails(self) -> None:
        failing_tee = self.directory / "tee"
        failing_tee.write_text(
            "#!/usr/bin/env bash\n/bin/cat >/dev/null\nexit 31\n",
            encoding="utf-8",
        )
        failing_tee.chmod(0o755)
        environment = os.environ.copy()
        environment.update(
            {
                "ATTEMPT_FILE": str(self.attempt_file),
                "FAKE_MODE": "success",
                "NETWORK_RETRY_DELAY_SECONDS": "0",
                "PATH": f"{self.directory}:{environment['PATH']}",
            }
        )

        result = subprocess.run(
            [str(SCRIPT), str(self.command)],
            check=False,
            capture_output=True,
            env=environment,
            text=True,
        )

        self.assertEqual(31, result.returncode)
        self.assertEqual(1, self._attempts())
        self.assertIn("Could not capture command output", result.stderr)

    def test_rejects_invalid_retry_delay(self) -> None:
        environment = os.environ.copy()
        environment["NETWORK_RETRY_DELAY_SECONDS"] = "later"

        result = subprocess.run(
            [str(SCRIPT), str(self.command)],
            check=False,
            capture_output=True,
            env=environment,
            text=True,
        )

        self.assertEqual(64, result.returncode)
        self.assertIn("must be a nonnegative number", result.stderr)


if __name__ == "__main__":
    unittest.main()
