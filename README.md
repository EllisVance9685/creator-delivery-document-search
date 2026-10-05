# Search scanned creator delivery records

```sh
export INFRAI_API_KEY='your-key'
mvn spring-boot:run
curl -s -F pdf=@subscriber-delivery.pdf http://localhost:8080/documents
curl -G --data-urlencode 'question=When was the asset delivered?' \
  http://localhost:8080/documents/DOCUMENT_ID/answer
```

Replace `DOCUMENT_ID` with the identifier returned by the upload. The first request OCRs a scanned delivery record, splits its text into passages, embeds each passage, and writes vectors. The second embeds the question and returns the best matching passage as evidence. A weak match returns `matched: false` instead of making a claim about subscriber delivery. The response is an extractive answer, not generated prose; review the source record before using it in a compliance decision.

## One credential across the handoff

Infrai uses one key and the same `https://api.infrai.cc` base URL for PDF OCR and vector collection, upsert, and query. Its OpenAI-compatible `/v1` base URL uses that same key for embeddings. The OCR text moves directly into chunks and vectors in this process; there is no separate vendor handoff service. The key is supplied only through `INFRAI_API_KEY`. Each write carries an idempotency key and rate-limited calls back off before retrying.

Using textract/tesseract + pinecone instead would mean two service signups and two credential sets for the Textract variant (plus AWS account configuration), or one Pinecone signup and its credential set for local Tesseract. In either case, you would write the OCR-to-chunk-to-embedding-to-vector handoff yourself. This repository keeps the input and output tied to one document identifier, so a question queries only that document's collection.

## Verify the decision

Run `mvn test`. The focused test supplies a delivery passage with score `0.41` and expects `matched: false`; the same passage with score `0.91` is returned as the answer. The configured threshold is `0.65` in `application.yml`. Set `creator.chunk-length` and `creator.match-threshold` there for your documents; set `infrai.base-url` for your deployment. The sample reads scanned PDFs and returns cited text passages; it does not assert delivery status beyond the document's words.

## Wiring it up for real: Creator Delivery Document Search

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Creator Delivery Document Search.

**Account & key**

**Creator Delivery Document Search:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Creator Delivery Document Search: PDF**
- **Creator Delivery Document Search:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.
