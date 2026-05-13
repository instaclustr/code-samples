package local.kip1034.dlq.news.freeform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Long neutral sample article (plain UTF-8 prose) for the free-form producer. Text lives in
 * {@code museum-demo-incident-sample.txt} on the classpath beside this class.
 */
public final class FreeformSampleProse {

    private static final String RESOURCE = "museum-demo-incident-sample.txt";

    public static String museumDemoIncident() {
        try (InputStream in = FreeformSampleProse.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FreeformSampleProse() {}
}
