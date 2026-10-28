# credit-service

Credit products microservice (personal loans, business loans and credit cards).
Spring Boot, WebFlux, RxJava 3, MongoDB.

## Requirements
- JDK 17, Maven, MongoDB
- config-server running on :8888
- customer-service running on :8081 (used to validate customers)

## Run
1. Start MongoDB, config-server and customer-service
2. `mvn spring-boot:run`
- Port: 8083 (from config-repo)
- Database: `credit_db`
- Swagger UI: http://localhost:8083/swagger-ui.html

## Business rules
| Product | Rule |
|---|---|
| PERSONAL credit | Personal customers only; max. 1 per customer |
| BUSINESS credit | Business customers only; unlimited |
| CREDIT_CARD | Personal or business customers; unlimited |
| General | A bank account is NOT required to hold a credit product |

Convention: `balance` is the amount owed. Available credit = `creditLimit - balance`.
A charge increases the balance; a payment decreases it.

## Endpoints
| Method | Path | Description |
|---|---|---|
| POST | /credits | Create a credit product |
| GET | /credits | List all credits |
| GET | /credits/{creditNumber} | Find by credit number |
| GET | /credits/customer/{customerDocument} | List credits of a customer |
| PUT | /credits/{creditNumber} | Update the credit limit |
| PATCH | /credits/{creditNumber}/balance | Atomic balance change (used by transaction-service) |
| DELETE | /credits/{creditNumber} | Delete a credit |

## Documentation
See the `docs/` folder: OpenAPI contract and sequence diagram.
