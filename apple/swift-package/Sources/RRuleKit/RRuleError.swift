import Foundation

/// Machine-readable context for a recurrence parse or evaluation failure.
public struct RRuleDiagnostic: Equatable, Sendable {
    /// Human-readable description suitable for logs and error UI.
    public let message: String

    /// Complete value supplied to the Kotlin parser, when available.
    public let inputValue: String?

    /// Stable RFC parser or validator reason, such as `MALFORMED_TOKEN`.
    public let reason: String?

    /// Content or rule property that failed, such as `DTSTART` or `BYDAY`.
    public let propertyName: String?

    /// Exact token rejected by the parser or validator.
    public let invalidToken: String?

    /// Zero-based source position in `inputValue`, when available.
    public let position: Int?

    public init(
        message: String,
        inputValue: String? = nil,
        reason: String? = nil,
        propertyName: String? = nil,
        invalidToken: String? = nil,
        position: Int? = nil
    ) {
        self.message = message
        self.inputValue = inputValue
        self.reason = reason
        self.propertyName = propertyName
        self.invalidToken = invalidToken
        self.position = position
    }
}

/// Errors reported by the native Swift recurrence facade.
public enum RRuleError: Error, Equatable, Sendable {
    /// The supplied recurrence content could not be parsed or resolved.
    case invalidContent(RRuleDiagnostic)

    /// A query argument violates the facade contract.
    case invalidArgument(String)

    /// A parsed recurrence could not be evaluated.
    case evaluationFailed(RRuleDiagnostic)

    /// More matching results exist than the caller allowed the query to return.
    case resultLimitExceeded(maximumCount: Int)
}

extension RRuleError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .invalidContent(let diagnostic),
             .evaluationFailed(let diagnostic):
            return diagnostic.message
        case .invalidArgument(let message):
            return message
        case .resultLimitExceeded(let maximumCount):
            return "More than \(maximumCount) results match the requested window"
        }
    }
}
