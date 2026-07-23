import Foundation
import Dispatch
import XCTest
import RRuleKit

final class RecurrenceScheduleTests: XCTestCase {
    func testParsesZonedContentAndReturnsFoundationDates() throws {
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART;TZID=Europe/Paris:20240330T210000
            RRULE:FREQ=DAILY;COUNT=3
            """
        )

        let occurrences = try schedule.occurrences(
            fromInclusive: date("2024-03-30T00:00:00Z"),
            toExclusive: date("2024-04-02T00:00:00Z"),
            maximumCount: 10
        )

        XCTAssertEqual(schedule.timeZoneIdentifier, "Europe/Paris")
        XCTAssertEqual(
            occurrences,
            [
                date("2024-03-30T20:00:00Z"),
                date("2024-03-31T19:00:00Z"),
                date("2024-04-01T19:00:00Z"),
            ]
        )
    }

    func testElapsedIntervalsLookBackAndRemainExactAcrossDST() throws {
        let spring = try schedule(start: "20240330T210000")
        let springIntervals = try spring.intervals(
            overlapping: date("2024-03-31T04:30:00Z")..<date("2024-03-31T06:00:00Z"),
            elapsedDurationSeconds: 32_400
        )

        XCTAssertEqual(springIntervals.count, 1)
        XCTAssertEqual(springIntervals[0].start, date("2024-03-30T20:00:00Z"))
        XCTAssertEqual(springIntervals[0].endExclusive, date("2024-03-31T05:00:00Z"))
        XCTAssertEqual(springIntervals[0].duration, 32_400)
        let attendanceWindow = date("2024-03-31T04:30:00Z")..<date("2024-03-31T06:00:00Z")
        XCTAssertEqual(
            springIntervals[0].intersection(with: attendanceWindow),
            date("2024-03-31T04:30:00Z")..<date("2024-03-31T05:00:00Z")
        )
        XCTAssertEqual(springIntervals[0].overlapDuration(with: attendanceWindow), 1_800)
        XCTAssertFalse(
            springIntervals[0].overlaps(
                springIntervals[0].endExclusive..<date("2024-03-31T06:00:00Z")
            )
        )
        XCTAssertNil(
            springIntervals[0].intersection(
                with: springIntervals[0].endExclusive..<date("2024-03-31T06:00:00Z")
            )
        )

        let fall = try schedule(start: "20241026T210000")
        let fallInterval = try XCTUnwrap(
            fall.intervals(
                overlapping: date("2024-10-26T18:00:00Z")..<date("2024-10-27T06:00:00Z"),
                elapsedDurationSeconds: 32_400
            ).first
        )
        XCTAssertEqual(fallInterval.start, date("2024-10-26T19:00:00Z"))
        XCTAssertEqual(fallInterval.endExclusive, date("2024-10-27T04:00:00Z"))
        XCTAssertEqual(fallInterval.duration, 32_400)
    }

    func testOverlapPolicySelectsTheRequestedInstant() throws {
        let content = """
        DTSTART;TZID=Europe/Paris:20241027T023000
        RRULE:FREQ=DAILY;COUNT=1
        """
        let earlier = try RecurrenceSchedule(content: content, ambiguousTimePolicy: .earlier)
        let later = try RecurrenceSchedule(content: content, ambiguousTimePolicy: .later)

        XCTAssertEqual(
            try earlier.nextOccurrence(after: date("2024-10-26T00:00:00Z")),
            date("2024-10-27T00:30:00Z")
        )
        XCTAssertEqual(
            try later.nextOccurrence(after: date("2024-10-26T00:00:00Z")),
            date("2024-10-27T01:30:00Z")
        )
    }

    func testMalformedContentIsACatchableSwiftError() {
        XCTAssertThrowsError(
            try RecurrenceSchedule(
                content: """
                DTSTART;TZID=Europe/Paris:20240330T210000
                RRULE:FREQ=NOT_A_FREQUENCY
                """
            )
        ) { error in
            guard case RRuleError.invalidContent(let diagnostic) = error else {
                return XCTFail("Unexpected error: \(error)")
            }
            XCTAssertTrue(diagnostic.message.contains("NOT_A_FREQUENCY"))
            XCTAssertEqual(diagnostic.reason, "MALFORMED_TOKEN")
            XCTAssertEqual(diagnostic.propertyName, "FREQ")
            XCTAssertEqual(diagnostic.invalidToken, "NOT_A_FREQUENCY")
            XCTAssertNotNil(diagnostic.position)
            XCTAssertTrue(diagnostic.inputValue?.contains("RRULE:FREQ=NOT_A_FREQUENCY") == true)
        }
    }

    func testMultipleSchedulesStayIndependent() throws {
        let first = try schedule(start: "20240330T210000")
        let second = try schedule(start: "20240330T220000")
        let windowStart = date("2024-03-30T00:00:00Z")
        let windowEnd = date("2024-03-31T23:00:00Z")

        XCTAssertNotEqual(
            try first.occurrences(fromInclusive: windowStart, toExclusive: windowEnd),
            try second.occurrences(fromInclusive: windowStart, toExclusive: windowEnd)
        )
    }

    func testEmptyWindowAndInvalidLimitAreHandledInSwift() throws {
        let schedule = try schedule(start: "20240330T210000")
        let boundary = date("2024-03-31T00:00:00Z")

        XCTAssertEqual(
            try schedule.occurrences(fromInclusive: boundary, toExclusive: boundary),
            []
        )
        XCTAssertThrowsError(
            try schedule.occurrences(
                fromInclusive: boundary,
                toExclusive: date("2024-04-01T00:00:00Z"),
                maximumCount: 0
            )
        ) { error in
            guard case RRuleError.invalidArgument = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
        XCTAssertThrowsError(
            try schedule.occurrences(
                fromInclusive: boundary,
                toExclusive: date("2024-04-01T00:00:00Z"),
                maximumCount: RecurrenceSchedule.maximumResultCount + 1
            )
        ) { error in
            guard case RRuleError.invalidArgument = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    func testIntervalLimitCannotSilentlyTruncatePayrollResults() throws {
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART:20240101T000000Z
            RRULE:FREQ=SECONDLY;COUNT=3
            """
        )

