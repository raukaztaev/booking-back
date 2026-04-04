package org.example.bookingback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BookingBackApplicationTests {

    @Test
    @DisplayName("Контекст вообще поднимается")
    void contextLoads() {
    }

}
