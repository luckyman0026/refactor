# Solution Documentation

## Overview

This solution implements a withdrawal processing system with reliable event delivery using the Outbox pattern. The system has been migrated from Java to Kotlin and follows SOLID principles with comprehensive test coverage.

## Architecture
  
### Technology Stack
- **Language**: Kotlin 1.8.22
- **Framework**: Spring Boot 2.4.2
- **Database**: H2 (embedded)
- **Build Tool**: Maven
- **Testing**: JUnit 5, Mockito

### Key Components

1. **Models**: Domain entities (Withdrawal, WithdrawalScheduled, User, PaymentMethod, WithdrawalEventOutbox)
2. **Repositories**: Spring Data JPA repositories for data access
3. **Services**: Business logic layer
   - `WithdrawalService`: Core withdrawal processing logic
   - `WithdrawalProcessingService`: Integration with payment provider
   - `EventsService`: Event publishing to outbox
   - `OutboxProcessor`: Background processor for reliable event delivery
4. **Controllers**: REST API endpoints
5. **Configuration**: Swagger for API documentation

## SOLID Principles Application

### Single Responsibility Principle (SRP)
- **WithdrawalService**: Handles withdrawal creation and status updates
- **EventsService**: Manages event storage to outbox
- **OutboxProcessor**: Processes events from outbox asynchronously
- **WithdrawalProcessingService**: Handles payment provider communication

Each service has a single, well-defined responsibility.

### Open/Closed Principle (OCP)
- Services are designed to be extended without modification
- Event processing can be extended by adding new event types without changing existing code
- Payment provider integration can be swapped by implementing a different strategy

### Liskov Substitution Principle (LSP)
- Repository interfaces can be substituted with different implementations (e.g., for testing)
- Services use dependency injection, allowing for easy substitution

### Interface Segregation Principle (ISP)
- Repository interfaces are focused and specific
- Services depend only on the interfaces they need

### Dependency Inversion Principle (DIP)
- High-level modules (services) depend on abstractions (repositories, other services)
- Dependencies are injected via constructor injection
- This enables easy testing and loose coupling

## Testing Strategy

### Unit Tests
- **WithdrawalServiceTest**: Comprehensive tests for withdrawal processing scenarios
  - Successful withdrawal creation and processing
  - Failed withdrawal handling (TransactionException)
  - Internal error handling
  - Scheduled withdrawal processing
  - Status transitions

- **EventsServiceTest**: Tests for event storage to outbox
- **OutboxProcessorTest**: Tests for event processing and retry logic

### Integration Tests
- **WithdrawalControllerIntegrationTest**: End-to-end tests for REST API
  - Create withdrawal ASAP
  - Create scheduled withdrawal
  - Error handling scenarios
  - Find all withdrawals

### Test Coverage
- Critical business logic (withdrawal processing) has comprehensive coverage
- Different error scenarios are tested
- Integration tests verify the full request/response cycle

## Event Reliability Solution: Outbox Pattern

### Problem Statement
The original implementation had a critical flaw: when a withdrawal status was updated in the database, the event was sent to the message queue. If the message queue was unavailable or the send failed, the event would be lost, violating the requirement for 100% event delivery.

### Solution: Transactional Outbox Pattern

The Outbox pattern ensures that events are never lost by storing them in the database within the same transaction as the business data update.

#### How It Works

1. **Event Storage (Transactional)**
   - When a withdrawal status changes, `EventsService.send()` saves the event to `WithdrawalEventOutbox` table
   - This happens within the **same database transaction** as the withdrawal status update
   - If the transaction commits, both the status update and event are guaranteed to be stored

2. **Asynchronous Processing**
   - `OutboxProcessor` runs every 5 seconds (via `@Scheduled`)
   - It reads pending events from the outbox table
   - Attempts to send each event to the message queue

3. **Retry Mechanism**
   - If sending fails, the event is marked for retry
   - Events are retried up to 3 times
   - After max retries, events are marked as permanently failed (for monitoring/alerting)

4. **Guaranteed Delivery**
   - Since events are stored in the database, they survive application restarts
   - Even if the message queue is down, events will be processed when it comes back online
   - No events are lost between status update and event delivery

#### Implementation Details

**WithdrawalEventOutbox Entity**
```kotlin
- withdrawalId: Links event to withdrawal
- withdrawalType: "WITHDRAWAL" or "WITHDRAWAL_SCHEDULED"
- status: Withdrawal status at time of event
- eventStatus: PENDING, PROCESSING, SENT, FAILED
- retryCount: Number of retry attempts
- errorMessage: Error details for failed events
```

**Transactional Guarantee**
- `EventsService.send()` is marked with `@Transactional`
- `WithdrawalService` uses `TransactionTemplate` for async operations to ensure transactional execution
- Both withdrawal status update and event storage happen atomically

**OutboxProcessor**
- Processes events with status PENDING or FAILED
- Marks events as PROCESSING before sending
- Updates to SENT on success, or increments retry count on failure
- Handles up to 3 retries before marking as permanently failed

### Benefits

1. **100% Event Delivery**: Events are never lost because they're stored in the database
2. **Resilience**: System can recover from message queue outages
3. **Observability**: Failed events are tracked with error messages and retry counts
4. **Scalability**: Can process events in batches or with multiple processors

### Trade-offs

1. **Additional Database Table**: Requires storage for events
2. **Eventual Consistency**: Events are processed asynchronously (typically within 5 seconds)
3. **Duplicate Events**: In a distributed system, events might be processed multiple times (idempotent consumers required)

## Design Decisions

### Kotlin Migration
- Migrated from Java to Kotlin as preferred by the team
- Used data classes for entities (immutability benefits)
- Leveraged Kotlin's null-safety features
- Used Kotlin's concise syntax for cleaner code

### Async Processing
- Withdrawal processing happens asynchronously to avoid blocking the API response
- Uses `ExecutorService` for async execution
- `TransactionTemplate` ensures transactional integrity in async context

### Error Handling
- Different exception types (TransactionException vs generic Exception) result in different status codes
- All status changes trigger events (even failures)
- Events include error information for observability

### API Design
- RESTful endpoints following Spring Boot conventions
- Form-based input for simplicity (could be enhanced with JSON DTOs)
- Swagger documentation for API exploration

## Future Improvements

1. **DTOs**: Replace form parameters with proper DTOs for better validation and type safety
2. **Idempotency**: Add idempotency keys to prevent duplicate withdrawals
3. **Monitoring**: Add metrics and alerts for failed events
4. **Event Schema**: Define proper event schemas for message queue integration
5. **Batch Processing**: Process outbox events in batches for better performance
6. **Dead Letter Queue**: Move permanently failed events to a dead letter queue for manual review

## Running the Application

1. Build: `mvn clean install`
2. Run: `mvn spring-boot:run`
3. API Documentation: http://localhost:7070/swagger-ui.html
4. H2 Console: http://localhost:7070/h2-console (if enabled)

## Testing

Run all tests:
```bash
mvn test
```

Run specific test class:
```bash
mvn test -Dtest=WithdrawalServiceTest
```

