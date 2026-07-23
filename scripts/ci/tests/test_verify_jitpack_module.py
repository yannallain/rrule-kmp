from __future__ import annotations

import contextlib
import copy
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path


CI_SCRIPT_DIRECTORY = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CI_SCRIPT_DIRECTORY))

from verify_jitpack_module import (  # noqa: E402
    JITPACK_REWRITTEN_ROOT_GROUP,
    PLATFORM_VARIANTS,
    PUBLICATION_GROUP,
    JitPackMetadataError,
    main,
    validate,
)


VERSION = "0.1.0"

EXPECTED_PLATFORM_VARIANTS = {
    "androidApiElements-published": "rrule-kmp-android",
    "androidRuntimeElements-published": "rrule-kmp-android",
    "androidSourcesElements-published": "rrule-kmp-android",
    "iosArm64ApiElements-published": "rrule-kmp-iosarm64",
    "iosArm64MetadataElements-published": "rrule-kmp-iosarm64",
    "iosArm64SourcesElements-published": "rrule-kmp-iosarm64",
    (
        "iosSimulatorArm64ApiElements-published"
    ): "rrule-kmp-iossimulatorarm64",
    (
        "iosSimulatorArm64MetadataElements-published"
    ): "rrule-kmp-iossimulatorarm64",
    (
        "iosSimulatorArm64SourcesElements-published"
    ): "rrule-kmp-iossimulatorarm64",
    "iosX64ApiElements-published": "rrule-kmp-iosx64",
    "iosX64MetadataElements-published": "rrule-kmp-iosx64",
    "iosX64SourcesElements-published": "rrule-kmp-iosx64",
    "jvmApiElements-published": "rrule-kmp-jvm",
    "jvmRuntimeElements-published": "rrule-kmp-jvm",
    "jvmSourcesElements-published": "rrule-kmp-jvm",
}


def _target(module: str) -> dict[str, str]:
    return {
        "url": f"../../{module}/{VERSION}/{module}-{VERSION}.module",
        "group": PUBLICATION_GROUP,
        "module": module,
        "version": VERSION,
    }


def _valid_metadata() -> dict:
    variants = [
        {"name": "metadataApiElements"},
        {"name": "metadataSourcesElements"},
    ]
    variants.extend(
        {
            "name": variant_name,
            "available-at": _target(module),
        }
        for variant_name, module in EXPECTED_PLATFORM_VARIANTS.items()
    )
    return {
        "formatVersion": "1.1",
        "component": {
            "group": JITPACK_REWRITTEN_ROOT_GROUP,
            "module": "rrule-kmp",
            "version": VERSION,
        },
        "variants": variants,
    }


