import Foundation

/// A half-open recurrence interval whose end is not part of the interval.
public struct RecurrenceInterval: Equatable, Sendable {
    /// The first instant contained by the interval.
    public let start: Date

    /// The first instant after the interval.
    public let endExclusive: Date

    /// The exact elapsed duration represented by this interval.
    public var duration: TimeInterval {
        endExclusive.timeIntervalSince(start)
    }

    internal init(start: Date, endExclusive: Date) {
        self.start = start
        self.endExclusive = endExclusive
    }

    /// Returns whether this interval and the half-open `window` share positive elapsed time.
    public func overlaps(_ window: Range<Date>) -> Bool {
        start < window.upperBound && endExclusive > window.lowerBound
    }

    /// Returns the half-open portion shared with `window`, or `nil` when they do not overlap.
    public func intersection(with window: Range<Date>) -> Range<Date>? {
        let intersectionStart = max(start, window.lowerBound)
        let intersectionEnd = min(endExclusive, window.upperBound)
        guard intersectionStart < intersectionEnd else {
            return nil
        }
        return intersectionStart..<intersectionEnd
    }

    /// Returns the positive elapsed time shared with `window`, in seconds.
    public func overlapDuration(with window: Range<Date>) -> TimeInterval {
        guard let intersection = intersection(with: window) else {
            return 0
        }
        return intersection.upperBound.timeIntervalSince(intersection.lowerBound)
    }
}
