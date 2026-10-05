package example.creator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreatorIndex {
    private final InfraiGateway gateway;
    private final int chunkLength;
    private final double threshold;

    public CreatorIndex(InfraiGateway gateway, @Value("${creator.chunk-length}") int chunkLength,
            @Value("${creator.match-threshold}") double threshold) {
        this.gateway = gateway;
        this.chunkLength = chunkLength;
        this.threshold = threshold;
    }

    public record Receipt(String documentId, int passages) {}
    public record Answer(String documentId, String answer, boolean matched) {}

    public Receipt ingest(byte[] pdf, String filename) {
        if (pdf.length == 0 || !filename.toLowerCase().endsWith(".pdf"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required");
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Upload is unavailable because the vector API has no collection cleanup operation");
    }

    public Answer ask(String documentId, String question) {
        if (!documentId.matches("[0-9a-fA-F-]{36}") || question == null || question.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document ID and question are required");
        JsonNode data = gateway.post("/v1/vector/query", Map.of(
                "collection", "creator_" + documentId.replace("-", ""),
                "embedding", gateway.embed(question), "top_k", 3, "include_metadata", true),
                UUID.randomUUID().toString());
        return select(documentId, data.path("matches"), threshold);
    }

    static Answer select(String id, JsonNode matches, double threshold) {
        JsonNode best = matches.isArray() && !matches.isEmpty() ? matches.get(0) : null;
        if (best == null || best.path("score").asDouble(0) < threshold ||
                best.path("metadata").path("text").asText().isBlank())
            return new Answer(id, "No supported passage found.", false);
        return new Answer(id, best.path("metadata").path("text").asText(), true);
    }

    static List<String> chunks(String text, int length) {
        if (length < 1) throw new IllegalArgumentException("chunk length must be positive");
        String normalized = text.replaceAll("\\s+", " ").trim();
        List<String> result = new ArrayList<>();
        for (int start = 0; start < normalized.length(); start += length)
            result.add(normalized.substring(start, Math.min(start + length, normalized.length())));
        return result;
    }
}
