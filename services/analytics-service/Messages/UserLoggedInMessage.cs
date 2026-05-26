namespace Puj.Analytics.Messages;

public record UserLoggedInMessage(
    string   EventId,
    string   EventType,
    string   UserId,
    string   Email,
    string   Role,
    DateTime OccurredAt
);
