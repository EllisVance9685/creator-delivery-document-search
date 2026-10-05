package example.creator;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CreatorDocumentController {
    private final CreatorIndex index;

    public CreatorDocumentController(CreatorIndex index) { this.index = index; }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreatorIndex.Receipt upload(@RequestParam("pdf") MultipartFile pdf) throws IOException {
        return index.ingest(pdf.getBytes(), pdf.getOriginalFilename() == null ? "" : pdf.getOriginalFilename());
    }

    @GetMapping("/documents/{documentId}/answer")
    public CreatorIndex.Answer answer(@PathVariable String documentId, @RequestParam String question) {
        return index.ask(documentId, question);
    }
}
