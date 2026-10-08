package io.github.eyupuk.transactions;

/**
 * Day 1 entry point. Keep the application independent of any IDE tooling.
 */
public final class App {
    private App() {
        // Prevent instantiation of a class containing only an entry point.
    }

    public static String greeting() {
        return "Java transaction platform: ready";
    }

    public static void main(String[] args) {
        System.out.println(greeting());
    }
}
