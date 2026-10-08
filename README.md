# Java Transaction Platform — Week 1, Day 1

Starter repository for an SDET → Java Backend Engineer learning programme.

## Scope

**Today:** JDK, Maven, IntelliJ, Git/GitHub, running a Java entry point, and one small domain-logic exercise. **No Spring Boot yet.**

## Prerequisites

- JDK 25, preferably Eclipse Temurin; language target is Java 21.
- Maven 3.9 or newer.
- Git; IntelliJ IDEA is optional but recommended.
- Internet access on the first Maven build to fetch plugins/JUnit from Maven Central.

## Start

```bash
mvn -version
mvn clean verify
java -cp target/classes io.github.eyupuk.transactions.App
```

Expected console output:

```text
Java transaction platform: ready
```

The **starter project** has one passing `AppTest`. This demonstrates that Maven, Java and JUnit are wired correctly. `TransferPolicy` is intentionally **unfinished**; it is the task for you to implement.

## Today's assignment: TransferPolicy

Open `src/main/java/io/github/eyupuk/transactions/domain/TransferPolicy.java`.

Business requirements:

1. Constructor accepts a configurable *per-transfer maximum* in whole pence.
2. If the maximum is zero or negative, throw `IllegalArgumentException`.
3. Calling `allows(amountPence)` with zero or negative amounts throws `IllegalArgumentException`.
4. Valid amounts less than or **equal to** the maximum return `true`.
5. Valid amounts above the maximum return `false`.
6. There must be no floating-point money representation and no Spring dependencies.

### Add JUnit tests

Create `src/test/java/io/github/eyupuk/transactions/domain/TransferPolicyTest.java`.

Test at least these cases:

| Constructor maximum | Requested amount | Expected |
| ---: | ---: | --- |
| 10_000 | 1 | true |
| 10_000 | 10_000 | true |
| 10_000 | 10_001 | false |
| 10_000 | 0 | IllegalArgumentException |
| 10_000 | -1 | IllegalArgumentException |
| 0 | (constructor only) | IllegalArgumentException |
| -1 | (constructor only) | IllegalArgumentException |

Use descriptive names, e.g. `allowsTransferAtConfiguredLimit`.

**Do not remove, bypass or disable assertions to make the build green.**

Run tests and capture the result:

```bash
mvn clean verify
java -cp target/classes io.github.eyupuk.transactions.App
```

## Git submission

```bash
git init -b main
git add .
git commit -m "Week 1 Day 1: initialise Java transaction platform"
```

On GitHub, create a new EMPTY repository named `java-transaction-platform` (do not add a README or .gitignore in the GitHub UI), then run:

```bash
git remote add origin https://github.com/eyupUK/java-transaction-platform.git
git push -u origin main
```

Adjust the remote username if your GitHub account uses another login. Authenticate using Git Credential Manager, the browser flow, SSH or an appropriate personal access token; do not put passwords/tokens in the repository.

## Acceptance criteria

- [ ] `java -version` and `javac -version` show JDK 25.
- [ ] `mvn -version` is 3.9+ and identifies the correct JDK.
- [ ] IntelliJ can open the existing Maven `pom.xml` project.
- [ ] `mvn clean verify` succeeds *after the assignment* with 8 or more tests total (1 starter test + 7 policy cases).
- [ ] `java -cp target/classes io.github.eyupuk.transactions.App` prints the expected line.
- [ ] `TransferPolicy` uses `long` pence and enforces input validation.
- [ ] `git status --short` is empty after the final commit.
- [ ] GitHub has the source, `README.md` and `pom.xml` but not `target/` or `.idea/`.
- [ ] You can explain in your own words: JDK vs JVM; JAR; `mvn test` vs `mvn package` vs `mvn verify`.

## Reviewer handover

Send your repository URL (or upload the changed source ZIP) and include:

1. Output of `java -version`, `mvn -version`, and `mvn clean verify` (show test totals).
2. `TransferPolicy.java` and `TransferPolicyTest.java`.
3. Your response to the questions in `docs/engineering-notes.md`.

Your mentor will check correctness, boundaries, clarity, maintainability and tests before progressing to Day 2.
# transaction-app
