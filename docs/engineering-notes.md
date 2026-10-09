# Day 1 — Engineering Notes

Fill these in **in your own words**. Your reviewer will challenge your reasoning.

## 1. JDK, JRE and JVM
Explain the differences and which is required to compile Java code.
- JDK (Java Development Kit): Contains the tools required to develop, compile and run Java applications, including the Java compiler (javac) and runtime components.
- JRE (Java Runtime Environment): Provides the JVM and supporting libraries required to run Java applications. It doesn't include development tools such as javac.
- JVM (Java Virtual Machine): Executes compiled Java bytecode and manages memory, garbage collection and other runtime operations.
  Key difference: The JDK is used for development, the JRE provides the runtime environment, and the JVM executes the bytecode.

## 2. What happens during `mvn clean verify`?
Name at least four relevant Maven stages and the role of the POM.
When executing mvn clean verify, Maven reads the project's pom.xml, resolves dependencies and executes the configured build lifecycle.
The main stages are:
1. clean: Deletes previous build outputs in target/.
2. validate: Checks project information.
3. compile: Compiles production Java source code.
4. test: Compiles and executes unit tests using a configured test provider such as JUnit.
5. package: Creates a distributable artifact, such as a JAR.
6. verify: Executes additional configured verification checks.
   The pom.xml defines project dependencies, plugins, Java configuration and build settings.
   Important: Maven does not automatically execute the application's main() method during verify.
## 3. Why did we use `long` pence instead of `double` pounds?
Explain precision and trade-offs. What problems might arise with `long`?
We use long to represent money in whole pence because floating-point types such as double cannot represent many decimal fractions exactly.
Using long avoids this type of precision problem when handling amounts in the smallest currency unit.
However, long also has limitations:
- It has a maximum and minimum value.
- Arithmetic overflow can produce incorrect results.
- It cannot directly represent fractional pence.
- It does not identify which currency an amount represents.
  For more complex financial calculations, BigDecimal is often preferable, combined with an explicit currency, scale and rounding policy.
## 4. Why is the exact maximum allowed?
Explain the policy boundary and how your test proves it.
The business requirement specifies that a transfer is allowed when its amount is less than or equal to the configured maximum.
The comparison uses <= instead of < because the maximum is inclusive.
For example, when the maximum is 10,000p:
- 9,999p → allowed
- 10,000p → allowed
- 10,001p → rejected
  The boundary test proves that a transfer exactly equal to the maximum is accepted and helps prevent an off-by-one error.

## 5. What is the difference between input validation and a rejected transfer?
Explain exception for malformed amount versus valid amount above maximum.
Input validation checks whether data meets the method's requirements before applying business logic.
A rejected transfer occurs when the input is valid but does not meet the business rules, such as exceeding the maximum allowed amount.
## 6. What would you improve before this became production code?
Consider currency, domain types, overflow, business rules, reporting and security.
I would improve the implementation in several areas:
- Money representation: Introduce an immutable Money value object containing an amount and currency.
- Overflow protection: Use checked arithmetic such as Math.addExact() when using long, or BigDecimal with explicit precision and rounding rules.
- Business validation: Consider available balance, transaction limits, daily limits, account status and authorization.
- Error handling: Introduce meaningful domain exceptions or rejection results rather than relying entirely on generic exceptions.
- Security: Validate the authenticated user's permission to initiate a transfer.
- Observability: Add structured logging, metrics and appropriate audit records without exposing sensitive information.
- Concurrency: Ensure simultaneous transactions cannot produce inconsistent account balances.
- Testing: Add boundary, concurrency, integration and failure-recovery tests.
  The goal would be to make the component reliable, maintainable, secure and suitable for a production financial application.
