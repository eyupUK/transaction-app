package io.github.eyupuk.transactions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {
    @Test
    void greetingDescribesTheProject() {
        assertEquals("Java transaction platform: ready", App.greeting());
    }
}
