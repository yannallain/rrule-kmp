from __future__ import annotations

import contextlib
import io
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path


COVERAGE_SCRIPT_DIRECTORY = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(COVERAGE_SCRIPT_DIRECTORY))

from convert_kover_xml_to_lcov import (  # noqa: E402
    CoverageConversionError,
    convert,
    main,
)


class ConvertKoverXmlToLcovTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.repository = Path(self.temporary_directory.name)
        self.common_root = self.repository / "src/commonMain/kotlin"
        self.report = self.repository / "report.xml"
        self.output = self.repository / "report.info"

    def _source(
        self,
        relative_path: str,
        contents: str = (
            "package io.example\n\n"
            "fun schedule(): Int {\n"
            "    return 1\n"
            "}\n"
        ),
    ) -> Path:
        source = self.common_root / relative_path
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(contents, encoding="utf-8")
        return source

    def _coverage(
        self,
        filename: str = "Schedule.kt",
        lines: str = (
            '<line nr="3" mi="0" ci="2" mb="0" cb="2"/>\n'
            '<line nr="4" mi="1" ci="0" mb="1" cb="0"/>'
        ),
        line_missed: int = 1,
        line_covered: int = 1,
        instruction_missed: int = 1,
        instruction_covered: int = 2,
        branch_missed: int = 1,
        branch_covered: int = 2,
    ) -> None:
        self.report.write_text(
            (
                '<report name="test">\n'
                '  <package name="io/example">\n'
                f'    <sourcefile name="{filename}">\n'
                f"      {lines}\n"
                "      <counter type=\"INSTRUCTION\" "
                f'missed="{instruction_missed}" '
                f'covered="{instruction_covered}"/>\n'
                "      <counter type=\"LINE\" "
                f'missed="{line_missed}" covered="{line_covered}"/>\n'
                "      <counter type=\"BRANCH\" "
                f'missed="{branch_missed}" covered="{branch_covered}"/>\n'
                "    </sourcefile>\n"
                "  </package>\n"
                "</report>\n"
            ),
            encoding="utf-8",
        )

    def _convert(self):
        return convert(
            report=self.report,
            output=self.output,
            repository_root=self.repository,
            source_roots=[Path("src/commonMain/kotlin")],
        )

    def test_maps_source_folder_and_emits_line_only_lcov(self) -> None:
        self._source("io/example/engine/Schedule.kt")
        self._coverage()

        summary = self._convert()

        self.assertEqual(1, summary.files)
        self.assertEqual(2, summary.lines)
        self.assertEqual(1, summary.covered_lines)
        self.assertEqual(3, summary.branches)
        self.assertEqual(2, summary.covered_branches)
        self.assertEqual(
            (
                "TN:rrule-kmp-jvm\n"
                "SF:src/commonMain/kotlin/io/example/engine/Schedule.kt\n"
                "DA:3,1\n"
                "DA:4,0\n"
                "LF:2\n"
                "LH:1\n"
                "end_of_record\n"
            ),
            self.output.read_text(encoding="utf-8"),
        )

    def test_output_is_byte_for_byte_deterministic(self) -> None:
        self._source("io/example/engine/Schedule.kt")
        self._coverage()

        self._convert()
        first_output = self.output.read_bytes()
        self._convert()

        self.assertEqual(first_output, self.output.read_bytes())

    def test_rejects_missing_report_source(self) -> None:
        self._source("io/example/engine/Other.kt")
        self._coverage()

        with self.assertRaisesRegex(
            CoverageConversionError,
            "no unique production-file match",
        ):
            self._convert()

    def test_rejects_ambiguous_package_and_filename(self) -> None:
        self._source("io/example/engine/Schedule.kt")
        second_root = self.repository / "src/jvmMain/kotlin"
        second_source = second_root / "io/example/adapter/Schedule.kt"
        second_source.parent.mkdir(parents=True, exist_ok=True)
        second_source.write_text(
            "package io.example\n\nfun schedule(): Int = 2\n",
            encoding="utf-8",
        )
        self._coverage(
            lines='<line nr="3" mi="0" ci="1" mb="0" cb="0"/>',
            line_missed=0,
            instruction_missed=0,
            instruction_covered=1,
            branch_missed=0,
            branch_covered=0,
        )

        with self.assertRaisesRegex(
            CoverageConversionError,
            "Coverage source keys must be unique",
        ):
            convert(
                report=self.report,
                output=self.output,
                repository_root=self.repository,
                source_roots=[
                    Path("src/commonMain/kotlin"),
                    Path("src/jvmMain/kotlin"),
                ],
            )

    def test_rejects_unreported_production_source(self) -> None:
        self._source("io/example/engine/Schedule.kt")
        self._source(
            "io/example/model/Unreported.kt",
            "package io.example\n\nclass Unreported\n",
        )
        self._coverage()

        with self.assertRaisesRegex(
            CoverageConversionError,
            "Production sources are absent",
        ):
            self._convert()

    def test_rejects_duplicate_or_out_of_range_lines(self) -> None:
        self._source("io/example/engine/Schedule.kt")

        invalid_lines = {
            "duplicate": (
                '<line nr="3" mi="0" ci="1" mb="0" cb="0"/>\n'
                '<line nr="3" mi="0" ci="1" mb="0" cb="0"/>'
            ),
            "out of range": (
                '<line nr="99" mi="0" ci="1" mb="0" cb="0"/>'
            ),
        }
        for label, lines in invalid_lines.items():
            with self.subTest(label=label):
                self._coverage(
                    lines=lines,
                    line_missed=0,
                    line_covered=2 if label == "duplicate" else 1,
                    instruction_missed=0,
                    instruction_covered=2 if label == "duplicate" else 1,
                    branch_missed=0,
                    branch_covered=0,
                )
                with self.assertRaises(CoverageConversionError):
                    self._convert()

    def test_rejects_negative_or_inconsistent_counters(self) -> None:
        self._source("io/example/engine/Schedule.kt")

        self._coverage(
            lines='<line nr="3" mi="-1" ci="1" mb="0" cb="0"/>',
            line_missed=0,
            branch_missed=0,
            branch_covered=0,
        )
        with self.assertRaisesRegex(
            CoverageConversionError,
            "Expected nonnegative",
        ):
            self._convert()

        self._coverage(branch_covered=1)
        with self.assertRaisesRegex(
            CoverageConversionError,
            "Branch counters disagree",
        ):
            self._convert()

        self._coverage(instruction_covered=1)
        with self.assertRaisesRegex(
            CoverageConversionError,
            "Instruction counters disagree",
        ):
            self._convert()

    def test_rejects_dtd_and_unsafe_source_names(self) -> None:
        self._source("io/example/engine/Schedule.kt")
        self._coverage()
        valid_report = self.report.read_text(encoding="utf-8")

        self.report.write_text(
            "<!DOCTYPE report [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
            + valid_report,
            encoding="utf-8",
        )
        with self.assertRaisesRegex(
            CoverageConversionError,
            "DTD or entity",
        ):
            self._convert()

        self._coverage(filename="../Schedule.kt")
        with self.assertRaisesRegex(
            CoverageConversionError,
            "Invalid source filename",
        ):
            self._convert()

    def test_cli_reports_a_zero_branch_source(self) -> None:
        self._source("io/example/engine/Schedule.kt")
        self._coverage(
            lines='<line nr="3" mi="0" ci="1" mb="0" cb="0"/>',
            line_missed=0,
            instruction_missed=0,
            instruction_covered=1,
            branch_missed=0,
            branch_covered=0,
        )
        arguments = [
            "convert_kover_xml_to_lcov.py",
            "--report",
            str(self.report),
            "--output",
            str(self.output),
            "--repository-root",
            str(self.repository),
            "--source-root",
            "src/commonMain/kotlin",
        ]
        standard_output = io.StringIO()

        with mock.patch.object(sys, "argv", arguments):
            with contextlib.redirect_stdout(standard_output):
                self.assertEqual(0, main())

        self.assertIn(
            "validated 0/0 branches (100.00%)",
            standard_output.getvalue(),
        )


if __name__ == "__main__":
    unittest.main()
