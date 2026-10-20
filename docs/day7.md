## Understand SOLID in backend Java

```text
SOLID is a set of five object-oriented design principles that help manage dependencies, responsibilities and change.
Principle	                Meaning	Example in your project 
S — Single Responsibility	A component should have one principal reason to change	Money handles monetary rules, not database connections
O — Open/Closed	            Extend behaviour without continually modifying stable components	Replace an in-memory repository with a database adapter
L — Liskov Substitution	    Implementations must honour the contract of their abstractions	Every repository implementation must respect the same update rules
I — Interface Segregation	Clients shouldn't depend on methods they don't need	A balance reader shouldn't require unrelated payment-processing methods
D — Dependency Inversion	High-level policy should depend on abstractions, not low-level implementation details	AccountService depends on AccountRepository, not ConcurrentHashMap
```