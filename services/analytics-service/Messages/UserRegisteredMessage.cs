namespace Puj.Analytics.Messages;

public record UserRegisteredMessage(
    string   EventId,
    string   EventType,
    string   UserId,
    string   Email,
    string   FirstName,
    string   LastName,
    string   Role,
    DateTime OccurredAt
);
