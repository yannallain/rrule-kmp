#!/usr/bin/env python3
"""Validate JitPack's root Gradle module metadata for every KMP target."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


JITPACK_REWRITTEN_ROOT_GROUP = "com.github.yannallain"
PUBLICATION_GROUP = "com.github.yannallain.rrule-kmp"
ROOT_MODULE = "rrule-kmp"
VALID_ROOT_GROUPS = {
    JITPACK_REWRITTEN_ROOT_GROUP,
    PUBLICATION_GROUP,
}

PLATFORM_VARIANTS = {
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


class JitPackMetadataError(RuntimeError):
    """Raised when hosted metadata cannot route every supported target."""


def _arguments(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate JitPack root metadata and all KMP platform redirects."
        )
    )
    parser.add_argument("--module", required=True, type=Path)
    parser.add_argument("--version", required=True)
    return parser.parse_args(argv)


def _mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise JitPackMetadataError(f"{label} must be a JSON object.")
    return value


def _variants(metadata: dict[str, Any]) -> dict[str, dict[str, Any]]:
    raw_variants = metadata.get("variants")
    if not isinstance(raw_variants, list):
        raise JitPackMetadataError("variants must be a JSON array.")

    variants: dict[str, dict[str, Any]] = {}
    for index, raw_variant in enumerate(raw_variants):
        variant = _mapping(raw_variant, f"variants[{index}]")
        name = variant.get("name")
        if not isinstance(name, str) or not name:
            raise JitPackMetadataError(
                f"variants[{index}].name must be a non-empty string."
            )
        if name in variants:
            raise JitPackMetadataError(f"Duplicate variant name: {name}")
        variants[name] = variant
    return variants


def _expected_target_url(module: str, version: str) -> str:
    return f"../../{module}/{version}/{module}-{version}.module"


def _validate_target(
    *,
    variant_name: str,
    variant: dict[str, Any],
    expected_module: str,
    expected_version: str,
) -> None:
    target = _mapping(
        variant.get("available-at"),
        f"{variant_name}.available-at",
    )
    expected_values = {
        "group": PUBLICATION_GROUP,
        "module": expected_module,
        "version": expected_version,
        "url": _expected_target_url(expected_module, expected_version),
    }
    for field, expected in expected_values.items():
        actual = target.get(field)
        if actual != expected:
            raise JitPackMetadataError(
                f"{variant_name}.available-at.{field} must be "
                f"{expected!r}, received {actual!r}."
            )


def validate(metadata: dict[str, Any], expected_version: str) -> None:
    """Validate root identity and platform redirects in JitPack metadata."""

    if not expected_version:
        raise JitPackMetadataError("Expected version must not be empty.")
    if metadata.get("formatVersion") != "1.1":
        raise JitPackMetadataError(
            "JitPack metadata must use Gradle formatVersion '1.1'."
        )

    component = _mapping(metadata.get("component"), "component")
    component_group = component.get("group")
    if (
        not isinstance(component_group, str)
        or component_group not in VALID_ROOT_GROUPS
    ):
        allowed_groups = ", ".join(sorted(VALID_ROOT_GROUPS))
        raise JitPackMetadataError(
            "component.group must be one of "
            f"{allowed_groups}, received {component_group!r}."
        )

    expected_component = {
        "module": ROOT_MODULE,
        "version": expected_version,
    }
    for field, expected in expected_component.items():
        actual = component.get(field)
        if actual != expected:
            raise JitPackMetadataError(
                f"component.{field} must be {expected!r}, "
                f"received {actual!r}."
            )

    variants = _variants(metadata)
    for common_variant in ("metadataApiElements", "metadataSourcesElements"):
        if common_variant not in variants:
            raise JitPackMetadataError(
                f"Missing common metadata variant: {common_variant}"
            )

    for variant_name, expected_module in PLATFORM_VARIANTS.items():
        variant = variants.get(variant_name)
        if variant is None:
            raise JitPackMetadataError(
                f"Missing platform variant: {variant_name}"
            )
        _validate_target(
            variant_name=variant_name,
            variant=variant,
            expected_module=expected_module,
            expected_version=expected_version,
        )

    expected_modules = set(PLATFORM_VARIANTS.values())
    observed_modules: set[str] = set()
    for variant_name, variant in variants.items():
        if "available-at" not in variant:
            continue
        target = _mapping(
            variant["available-at"],
            f"{variant_name}.available-at",
        )
        module = target.get("module")
        if not isinstance(module, str) or module not in expected_modules:
            raise JitPackMetadataError(
                f"{variant_name} redirects to unsupported module {module!r}."
            )
        _validate_target(
            variant_name=variant_name,
            variant=variant,
            expected_module=module,
            expected_version=expected_version,
        )
        observed_modules.add(module)

    if observed_modules != expected_modules:
        missing = ", ".join(sorted(expected_modules - observed_modules))
        raise JitPackMetadataError(
            f"JitPack metadata does not expose every platform: {missing}"
        )


def main(argv: list[str] | None = None) -> int:
    arguments = _arguments(argv)
    try:
        metadata = json.loads(arguments.module.read_text(encoding="utf-8"))
        validate(_mapping(metadata, "root"), arguments.version)
    except (
        JitPackMetadataError,
        json.JSONDecodeError,
        OSError,
        UnicodeError,
    ) as error:
        print(f"JitPack metadata validation failed: {error}", file=sys.stderr)
        return 1

    print(
        "Validated JitPack metadata for "
        f"{PUBLICATION_GROUP}:{ROOT_MODULE}:{arguments.version}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
