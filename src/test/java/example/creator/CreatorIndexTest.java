package example.creator;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;

class CreatorIndexTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void subscriberClaimRequiresSupportedPassage() throws Exception {
        var weak = json.readTree("[{\"score\":0.41,\"metadata\":{\"text\":\"Delivery confirmed\"}}]");
        var strong = json.readTree("[{\"score\":0.91,\"metadata\":{\"text\":\"Delivery confirmed\"}}]");
        assertFalse(CreatorIndex.select("doc", weak, 0.65).matched());
        assertEquals("Delivery confirmed", CreatorIndex.select("doc", strong, 0.65).answer());
    }

    @Test
    void uploadDoesNotCreateACollectionWithoutCleanupSupport() {
        CreatorIndex index = new CreatorIndex(null, 900, 0.65);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> index.ingest(new byte[] {1}, "record.pdf"));

        assertEquals(501, error.getStatusCode().value());
    }
}
