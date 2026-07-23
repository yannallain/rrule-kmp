import Foundation
import XCTest
import RRuleKit

/// Compiles and exercises the API that native Swift consumers are expected to use.
final class PublicApiCompatibilityTests: XCTestCase {
    func testScheduleAndIntervalSurface() throws {
        let first = Date(timeIntervalSince1970: 1_704_067_200)
        let second = first.addingTimeInterval(86_400)
        let end = second.addingTimeInterval(86_400)
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART:20240101T000000Z
            RRULE:FREQ=DAILY;COUNT=2
            """
        )

        requireSendable(schedule)
        XCTAssertEqual(RecurrenceSchedule.maximumResultCount, 100_000)
        XCTAssertNil(schedule.timeZoneIdentifier)

        XCTAssertEqual(
            try schedule.occurrences(fromInclusive: first, toExclusive: end),
            [first, second]
        )
        XCTAssertEqual(
            try schedule.occurrences(
                fromInclusive: first,
                toExclusive: end,
                maximumCount: 2
            ),
            [first, second]
        )

        XCTAssertEqual(try schedule.nextOccurrence(after: first), second)
        XCTAssertEqual(
            try schedule.nextOccurrence(after: first, inclusive: true),
            first
        )
        XCTAssertEqual(try schedule.previousOccurrence(before: second), first)
        XCTAssertEqual(
            try schedule.previousOccurrence(before: second, inclusive: true),
            second
        )

        let query = first..<first.addingTimeInterval(7_200)
        let intervals = try schedule.intervals(
            overlapping: query,
            elapsedDurationSeconds: 3_600
        )
        let explicitlyBoundedIntervals = try schedule.intervals(
            overlapping: query,
            elapsedDurationSeconds: 3_600,
            maximumCount: 2
        )
        XCTAssertEqual(intervals, explicitlyBoundedIntervals)

        let interval = try XCTUnwrap(intervals.first)
        requireEquatable(interval)
        requireSendable(interval)
        XCTAssertEqual(interval.start, first)
        XCTAssertEqual(interval.endExclusive, first.addingTimeInterval(3_600))
        XCTAssertEqual(interval.duration, 3_600)

        let overlap = first.addingTimeInterval(1_800)..<first.addingTimeInterval(7_200)
        XCTAssertTrue(interval.overlaps(overlap))
        XCTAssertEqual(
            interval.intersection(with: overlap),
            first.addingTimeInterval(1_800)..<first.addingTimeInterval(3_600)
        )
        XCTAssertEqual(interval.overlapDuration(with: overlap), 1_800)

        let adjacent = interval.endExclusive..<interval.endExclusive.addingTimeInterval(1)
        XCTAssertFalse(interval.overlaps(adjacent))
        XCTAssertNil(interval.intersection(with: adjacent))
        XCTAssertEqual(interval.overlapDuration(with: adjacent), 0)
    }

    func testPoliciesAndDiagnosticsSurface() throws {
        let policies: [AmbiguousTimePolicy] = [.earlier, .later]
        policies.forEach(requireSendable)
        XCTAssertEqual(policies.map(policyName), ["earlier", "later"])

        let zonedSchedule = try RecurrenceSchedule(
            content: """
            DTSTART;TZID=Europe/Paris:20241027T023000
            RRULE:FREQ=DAILY;COUNT=1
            """,
            ambiguousTimePolicy: .later
        )
        XCTAssertEqual(zonedSchedule.timeZoneIdentifier, "Europe/Paris")

        let minimalDiagnostic = RRuleDiagnostic(message: "Minimal diagnostic")
        XCTAssertNil(minimalDiagnostic.inputValue)
        XCTAssertNil(minimalDiagnostic.reason)
        XCTAssertNil(minimalDiagnostic.propertyName)
        XCTAssertNil(minimalDiagnostic.invalidToken)
        XCTAssertNil(minimalDiagnostic.position)

        let diagnostic = RRuleDiagnostic(
            message: "Invalid frequency",
            inputValue: "RRULE:FREQ=INVALID",
            reason: "MALFORMED_TOKEN",
            propertyName: "FREQ",
            invalidToken: "INVALID",
            position: 11
        )
        requireEquatable(diagnostic)
        requireSendable(diagnostic)
        XCTAssertEqual(diagnostic.message, "Invalid frequency")
        XCTAssertEqual(diagnostic.inputValue, "RRULE:FREQ=INVALID")
        XCTAssertEqual(diagnostic.reason, "MALFORMED_TOKEN")
        XCTAssertEqual(diagnostic.propertyName, "FREQ")
        XCTAssertEqual(diagnostic.invalidToken, "INVALID")
        XCTAssertEqual(diagnostic.position, 11)
        XCTAssertEqual(diagnostic, diagnostic)

        let errors: [RRuleError] = [
            .invalidContent(diagnostic),
            .invalidArgument("maximumCount must be positive"),
            .evaluationFailed(diagnostic),
            .resultLimitExceeded(maximumCount: 10),
        ]
        errors.forEach(assertErrorSurface)
    }

    private func policyName(_ policy: AmbiguousTimePolicy) -> String {
        switch policy {
        case .earlier:
            return "earlier"
        case .later:
            return "later"
        @unknown default:
            return "unknown"
        }
    }

    private func assertErrorSurface(_ error: RRuleError) {
        requireError(error)
        requireLocalizedError(error)
        requireEquatable(error)
        requireSendable(error)
        XCTAssertEqual(error, error)
        XCTAssertNotNil(error.errorDescription)

        switch error {
        case .invalidContent(let diagnostic):
            XCTAssertEqual(diagnostic.reason, "MALFORMED_TOKEN")
        case .invalidArgument(let message):
            XCTAssertEqual(message, "maximumCount must be positive")
        case .evaluationFailed(let diagnostic):
            XCTAssertEqual(diagnostic.propertyName, "FREQ")
        case .resultLimitExceeded(maximumCount: let maximumCount):
            XCTAssertEqual(maximumCount, 10)
        @unknown default:
            XCTFail("Unexpected RRuleError case")
        }
    }
}

private func requireEquatable<T: Equatable>(_: T) {}

private func requireError<T: Error>(_: T) {}

private func requireLocalizedError<T: LocalizedError>(_: T) {}

private func requireSendable<T: Sendable>(_: T) {}
