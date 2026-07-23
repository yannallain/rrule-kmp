import Foundation
import RRuleKmpCore

/// A parsed UTC or `TZID`-bearing recurrence that produces absolute Foundation dates.
public final class RecurrenceSchedule: @unchecked Sendable {
    /// Largest collection that one native query may materialize. Split larger time windows.
    public static let maximumResultCount = 100_000

    private let core: AppleRecurrenceSchedule

    /// The `TZID` declared by `DTSTART`, or `nil` for a UTC start.
    public var timeZoneIdentifier: String? {
        core.timeZoneIdentifier
    }

    /// Parses recurrence content using an explicit daylight-saving overlap policy.
    public init(
        content: String,
        ambiguousTimePolicy: AmbiguousTimePolicy = .earlier
    ) throws {
        do {
            core = try AppleRecurrenceParser.shared.parse(
                content: content,
                preferLaterOffsetAtOverlap: ambiguousTimePolicy == .later
            )
        } catch {
            throw Self.mappedError(error, fallback: .invalidContent(Self.diagnostic(from: error)))
        }
    }

    /// Returns every start in `[fromInclusive, toExclusive)` when it fits `maximumCount`.
    ///
    /// Throws ``RRuleError/resultLimitExceeded(maximumCount:)`` instead of returning an
    /// incomplete result when more starts match the requested window.
    public func occurrences(
        fromInclusive: Date,
        toExclusive: Date,
        maximumCount: Int = 1_000
    ) throws -> [Date] {
        let count = try Self.validatedMaximumCount(maximumCount)
        do {
            return try core.occurrences(
                fromInclusive: fromInclusive,
                toExclusive: toExclusive,
                maximumCount: count
            )
        } catch {
            throw Self.mappedError(error, fallback: .evaluationFailed(Self.diagnostic(from: error)))
        }
    }

    /// Returns the next occurrence relative to `date`.
    public func nextOccurrence(
        after date: Date,
        inclusive: Bool = false
    ) throws -> Date? {
        do {
            return try core.nextOccurrence(after: date, inclusive: inclusive)
        } catch {
            throw Self.mappedError(error, fallback: .evaluationFailed(Self.diagnostic(from: error)))
        }
    }

    /// Returns the previous occurrence relative to `date`.
    public func previousOccurrence(
        before date: Date,
        inclusive: Bool = false
    ) throws -> Date? {
        do {
            return try core.previousOccurrence(before: date, inclusive: inclusive)
        } catch {
            throw Self.mappedError(error, fallback: .evaluationFailed(Self.diagnostic(from: error)))
        }
    }

    /// Returns half-open elapsed-duration intervals overlapping `window`.
    ///
    /// Starts before the window are queried when their interval can overlap it. Each end is
    /// computed by adding exactly `elapsedDurationSeconds`, including across DST transitions.
    public func intervals(
        overlapping window: Range<Date>,
        elapsedDurationSeconds: Int64,
        maximumCount: Int = 1_000
    ) throws -> [RecurrenceInterval] {
        guard elapsedDurationSeconds > 0 else {
            throw RRuleError.invalidArgument("elapsedDurationSeconds must be positive")
        }
        let count = try Self.validatedMaximumCount(maximumCount)
        let result: AppleRecurrenceIntervalQueryResult
        do {
            result = try core.intervals(
                overlappingStartInclusive: window.lowerBound,
                endExclusive: window.upperBound,
                elapsedDurationSeconds: elapsedDurationSeconds,
                maximumCount: count
            )
        } catch {
            throw Self.mappedError(error, fallback: .evaluationFailed(Self.diagnostic(from: error)))
        }
        guard !result.hasMore else {
            throw RRuleError.resultLimitExceeded(maximumCount: maximumCount)
        }
        return result.intervals.map { interval in
            RecurrenceInterval(start: interval.start, endExclusive: interval.endExclusive)
        }
    }

    private static func validatedMaximumCount(_ value: Int) throws -> Int32 {
        guard value > 0, value <= maximumResultCount else {
            throw RRuleError.invalidArgument(
                "maximumCount must be between 1 and \(maximumResultCount)"
            )
        }
        return Int32(value)
    }

    private static func mappedError(_ error: Error, fallback: RRuleError) -> RRuleError {
        guard let coreError = (error as NSError).kotlinException as? AppleRecurrenceException else {
            return fallback
        }
        let diagnostic = Self.diagnostic(from: error, coreError: coreError)
        if coreError.code === AppleRecurrenceErrorCode.invalidArgument {
            return .invalidArgument(diagnostic.message)
        }
        if coreError.code === AppleRecurrenceErrorCode.resultLimitExceeded {
            let maximumCount = coreError.maximumCount?.intValue ?? 0
            return .resultLimitExceeded(maximumCount: maximumCount)
        }
        if coreError.code === AppleRecurrenceErrorCode.invalidContent ||
            coreError.code === AppleRecurrenceErrorCode.unsupportedTemporalDomain {
            return .invalidContent(diagnostic)
        }
        return .evaluationFailed(diagnostic)
    }

    private static func message(from error: Error) -> String {
        (error as NSError).localizedDescription
    }

    private static func diagnostic(
        from error: Error,
        coreError: AppleRecurrenceException? = nil
    ) -> RRuleDiagnostic {
        RRuleDiagnostic(
            message: Self.message(from: error),
            inputValue: coreError?.inputValue,
            reason: coreError?.reason,
            propertyName: coreError?.propertyName,
            invalidToken: coreError?.invalidToken,
            position: coreError?.position?.intValue
        )
    }
}
