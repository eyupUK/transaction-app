# Day 1 — Engineering Notes

Fill these in **in your own words**. Your reviewer will challenge your reasoning.

## 1. JDK, JRE and JVM
Explain the differences and which is required to compile Java code.
JDK stands for Java Development Kit, which includes compiler, debugger... etc.
JRE stands for Java Runtime Environment, which contains required libraries.
JVM stands for Java Virtual Machine is responsible for providing a virtual machine runs apps operating system independent

## 2. What happens during `mvn clean verify`?
Name at least four relevant Maven stages and the role of the POM.
It starts scanning for project, building jar, loading pre-fixes, deleting ald target folder, recompiling sources and runs executables

## 3. Why did we use `long` pence instead of `double` pounds?
Explain precision and trade-offs. What problems might arise with `long`?

## 4. Why is the exact maximum allowed?
Explain the policy boundary and how your test proves it.

## 5. What is the difference between input validation and a rejected transfer?
Explain exception for malformed amount versus valid amount above maximum.
Invalid amounts like equal or smaller than 0 should not be initialized as max amount in the constructor. Otherwise, allows() always returns false running unnecessarily.

## 6. What would you improve before this became production code?
Consider currency, domain types, overflow, business rules, reporting and security.
