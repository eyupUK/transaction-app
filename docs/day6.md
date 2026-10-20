## Maven Build Engineering & Reusable Java Libraries

#### 1. Maven aggregation vs inheritance
4.5/5 — Very good
Maven aggregation and inheritance are two distinct mechanisms used to manage multi-module projects, often used together but serving completely different structural purposes.
The fundamental difference is their direction of control: aggregation is a top-down relationship used to build multiple modules at once, while inheritance is a bottom-up relationship used to share configuration across modules.

Aggregation (Multi-Module Build)

• The Purpose: It controls the reactor build order. It allows you to run a single Maven command (like mvn clean install) on a parent project and have Maven automatically build all the sub-modules.
• The Configuration: Defined in the parent POM using the <modules> tag. The parent explicitly names its children.
• Direction: Top-Down. The aggregator POM knows about its modules, but the modules do not necessarily need to know about the aggregator.
• Use Case: You have a project split into core, web-ui, and api. You want to build them all simultaneously with one command.

Inheritance (Configuration Sharing)

• The Purpose: It eliminates duplication by sharing POM configuration. It allows child modules to inherit dependencies, plugins, properties, and metadata from a parent POM.
• The Configuration: Defined in the child POM using the <parent> tag. The child explicitly points up to its parent.
• Direction: Bottom-Up. The child POM knows about its parent, but the parent has no idea which or how many children are inheriting from it.
• Use Case: You want 20 different microservices to use the exact same version of Logback, the same Java compiler settings, and the same deployment plugins without copy-pasting them into 20 files.
######   Small correction

You said aggregation controls the reactor build order. More precisely, it defines which modules participate in the build. The reactor then determines their order from actual dependency relationships and other build relationships.
Another nuance: an aggregator POM does not have to be the parent POM inherited by its modules.
Interview-ready answer
Maven aggregation and inheritance are two separate mechanisms that are often used together.
Aggregation uses the modules section to group multiple projects so Maven can build them together using the reactor.
Inheritance uses the parent section to share configuration such as properties, dependency versions and plugin settings.
In my transaction platform project, the root POM serves both purposes. It aggregates transaction-domain and transaction-application while providing common Java and JUnit configurations.
The application also declares an explicit dependency on the domain module, which determines their required build order.

This last example is particularly valuable because it demonstrates hands-on experience.

#### 2. How Maven determines the build order

4.5/5 — Strong
Maven determines the build order using a Directed Acyclic Graph (DAG) managed by the Maven Reactor.
When you execute a build from an aggregator POM, Maven doesn't simply compile modules in the order they are listed in the <modules> section. Instead, it inspects the inter-module dependencies to dynamically arrange them from the most independent module to the most dependent module.

### Interview-ready answer
Maven uses the reactor to collect modules and determine the correct build order based on dependencies between projects.
If the application module depends on the domain module, Maven builds the domain module first, regardless of their order in the modules list.
Maven also considers plugin and build-extension relationships. If no dependency relationship determines the order, it uses the order declared in the parent POM.
In my project, transaction-domain must be built before transaction-application because the application uses the domain library.

Useful command:
```bash
mvn -pl transaction-application -am verify
```
tells Maven to also build required reactor dependencies.

#### 3. Dependencies vs dependencyManagement

4/5 — Correct
The fundamental difference between <dependencies> and <dependencyManagement> is execution: <dependencies> directly adds libraries to your project's runtime or compile-time classpath, while <dependencyManagement> acts purely as a centralized version lookup configuration lookup tool that adds nothing to the classpath on its own.
However, your phrase the fundamental difference is execution is not the best way to describe it.
The distinction is between declaring dependencies and managing dependency configuration.
Furthermore, dependencies are not necessarily added to both compile-time and runtime classpaths. Their availability depends on scope.
For example:
- compile makes a dependency available during compilation and runtime.
- test makes it available to test compilation and execution.
- provided supplies it for compilation while expecting the runtime environment to provide it.
  Also, dependencyManagement manages more than versions; it can specify scopes, exclusions and other dependency metadata.



### Interview-ready answer
The dependencies section declares the libraries a Maven module actually uses, including the appropriate dependency scope.
DependencyManagement centralises versions and other dependency settings but doesn't automatically add those libraries to the module's classpath.
In my multi-module project, the parent POM manages the JUnit version, while transaction-domain and transaction-application each explicitly declare JUnit as a test dependency.
This avoids duplicating version declarations and helps maintain consistency across modules.

