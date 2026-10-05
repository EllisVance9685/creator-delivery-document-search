package example.creator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InfraiGateway {
    private final String baseUrl;
    private final String key;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();
    private final OpenAIClient embeddings;

    public InfraiGateway(@Value("${infrai.base-url}") String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
        this.key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        this.embeddings = OpenAIOkHttpClient.builder().apiKey(key).baseUrl(baseUrl + "/v1").build();
    }

    public List<Float> embed(String text) {
        return embeddings.embeddings().create(EmbeddingCreateParams.builder()
                .model("text-embedding-3-small").input(text).build()).data().get(0).embedding();
    }

    public JsonNode ocr(byte[] pdf, String name, String requestId) {
        String boundary = "creator-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"pdf\"; filename=\"" +
                name.replaceAll("[^a-zA-Z0-9._-]", "_") + "\"\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + pdf.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(pdf, 0, body, prefix.length, pdf.length);
        System.arraycopy(suffix, 0, body, prefix.length + pdf.length, suffix.length);
        return send("POST", "/v1/pdf/ocr", body, "multipart/form-data; boundary=" + boundary, requestId);
    }

    public JsonNode post(String path, Map<String, ?> fields, String requestId) {
        try {
            return send("POST", path, json.writeValueAsBytes(fields), "application/json", requestId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid request data", e);
        }
    }

    private JsonNode send(String method, String path, byte[] body, String contentType, String requestId) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + key)
                        .header("Content-Type", contentType).header("Idempotency-Key", requestId)
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                            .filter(value -> value.matches("[0-9]+"))
                            .map(Long::parseLong).orElse(1L << attempt);
                    Thread.sleep(Math.min(seconds, 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    int status = response.statusCode();
                    HttpStatus mapped = status >= 400 && status < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
                    throw new ResponseStatusException(mapped,
                            envelope.path("error").path("code").asText("UPSTREAM_REJECTED") + ": " +
                                    envelope.path("error").path("message").asText());
                }
                if (response.statusCode() >= 500) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                return envelope.path("data");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted", e);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream transport error", e);
            }
        }
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Retry later");
    }
}
