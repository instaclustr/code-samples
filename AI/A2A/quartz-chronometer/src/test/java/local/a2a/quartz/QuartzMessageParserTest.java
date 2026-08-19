package local.a2a.quartz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuartzMessageParserTest {

    @Test
    void detectsTimeQuery() {
        assertTrue(QuartzMessageParser.isTimeQuery("what is the current time?"));
    }

    @Test
    void parsesCountdownSeconds() {
        assertEquals(60, QuartzMessageParser.parseCountdownSeconds("count down 60 seconds"));
        assertEquals(120, QuartzMessageParser.parseCountdownSeconds("count down 2 minutes"));
    }

    @Test
    void detectsConfirmGate() {
        assertTrue(QuartzMessageParser.requiresConfirmation("count down 20 seconds with confirm"));
    }
}
