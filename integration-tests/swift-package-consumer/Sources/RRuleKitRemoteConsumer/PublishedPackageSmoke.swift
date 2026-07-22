import Foundation
import RRuleKit

/// Compiles a real call through the remotely resolved Swift package product.
public func publishedPackageOccurrence() throws -> Date? {
    let schedule = try RecurrenceSchedule(
        content: """
        DTSTART:20260722T090000Z
        RRULE:FREQ=DAILY;COUNT=2
        """
    )
    return try schedule.nextOccurrence(
        after: Date(timeIntervalSince1970: 1_774_173_600),
        inclusive: true
    )
}