class VerifyJitPackModuleTest(unittest.TestCase):
    def test_platform_contract_is_complete(self) -> None:
        self.assertEqual(EXPECTED_PLATFORM_VARIANTS, PLATFORM_VARIANTS)

    def test_accepts_jitpack_root_rewrite_and_every_platform(self) -> None:
        validate(_valid_metadata(), VERSION)

        metadata = _valid_metadata()
        metadata["component"]["group"] = PUBLICATION_GROUP
        validate(metadata, VERSION)

    def test_rejects_unexpected_root_identity(self) -> None:
        invalid_values = [
            ("group", "com.example"),
            ("group", {"not": "a string"}),
            ("module", "another-module"),
            ("version", "0.2.0"),
        ]
        for field, value in invalid_values:
            with self.subTest(field=field):
                metadata = _valid_metadata()
                metadata["component"][field] = value
                with self.assertRaisesRegex(
                    JitPackMetadataError,
                    rf"component\.{field}",
                ):
                    validate(metadata, VERSION)

    def test_rejects_missing_common_or_platform_variant(self) -> None:
        missing_names = [
            "metadataApiElements",
            "androidRuntimeElements-published",
            "iosSimulatorArm64ApiElements-published",
            "jvmRuntimeElements-published",
        ]
        for missing_name in missing_names:
            with self.subTest(missing_name=missing_name):
                metadata = _valid_metadata()
                metadata["variants"] = [
                    variant
                    for variant in metadata["variants"]
                    if variant["name"] != missing_name
                ]
                with self.assertRaisesRegex(
                    JitPackMetadataError,
                    "Missing",
                ):
                    validate(metadata, VERSION)

    def test_rejects_incorrect_platform_redirect_fields(self) -> None:
        invalid_values = {
            "group": JITPACK_REWRITTEN_ROOT_GROUP,
            "module": "rrule-kmp-jvm",
            "version": "0.2.0",
            "url": "../../wrong/module.json",
        }
        for field, value in invalid_values.items():
            with self.subTest(field=field):
                metadata = _valid_metadata()
                variant = next(
                    item
                    for item in metadata["variants"]
                    if item["name"] == "androidApiElements-published"
                )
                variant["available-at"][field] = value
                with self.assertRaisesRegex(
                    JitPackMetadataError,
                    rf"available-at\.{field}",
                ):
                    validate(metadata, VERSION)

    def test_rejects_unknown_or_invalid_redirect_module(self) -> None:
        invalid_modules = [
            "rrule-kmp-unexpected",
            {"not": "a string"},
        ]
        for invalid_module in invalid_modules:
            with self.subTest(invalid_module=invalid_module):
                metadata = _valid_metadata()
                metadata["variants"].append(
                    {
                        "name": "unexpectedApiElements",
                        "available-at": {
                            **_target("rrule-kmp-unexpected"),
                            "module": invalid_module,
                        },
                    }
                )
                with self.assertRaisesRegex(
                    JitPackMetadataError,
                    "unsupported module",
                ):
                    validate(metadata, VERSION)

    def test_rejects_duplicate_variant(self) -> None:
        metadata = _valid_metadata()
        metadata["variants"].append(copy.deepcopy(metadata["variants"][0]))
        with self.assertRaisesRegex(
            JitPackMetadataError,
            "Duplicate variant name",
        ):
            validate(metadata, VERSION)

    def test_cli_reports_malformed_metadata_without_traceback(self) -> None:
        invalid_contents = [
            b"{not-json",
            b"\xff",
        ]
        for invalid_content in invalid_contents:
            with self.subTest(invalid_content=invalid_content):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    module = Path(temporary_directory) / "rrule-kmp.module"
                    module.write_bytes(invalid_content)
                    standard_error = io.StringIO()
                    with contextlib.redirect_stderr(standard_error):
                        exit_code = main(
                            [
                                "--module",
                                str(module),
                                "--version",
                                VERSION,
                            ]
                        )

                self.assertEqual(1, exit_code)
                self.assertIn(
                    "JitPack metadata validation failed",
                    standard_error.getvalue(),
                )
                self.assertNotIn("Traceback", standard_error.getvalue())

    def test_cli_reports_invalid_structure_without_traceback(self) -> None:
        metadata = _valid_metadata()
        metadata["component"]["group"] = {"not": "a string"}
        with tempfile.TemporaryDirectory() as temporary_directory:
            module = Path(temporary_directory) / "rrule-kmp.module"
            module.write_text(json.dumps(metadata), encoding="utf-8")
            standard_error = io.StringIO()
            with contextlib.redirect_stderr(standard_error):
                exit_code = main(
                    [
                        "--module",
                        str(module),
                        "--version",
                        VERSION,
                    ]
                )

        self.assertEqual(1, exit_code)
        self.assertIn("component.group", standard_error.getvalue())
        self.assertNotIn("Traceback", standard_error.getvalue())

    def test_cli_accepts_valid_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            module = Path(temporary_directory) / "rrule-kmp.module"
            module.write_text(
                json.dumps(_valid_metadata()),
                encoding="utf-8",
            )
            standard_output = io.StringIO()
            with contextlib.redirect_stdout(standard_output):
                exit_code = main(
                    [
                        "--module",
                        str(module),
                        "--version",
                        VERSION,
                    ]
                )

        self.assertEqual(0, exit_code)
        self.assertIn(PUBLICATION_GROUP, standard_output.getvalue())