### Interview follow-up: What is a BOM?
A Bill of Materials is a POM that centralises compatible dependency versions. Maven can import it into dependencyManagement using a dependency of type pom and scope import.
That's particularly useful in larger enterprise projects.

#### 4. How Maven resolves conflicting transitive dependencies

4.5/5 — Strong
When an application inadvertently pulls in multiple versions of the same library through different dependency paths, Maven resolves the conflict using a deterministic set of rules. It does not look at release dates or highest version numbers. Instead, it uses "Nearest Definition", Declaration Order, and Dependency Management overrides to pick a single winner.
You've correctly identified Maven's three major mechanisms:
- Nearest definition
- First declaration when competing paths are equally near
- Explicit dependency management
  Maven doesn't simply choose the newest version.




### Practical example
```
Application
├── Library A
│   └── Jackson 2.15
└── Library B
└── Jackson 2.17
```


Both versions are at the same dependency depth.
Without an explicit version constraint or management override, declaration order can determine which version Maven selects.
### Interview-ready answer
Maven resolves conflicting transitive dependencies primarily through nearest-definition mediation.
The dependency version closest to the application in the dependency tree normally wins. If versions are at the same depth, the first declared path takes precedence.
We can explicitly manage dependency versions in the parent POM or declare a direct dependency to ensure the intended version is selected.
I would use mvn dependency:tree to investigate conflicts and confirm the resolved versions, then run regression tests to verify compatibility.

Your theoretical understanding here is good.
The next step is learning how to diagnose a real conflict that produces a NoSuchMethodError or NoClassDefFoundError at runtime.

#### 5. Difference between mvn package, install and deploy

######    3/5 — Needs correction

The differences between mvn package, mvn install, and mvn deploy correspond to the progressive stages of the Maven default build lifecycle. Each phase acts as a cumulative step: executing a phase automatically triggers every preceding phase in the lifecycle.
The differences between mvn package, mvn install, and mvn deploy correspond to the progressive stages of the Maven default build lifecycle. Each phase acts as a cumulative step: executing a phase automatically triggers every preceding phase in the lifecycle.
The primary distinction lies in where the compiled project artifact is placed at the end of the operation.

1. mvn package (Artifact Packaging)

• What it does: Compiles your source code, runs unit tests, and compiles the application files into its final distributable archive format based on your POM's configuration (such as a JAR, WAR, or EAR file).
• Where the artifact goes: The generated file is saved exclusively inside your local project's /target directory.
• Scope: It is completely invisible to any other independent Maven projects running on your machine.

2. mvn install (Local Maven Repository)

• What it does: Executes the entire package phase first. Once the JAR/WAR is built, it copies that artifact alongside its pom.xml metadata into your local machine's Maven repository (typically located at ~/.m2/repository).
• Where the artifact goes: Your local ~/.m2/repository directory.
• Scope: It makes the artifact available as a dependency for any other local Maven projects running on the exact same computer. If Project B depends on Project A, running mvn install on Project A allows Project B to resolve it locally.

3. mvn deploy (Remote Artifact Repository)

• What it does: Executes the entire package and install phases first. After updating the local repository, it signs and uploads the final artifact, its javadocs, sources, and POM metadata out of your local machine over the network.
• Where the artifact goes: A shared, remote artifact repository or package registry (such as Sonatype Nexus, JFrog Artifactory, or GitHub Packages) configured in your POM's <distributionManagement> section.
• Scope: Global / Team-wide. It makes your library available to your CI/CD pipelines and all other developers on your team who pull dependencies from that central remote server.

#####    **Three corrections**

Correction 1 — package doesn't guarantee a JAR in target/.
The output depends on project packaging and build configuration.
For example, your root Maven project uses:
`<packaging>pom</packaging`


It doesn't produce a normal application JAR during the package phase.
Correction 2 — deploy doesn't automatically sign artifacts or generate Javadocs and source JARs.
Those operations require appropriate plugin configuration. deploy publishes the project's configured artifacts and metadata; signing and producing additional artifacts are separate build responsibilities.
Correction 3 — install doesn't make the artifact exclusively available in the local repository.
Even before installation, Maven reactor modules can depend on one another within the same multi-module build.
install makes the artifact available for resolution by independent local builds.
These distinctions follow Maven's defined lifecycle phases.

