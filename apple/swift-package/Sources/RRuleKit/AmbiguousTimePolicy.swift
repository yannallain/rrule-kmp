/// Selects which instant represents a local time repeated during a daylight-saving overlap.
public enum AmbiguousTimePolicy: Sendable {
    /// Uses the first occurrence of the repeated local time.
    case earlier

    /// Uses the second occurrence of the repeated local time.
    case later
}