        XCTAssertThrowsError(
            try schedule.intervals(
                overlapping: date("2024-01-01T00:00:00Z")..<date("2024-01-01T00:01:00Z"),
                elapsedDurationSeconds: 1,
                maximumCount: 1
            )
        ) { error in
            XCTAssertEqual(error as? RRuleError, .resultLimitExceeded(maximumCount: 1))
        }
    }

    func testOccurrenceLimitCannotSilentlyTruncateResults() throws {
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART:20240101T000000Z
            RRULE:FREQ=SECONDLY;COUNT=3
            """
        )

        XCTAssertThrowsError(
            try schedule.occurrences(
                fromInclusive: date("2024-01-01T00:00:00Z"),
                toExclusive: date("2024-01-01T00:01:00Z"),
                maximumCount: 1
            )
        ) { error in
            XCTAssertEqual(error as? RRuleError, .resultLimitExceeded(maximumCount: 1))
        }
    }

    func testUtcRDateExDateAndExhaustedQueries() throws {
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART:20240101T000000Z
            RRULE:FREQ=DAILY;COUNT=3
            RDATE:20240110T000000Z
            EXDATE:20240102T000000Z
            """
        )

        XCTAssertNil(schedule.timeZoneIdentifier)
        XCTAssertEqual(
            try schedule.occurrences(
                fromInclusive: date("2024-01-01T00:00:00Z"),
                toExclusive: date("2024-01-11T00:00:00Z")
            ),
            [
                date("2024-01-01T00:00:00Z"),
                date("2024-01-03T00:00:00Z"),
                date("2024-01-10T00:00:00Z"),
            ]
        )
        XCTAssertNil(try schedule.nextOccurrence(after: date("2024-01-11T00:00:00Z")))
        XCTAssertNil(try schedule.previousOccurrence(before: date("2023-12-31T00:00:00Z")))
    }

    func testUnsupportedAndUnknownTimeZonesAreCatchable() {
        XCTAssertThrowsError(
            try RecurrenceSchedule(
                content: """
                DTSTART:20240330T210000
                RRULE:FREQ=DAILY
                """
            )
        ) { error in
            guard case RRuleError.invalidContent = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
        XCTAssertThrowsError(
            try RecurrenceSchedule(
                content: """
                DTSTART;TZID=Not/A_Time_Zone:20240330T210000
                RRULE:FREQ=DAILY
                """
            )
        ) { error in
            guard case RRuleError.invalidContent = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    func testGeneratedOccurrenceInDSTGapIsSkipped() throws {
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART;TZID=Europe/Paris:20240330T023000
            RRULE:FREQ=DAILY;COUNT=3
            """
        )

        XCTAssertEqual(
            try schedule.occurrences(
                fromInclusive: date("2024-03-30T00:00:00Z"),
                toExclusive: date("2024-04-03T00:00:00Z")
            ),
            [
                date("2024-03-30T01:30:00Z"),
                date("2024-04-01T00:30:00Z"),
                date("2024-04-02T00:30:00Z"),
            ]
        )
    }

    func testNonFiniteDateIsACatchableInvalidArgument() throws {
        let schedule = try self.schedule(start: "20240330T210000")

        XCTAssertThrowsError(
            try schedule.occurrences(
                fromInclusive: Date(timeIntervalSinceReferenceDate: .nan),
                toExclusive: date("2024-04-01T00:00:00Z")
            )
        ) { error in
            guard case RRuleError.invalidArgument = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    func testFractionalBoundaryAndExtremeDurationStaySafe() throws {
        let schedule = try RecurrenceSchedule(
            content: """
            DTSTART:20010101T000000Z
            RRULE:FREQ=DAILY;COUNT=1
            """
        )
        XCTAssertTrue(
            try schedule.occurrences(
                fromInclusive: Date(timeIntervalSinceReferenceDate: 0.000_000_010),
                toExclusive: Date(timeIntervalSinceReferenceDate: 1.0),
                maximumCount: 1
            ).isEmpty
        )
        XCTAssertThrowsError(
            try schedule.intervals(
                overlapping: Date(timeIntervalSinceReferenceDate: 0.0)..<Date(timeIntervalSinceReferenceDate: 1.0),
                elapsedDurationSeconds: Int64.max,
                maximumCount: 1
            )
        ) { error in
            guard case RRuleError.invalidArgument = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    func testScheduleSupportsConcurrentBoundedQueries() throws {
        let schedule = try self.schedule(start: "20240330T210000")
        let failures = FailureCollector()
        let start = date("2024-03-30T00:00:00Z")
        let end = date("2024-04-02T00:00:00Z")

        DispatchQueue.concurrentPerform(iterations: 32) { _ in
            do {
                let values = try schedule.occurrences(
                    fromInclusive: start,
                    toExclusive: end,
                    maximumCount: 10
                )
                if values.count != 3 {
                    failures.append("Expected 3 values, received \(values.count)")
                }
            } catch {
                failures.append(String(describing: error))
            }
        }

        XCTAssertEqual(failures.values, [])
    }

    private func schedule(start: String) throws -> RecurrenceSchedule {
        try RecurrenceSchedule(
            content: """
            DTSTART;TZID=Europe/Paris:\(start)
            RRULE:FREQ=DAILY;COUNT=3
            """
        )
    }

    private func date(_ value: String) -> Date {
        guard let date = ISO8601DateFormatter().date(from: value) else {
            fatalError("Invalid test date: \(value)")
        }
        return date
    }
}

private final class FailureCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [String] = []

    var values: [String] {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }

    func append(_ value: String) {
        lock.lock()
        storage.append(value)
        lock.unlock()
    }
}
