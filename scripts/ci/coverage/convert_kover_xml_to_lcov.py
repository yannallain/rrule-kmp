#!/usr/bin/env python3
"""Convert Kover's JaCoCo XML into path-accurate LCOV for Codecov."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


PACKAGE_PATTERN = re.compile(
    r"^[ \t]*package[ \t]+"
    r"([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)"
    r"[ \t]*;?[ \t]*(?://.*)?$",
    re.MULTILINE,
)
PACKAGE_PATH_PATTERN = re.compile(
    r"(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*"
)


class CoverageConversionError(RuntimeError):
    """Raised when coverage cannot be mapped without losing source files."""


@dataclass(frozen=True)
class SourceDescriptor:
    path: Path
    repository_path: str
    line_count: int


@dataclass(frozen=True)
class LineCoverage:
    number: int
    covered: bool
    covered_branches: int
    missed_branches: int


@dataclass(frozen=True)
class SourceCoverage:
    source: SourceDescriptor
    lines: tuple[LineCoverage, ...]


@dataclass(frozen=True)
class ConversionSummary:
    files: int
    lines: int
    covered_lines: int
    branches: int
    covered_branches: int


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Map Kover JaCoCo source entries to real repository paths and "
            "write deterministic LCOV."
        )
    )
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--repository-root",
        default=Path.cwd(),
        type=Path,
    )
    parser.add_argument(
        "--source-root",
        action="append",
        dest="source_roots",
        required=True,
        type=Path,
    )
    return parser.parse_args()


def _repository_relative(path: Path, repository_root: Path) -> str:
    try:
        return path.resolve().relative_to(repository_root).as_posix()
    except ValueError as error:
        raise CoverageConversionError(
            f"Source path escapes the repository: {path}"
        ) from error


def _declared_package(source: Path, contents: str) -> str:
    match = PACKAGE_PATTERN.search(contents)
    if match is None:
        return ""
    return match.group(1).replace(".", "/")


def _source_index(
    repository_root: Path,
    source_roots: list[Path],
) -> tuple[dict[tuple[str, str], SourceDescriptor], set[str]]:
    index: dict[tuple[str, str], list[SourceDescriptor]] = {}
    repository_paths: set[str] = set()

    for configured_root in source_roots:
        root = (
            configured_root
            if configured_root.is_absolute()
            else repository_root / configured_root
        ).resolve()
        _repository_relative(root, repository_root)
        if not root.is_dir():
            raise CoverageConversionError(
                f"Coverage source root does not exist: {configured_root}"
            )

        for source in sorted(root.rglob("*.kt")):
            contents = source.read_text(encoding="utf-8")
            repository_path = _repository_relative(source, repository_root)
            descriptor = SourceDescriptor(
                path=source,
                repository_path=repository_path,
                line_count=len(contents.splitlines()),
            )
            key = (_declared_package(source, contents), source.name)
            index.setdefault(key, []).append(descriptor)
            repository_paths.add(repository_path)

    if not repository_paths:
        raise CoverageConversionError("No Kotlin production sources were found.")

    ambiguous = {
        key: descriptors
        for key, descriptors in index.items()
        if len(descriptors) != 1
    }
    if ambiguous:
        details = "; ".join(
            f"{package}/{filename}: "
            + ", ".join(item.repository_path for item in descriptors)
            for (package, filename), descriptors in sorted(ambiguous.items())
        )
        raise CoverageConversionError(
            f"Coverage source keys must be unique: {details}"
        )

    return (
        {key: descriptors[0] for key, descriptors in index.items()},
        repository_paths,
    )


def _nonnegative_integer(element: ElementTree.Element, attribute: str) -> int:
    raw_value = element.get(attribute)
    try:
        value = int(raw_value) if raw_value is not None else -1
    except ValueError as error:
        raise CoverageConversionError(
            f"Invalid integer {attribute}={raw_value!r} on <{element.tag}>."
        ) from error
    if value < 0:
        raise CoverageConversionError(
            f"Expected nonnegative {attribute} on <{element.tag}>."
        )
    return value


def _counter(
    sourcefile: ElementTree.Element,
    counter_type: str,
) -> tuple[int, int]:
    counters = [
        counter
        for counter in sourcefile.findall("counter")
        if counter.get("type") == counter_type
    ]
    if len(counters) != 1:
        name = sourcefile.get("name", "<unknown>")
        raise CoverageConversionError(
            f"{name} must contain exactly one {counter_type} counter."
        )
    counter = counters[0]
    return (
        _nonnegative_integer(counter, "missed"),
        _nonnegative_integer(counter, "covered"),
    )


def _validate_package_path(package_name: str) -> None:
    path = PurePosixPath(package_name)
    if (
        path.is_absolute()
        or ".." in path.parts
        or (
            package_name
            and PACKAGE_PATH_PATTERN.fullmatch(package_name) is None
        )
    ):
        raise CoverageConversionError(
            f"Invalid package path in coverage report: {package_name!r}"
        )


def _read_coverage(
    report: Path,
    source_index: dict[tuple[str, str], SourceDescriptor],
    expected_sources: set[str],
) -> tuple[SourceCoverage, ...]:
    try:
        report_bytes = report.read_bytes()
        upper_report = report_bytes.upper()
        if b"<!DOCTYPE" in upper_report or b"<!ENTITY" in upper_report:
            raise CoverageConversionError(
                "Kover XML must not contain DTD or entity declarations."
            )
        root = ElementTree.fromstring(report_bytes)
    except CoverageConversionError:
        raise
    except (ElementTree.ParseError, OSError) as error:
        raise CoverageConversionError(
            f"Cannot parse Kover XML report: {report}"
        ) from error
    if root.tag != "report":
        raise CoverageConversionError(
            f"Expected a JaCoCo <report> root, found <{root.tag}>."
        )

    packages = root.findall("package")
    if not packages:
        raise CoverageConversionError("Kover XML contains no packages.")

    mapped_paths: set[str] = set()
    mapped_coverage: list[SourceCoverage] = []
    for package in packages:
        package_name = package.get("name")
        if package_name is None:
            raise CoverageConversionError("Coverage package has no name.")
        _validate_package_path(package_name)

        for sourcefile in package.findall("sourcefile"):
            filename = sourcefile.get("name")
            if (
                not filename
                or "/" in filename
                or "\\" in filename
                or PurePosixPath(filename).name != filename
                or filename in {".", ".."}
            ):
                raise CoverageConversionError(
                    f"Invalid source filename in coverage report: {filename!r}"
                )
            key = (package_name, filename)
            source = source_index.get(key)
            if source is None:
                raise CoverageConversionError(
                    "Kover source has no unique production-file match: "
                    f"{package_name}/{filename}"
                )
            if source.repository_path in mapped_paths:
                raise CoverageConversionError(
                    f"Source appears more than once: {source.repository_path}"
                )

            line_elements = sourcefile.findall("line")
            if not line_elements:
                raise CoverageConversionError(
                    f"Coverage source has no executable lines: "
                    f"{source.repository_path}"
                )

            lines: list[LineCoverage] = []
            previous_line = 0
            for line in line_elements:
                number = _nonnegative_integer(line, "nr")
                missed_instructions = _nonnegative_integer(line, "mi")
                covered_instructions = _nonnegative_integer(line, "ci")
                missed_branches = _nonnegative_integer(line, "mb")
                covered_branches = _nonnegative_integer(line, "cb")
                if number <= previous_line:
                    raise CoverageConversionError(
                        f"Coverage lines must be strictly ordered in "
                        f"{source.repository_path}."
                    )
                if number > source.line_count:
                    raise CoverageConversionError(
                        f"Coverage line {number} exceeds "
                        f"{source.repository_path} ({source.line_count} lines)."
                    )
                if missed_instructions + covered_instructions == 0:
                    raise CoverageConversionError(
                        f"Coverage line {number} has no instructions in "
                        f"{source.repository_path}."
                    )
                lines.append(
                    LineCoverage(
                        number=number,
                        covered=covered_instructions > 0,
                        covered_branches=covered_branches,
                        missed_branches=missed_branches,
                    )
                )
                previous_line = number

            missed_instruction_counter, covered_instruction_counter = _counter(
                sourcefile,
                "INSTRUCTION",
            )
            if missed_instruction_counter != sum(
                _nonnegative_integer(line, "mi")
                for line in line_elements
            ) or covered_instruction_counter != sum(
                _nonnegative_integer(line, "ci")
                for line in line_elements
            ):
                raise CoverageConversionError(
                    f"Instruction counters disagree with line data in "
                    f"{source.repository_path}."
                )

            _counter(sourcefile, "LINE")
            missed_branch_counter, covered_branch_counter = _counter(
                sourcefile,
                "BRANCH",
            )
            if missed_branch_counter != sum(
                line.missed_branches for line in lines
            ) or covered_branch_counter != sum(
                line.covered_branches for line in lines
            ):
                raise CoverageConversionError(
                    f"Branch counters disagree with line data in "
                    f"{source.repository_path}."
                )

            mapped_paths.add(source.repository_path)
            mapped_coverage.append(
                SourceCoverage(source=source, lines=tuple(lines))
            )

    if not mapped_coverage:
        raise CoverageConversionError("Kover XML contains no source files.")

    unreported_sources = sorted(expected_sources - mapped_paths)
    if unreported_sources:
        raise CoverageConversionError(
            "Production sources are absent from Kover XML: "
            + ", ".join(unreported_sources)
        )

    return tuple(
        sorted(
            mapped_coverage,
            key=lambda coverage: coverage.source.repository_path,
        )
    )


def _line_lcov(coverage: tuple[SourceCoverage, ...]) -> str:
    """Render line-only LCOV while preserving Kover's line semantics.

    Codecov classifies an executed line as uncovered when any branch on that
    line is missed. Kover's enforced threshold is a line-coverage threshold,
    so the public LCOV intentionally omits branch records. Branch counters are
    still validated while reading the XML and remain available in Kover's
    retained XML and HTML reports.
    """
    output: list[str] = []
    for source_coverage in coverage:
        output.extend(
            (
                "TN:rrule-kmp-jvm",
                f"SF:{source_coverage.source.repository_path}",
            )
        )
        for line in source_coverage.lines:
            output.append(f"DA:{line.number},{1 if line.covered else 0}")

        covered_lines = sum(line.covered for line in source_coverage.lines)
        output.extend(
            (
                f"LF:{len(source_coverage.lines)}",
                f"LH:{covered_lines}",
                "end_of_record",
            )
        )
    return "\n".join(output) + "\n"


def convert(
    report: Path,
    output: Path,
    repository_root: Path,
    source_roots: list[Path],
) -> ConversionSummary:
    """Convert one Kover XML report and return its verified LCOV totals."""
    resolved_repository_root = repository_root.resolve()
    if not resolved_repository_root.is_dir():
        raise CoverageConversionError(
            f"Repository root does not exist: {repository_root}"
        )
    source_index, expected_sources = _source_index(
        resolved_repository_root,
        source_roots,
    )
    coverage = _read_coverage(report, source_index, expected_sources)
    contents = _line_lcov(coverage)
    if not contents.strip():
        raise CoverageConversionError("Generated LCOV report is empty.")

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary_output = output.with_name(f".{output.name}.tmp")
    temporary_output.write_text(contents, encoding="utf-8", newline="\n")
    temporary_output.replace(output)

    all_lines = [
        line
        for source_coverage in coverage
        for line in source_coverage.lines
    ]
    return ConversionSummary(
        files=len(coverage),
        lines=len(all_lines),
        covered_lines=sum(line.covered for line in all_lines),
        branches=sum(
            line.covered_branches + line.missed_branches
            for line in all_lines
        ),
        covered_branches=sum(line.covered_branches for line in all_lines),
    )


def main() -> int:
    arguments = _arguments()
    try:
        summary = convert(
            report=arguments.report,
            output=arguments.output,
            repository_root=arguments.repository_root,
            source_roots=arguments.source_roots,
        )
    except CoverageConversionError as error:
        print(f"Coverage conversion failed: {error}", file=sys.stderr)
        return 1

    line_percent = 100.0 * summary.covered_lines / summary.lines
    branch_percent = (
        100.0
        if summary.branches == 0
        else 100.0 * summary.covered_branches / summary.branches
    )
    print(
        f"Mapped {summary.files} sources: "
        f"{summary.covered_lines}/{summary.lines} lines "
        f"({line_percent:.2f}%), "
        f"validated {summary.covered_branches}/{summary.branches} branches "
        f"({branch_percent:.2f}%)."
    )
    print(f"Wrote path-accurate LCOV to {arguments.output}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