### Improved interview answer
Package, install and deploy are progressive phases of Maven's default lifecycle.
Package creates the artifact according to the project's packaging configuration, such as a JAR or WAR.
Install places the built artifact and its POM in the local Maven repository, allowing other local projects to resolve it as a dependency.
Deploy publishes the artifact to a configured remote artifact repository so other developers and CI pipelines can consume it.
Each phase runs the preceding lifecycle phases. Additional tasks such as signing artifacts or publishing source and Javadoc JARs require corresponding plugin configurations.

This is much more effective than your original multi-paragraph explanation.

#### 6. Normal JAR vs executable fat JAR

4/5 — Good
Correction 1 — A normal JAR can also be executable
A JAR doesn't have to be fat to run using:
```bash
java -jar application.jar
```


A small application with no external dependencies can run as a normal JAR if it has the required Main-Class manifest entry.
A normal JAR with dependencies can also be executable when its runtime dependencies are made available appropriately.
Correction 2 — Not every fat JAR uses the same packaging technique

###### **Two common approaches are:**

```text
Approach	                Packaging
Shaded JAR	                Combines application classes and dependency classes into one archive
Spring Boot executable JAR	Commonly stores application classes and nested dependency JARs separately
```
For Spring Boot, the manifest normally identifies a launcher class using Main-Class, and the application entry point using Start-Class.

###### Example:

Main-Class: org.springframework.boot.loader.launch.JarLauncher
Start-Class: com.example.TransactionApplication

The JVM starts the launcher, which loads the application's nested dependencies and invokes the application.
### Improved interview answer
A normal JAR usually contains the compiled classes and resources of one module. Dependencies are generally supplied separately through the runtime classpath.
A fat or executable JAR packages the application together with its runtime dependencies, simplifying deployment.
Different tools use different packaging strategies. For example, Maven Shade can combine classes into one archive, while Spring Boot typically packages dependencies as nested JARs and uses a launcher.
Both approaches still require a compatible Java runtime.
In my current Maven project, transaction-domain produces a normal reusable library JAR. Later, when we introduce Spring Boot, we'll package the backend as an executable application.

That last sentence demonstrates how you understand the architectural difference through your project.

#### 7. How would you safely upgrade a shared Java library used by 20 applications?

######    3/5 — Correct direction, missing implementation

This is the most important question for a Developer Platform Engineer.
Your strategy identifies three sensible principles: backward compatibility, downstream testing and gradual adoption.
However, I'd challenge three details.
First, you cannot guarantee binary and semantic compatibility merely by planning for it. You need explicit compatibility tests and evidence. Some upgrades will intentionally contain breaking changes.
Second, consumer-driven contract testing is not necessarily the primary tool for a shared Java library. Pact-style contract tests are particularly useful for service interactions. For a Java library, API and binary compatibility analysis, consumer compilation and integration tests are usually more direct.
Third, your answer needs a release, rollout and rollback procedure.

######    Recommended upgrade strategy

1. Assess compatibility
   Identify public API changes, removed methods, changed signatures, behaviour changes and dependency upgrades. Use tools such as Revapi or japicmp for Java API compatibility analysis.
2. Build and publish a candidate
   Produce a versioned artifact in a controlled repository, document the changes and run the library's own unit and integration tests.
3. Validate representative consumers
   Compile and test applications that use different parts of the library. Check dependency conflicts and important runtime behaviours.
4. Roll out incrementally
   Start with one or two low-risk applications, monitor results, then expand adoption. Allow applications to upgrade on independent release schedules.
5. Monitor and preserve rollback
   Track failures and regressions. Keep the previous stable library available so affected applications can restore the previous dependency version and redeploy if necessary.

### Improved interview answer
I would start by assessing the compatibility impact of the proposed library changes, including public APIs, binary compatibility, behaviour changes and transitive dependencies.
Before publishing, I'd run the library tests and compatibility analysis, then validate representative consuming applications through compilation and integration tests.
I'd publish a versioned release to our artifact repository with clear release notes and avoid forcing all 20 applications to upgrade simultaneously.
I'd initially roll out to a small number of low-risk consumers, monitor errors and performance, and progressively expand adoption.
If problems occur, the affected applications could return to the previous stable library version.
For breaking changes, I'd use an appropriate versioning strategy and potentially provide a compatibility layer or migration period.

This is the strongest answer to practise for a developer-platform interview.
It shows that you understand not just Maven, but also how platform engineering affects multiple application teams.