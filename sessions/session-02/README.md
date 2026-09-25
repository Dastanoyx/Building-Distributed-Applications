# Session 02 — Spring Boot services: layering, REST and error contracts

**Goal of the lab:** Build the catalog service's web and application layers, with DTOs, validation and one error shape.

## Code to read

| File | Why |
|---|---|
| `orderflow/catalog-service/.../product/Product.java` | a domain object with behaviour: no setter for stock |
| `orderflow/catalog-service/.../product/ProductController.java` | status codes, Location header, pagination |
| `orderflow/catalog-service/.../product/dto/` | records at the boundary — never the entity |
| `orderflow/catalog-service/.../shared/ApiExceptionHandler.java` | RFC 9457 ProblemDetail, one shape for the whole platform |
| `orderflow/catalog-service/src/test/.../ProductControllerTest.java` | @WebMvcTest: the web slice only, mocked service |

## What you build

Rebuild `Product`, `ProductService`, `ProductController` and the exception handler from scratch; compare with the reference afterwards.

## Commands

```bash
mvn -pl catalog-service -am test
mvn -pl catalog-service -am spring-boot:run
curl -i -X POST localhost:8081/api/products -H 'Content-Type: application/json' -d '{"sku":"bad","name":"","price":0,"stock":-1}'
```

## It works when

- creating a product returns 201 with a Location header
- an invalid payload returns 400 problem+json listing every bad field
- the service is never reached when validation fails (`verifyNoInteractions`)

---

Statements and solutions: `Session_02_Exercises.pdf` and `Session_02_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).
