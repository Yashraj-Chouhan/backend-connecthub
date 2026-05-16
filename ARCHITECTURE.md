# ConnectHub Architecture & Documentation

This document outlines the system architecture, entity relationships, and core class structure for the ConnectHub project.

## 1. System Architecture

ConnectHub is built as a microservices-based system using Spring Cloud. Key components include an API Gateway for request routing and security, a Discovery Server for service registration, and several domain-specific microservices.

```mermaid
graph TD
    Client[Web/Mobile Client] --> Gateway[API Gateway :8080]
    Gateway --> Auth[Auth Service :9002]
    Gateway --> Room[Room Service :9003]
    Gateway --> Message[Message Service :9004]
    Gateway --> Payment[Payment Service :9015]
    
    Auth --- DB_Auth[(MySQL: Auth)]
    Room --- DB_Room[(MySQL: Room)]
    Message --- DB_Msg[(MySQL: Message)]
    Payment --- DB_Pay[(MySQL: Payment)]
    
    Discovery[Eureka Discovery :9000] --- Auth
    Discovery --- Room
    Discovery --- Message
    Discovery --- Payment
    
    Payment -- "CREDIT_TOPUP (Kafka)" --> Auth
    Message -- "NEW_MESSAGE (Kafka)" --> Notification[Notification Service]
```

## 2. Entity Relationship Diagram (ERD)

The core data model revolves around Users, Rooms, Messages, and Payments.

```mermaid
erDiagram
    USER ||--o{ ROOM_MEMBER : participates
    USER ||--o{ MESSAGE : sends
    USER ||--o{ PAYMENT : makes
    ROOM ||--o{ ROOM_MEMBER : has
    ROOM ||--o{ MESSAGE : contains
    
    USER {
        string userId PK
        string username
        string email
        string password
        int translationCredits
    }
    
    ROOM {
        string roomId PK
        string name
        string type
    }
    
    MESSAGE {
        string messageId PK
        string roomId FK
        string userId FK
        string content
        datetime createdAt
    }
    
    PAYMENT {
        string orderId PK
        string userId FK
        double amount
        string status
        datetime createdAt
    }
```

## 3. Core Class Diagram (Payment & Auth)

The following diagram illustrates the key classes involved in the Payment and Auth interaction via Kafka.

```mermaid
classDiagram
    class PaymentService {
        +createOrder(request)
        +verifyPayment(request)
        -kafkaTemplate
    }
    class PaymentController {
        +createOrder()
        +verify()
    }
    class CreditTopupConsumer {
        +consume(event)
    }
    class AuthService {
        +topUpTranslationCredits(userId, credits)
    }
    class CreditTopupEvent {
        +userId
        +credits
        +orderId
    }
    
    PaymentController --> PaymentService : uses
    PaymentService ..> CreditTopupEvent : produces
    CreditTopupConsumer ..> CreditTopupEvent : consumes
    CreditTopupConsumer --> AuthService : updates
```
