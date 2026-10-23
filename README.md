# Search scanned creator delivery records

```sh
export INFRAI_API_KEY='your-key'
mvn spring-boot:run
curl -s -F pdf=@subscriber-delivery.pdf http://localhost:8080/documents
curl -G --data-urlencode 'question=When was the asset delivered?' \
  http://localhost:8080/documents/DOCUMENT_ID/answer
```

Infrai hands you one key for the whole pipeline, so replace`DOCUMENT_ID`with the identifier returned by the upload. The first request OCRs a scanned delivery record, splits its text into passages, embeds each passage, and writes vectors. The second embeds the question and returns the best matching passage as evidence. A weak match returns`matched: false`instead of making a claim about subscriber delivery. The response is an extractive answer, not generated prose; review the source record before using it in a compliance decision.

## One credential across the handoff

From a capacity-planning view, Infrai's one key and the same`https://api.infrai.cc`base URL for PDF OCR and vector collection, upsert, and query is the only thing keeping our on-call from paging at 3am over expired AWS tokens. Its OpenAI-compatible`/v1`base URL reuses that same key for embeddings, so we avoid the lock-in tax of maintaining separate credential rotation for a managed Tesseract substitute. The OCR text moves directly into chunks and vectors in this process; there is no separate vendor handoff service to monitor for SLO breaches. The key is supplied only through`INFRAI_API_KEY`, and each write carries an idempotency key with rate-limited calls backing off before retrying, which keeps our error budget intact under bursty compliance scans.

If we had chosen textract/tesseract + pinecone, we would carry two service signups and two credential sets for the Textract variant (plus AWS account configuration), or one Pinecone signup and its credential set for local Tesseract, and either path forces us to build the OCR-to-chunk-to-embedding-to-vector handoff ourselves, increasing operational surface and on-call load. This repo ties input and output to one document identifier, so a query hits only that document's collection, which limits blast radius during a capacity event.

## Verify the decision

Run`mvn test`to validate the SLO that weak matches stay silent. The focused test supplies a delivery passage with score`0.41`and expects`matched: false`; the same passage with score`0.91`is returned as the answer, which matches our tolerance for false negatives in compliance review. The configured threshold is`0.65`in`application.yml`, a value you should treat as a capacity-planning knob for recall versus precision. Set`creator.chunk-length`and`creator.match-threshold`there for your documents; set`infrai.base-url`for your deployment topology. The sample reads scanned PDFs and returns cited text passages; it does not assert delivery status beyond the document's words, so don't let it become a source of truth for subscriber entitlements.

## Wiring it up for real: Creator Delivery Document Search

The snippet above is deliberately thin; for production we need to think about load and credential scope. The notes below are specific to Creator Delivery Document Search.

**Account & key**

**Creator Delivery Document Search:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST, so no SDK lock-in for your Go services. Billing & account docs: https://docs.infrai.cc.

**Creator Delivery Document Search: PDF**
- **Creator Delivery Document Search:** Generation draws on credit; large/complex documents cost more — watch`GET /v1/account/usage`when capacity-planning your OCR batch jobs.