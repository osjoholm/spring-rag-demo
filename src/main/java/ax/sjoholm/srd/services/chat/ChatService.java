package ax.sjoholm.srd.services.chat;

import ax.sjoholm.srd.configuration.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ChatService {

    private static final int STREAM_FLUSH_THRESHOLD = 60;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RagProperties props;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final SimpleLoggerAdvisor promptLogger = new SimpleLoggerAdvisor();

    public ChatService(ChatClient chatClient, VectorStore vectorStore, RagProperties props) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.props = props;
    }

    public void stream(String conversationId, String userMessage, StreamCallbacks cb) {
        executor.submit(() -> {
            try {
                var qaAdvisor = qaAdvisor(userMessage);
                AtomicBoolean sourcesSent = new AtomicBoolean(false);
                StringBuilder tokenBuffer = new StringBuilder();

                log.info("Streaming prompt [{}]: {}", conversationId, userMessage);
                Flux<ChatClientResponse> flux = chatClient
                        .prompt()
                        .advisors(qaAdvisor, promptLogger)
                        .user(userMessage)
                        .stream()
                        .chatClientResponse();

                flux.doOnNext(resp -> {
                            String token = resp.chatResponse()
                                    .getResult()
                                    .getOutput()
                                    .getText();

                            if (token != null && !token.isEmpty()) {
                                tokenBuffer.append(token);
                                if (shouldFlush(token, tokenBuffer)) {
                                    flushBuffer(tokenBuffer, cb);
                                }
                            }

                            if (!sourcesSent.get()) {
                                List<Document> retrieved = retrievedDocuments(resp.context());
                                if (!retrieved.isEmpty() && sourcesSent.compareAndSet(false, true)) {
                                    log.debug("Streaming {} retrieved documents as sources", retrieved.size());
                                    cb.onSources(toSources(retrieved));
                                }
                            }
                        })
                        .doOnError(t -> {
                            flushBuffer(tokenBuffer, cb);
                            cb.onError(t);
                        })
                        .doOnComplete(() -> {
                            flushBuffer(tokenBuffer, cb);
                            cb.onDone();
                        })
                        .subscribe();

            } catch (Throwable t) {
                cb.onError(t);
            }
        });
    }

    private static boolean shouldFlush(String latestToken, StringBuilder buffer) {
        return buffer.length() >= STREAM_FLUSH_THRESHOLD
                || latestToken.contains("\n")
                || latestToken.contains(". ")
                || latestToken.contains("? ")
                || latestToken.contains("! ");
    }

    private static void flushBuffer(StringBuilder buffer, StreamCallbacks cb) {
        if (buffer.isEmpty()) {
            return;
        }
        cb.onToken(buffer.toString());
        buffer.setLength(0);
    }

    public ChatDtos.ChatResponse chat(ax.sjoholm.srd.api.ChatRequest req) {
    var qaAdvisor = qaAdvisor(req.getQuestion());

    log.info("Prompt: {}", req.getQuestion());
    var hits = vectorStore.similaritySearch(req.getQuestion());
    hits.forEach(d -> log.info("hit md={} chars={} head={}",
        d.getMetadata(),
        d.getText().length(),
        d.getText().substring(0, Math.min(120, d.getText().length())).replace("\n", "\\n")
    ));

    ChatClientResponse resp = chatClient
        .prompt()
        .advisors(qaAdvisor, promptLogger)
        .user(req.getQuestion())
        .call()
        .chatClientResponse();

    String answer = resp.chatResponse()
        .getResult()
        .getOutput()
        .getText();
    List<Document> retrieved = retrievedDocuments(resp.context());


    if (retrieved.isEmpty()) {
      return new ChatDtos.ChatResponse(
          "Jag kan tyvärr inte svara på det utifrån de nuvarande dokumenten.",
          new ChatDtos.Verification("INSUFFICIENT_CONTEXT", "No sufficiently similar document chunks were retrieved."),
          List.of(),
          List.of());
    }

    var citations = retrieved.stream()
        .map(this::toCitation)
        .distinct()
        .toList();

    boolean includeChunks = Boolean.TRUE.equals(req.getIncludeChunks());
    var chunks = includeChunks
        ? retrieved.stream().map(this::toChunk).toList()
        : List.<ChatDtos.Chunk>of();

    var verification = citations.isEmpty()
        ? new ChatDtos.Verification("INSUFFICIENT_CONTEXT", "Model returned an answer but no citations were captured.")
        : new ChatDtos.Verification("SUPPORTED", "Answer is backed by retrieved document chunks.");

    if (citations.isEmpty()) {
      return new ChatDtos.ChatResponse(
          "I can’t answer that from the currently ingested documents.",
          verification,
          List.of(),
          chunks);
    }

    return new ChatDtos.ChatResponse(answer, verification, citations, chunks);
  }

    private QuestionAnswerAdvisor qaAdvisor(String query) {
        var searchRequest = SearchRequest.builder()
                .query(query)
                .topK(props.topK())
                .similarityThreshold(props.similarityThreshold())
                .build();

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .promptTemplate(PromptTemplate.builder()
                        .template("""
                                {query}

                                Kontextinformation är nedan, omgiven av ---------------------

                                ---------------------
                                {question_answer_context}
                                ---------------------

                                Baserat på endast den ovanstående kontextinformationen, svara på frågan på svenska 
                                så kortfattat och koncist som möjligt. Referera till den lag och paragraf där du 
                                hittat svaret. Om du inte hittar svaret i kontextinformationen, säg att du inte kan 
                                svara utgående från de nuvarande dokumenten.
                                """)
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Document> retrievedDocuments(Map<String, Object> context) {
        return (List<Document>) context.getOrDefault(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());
    }

    private List<Source> toSources(List<Document> documents) {
        return documents.stream()
                .map(this::toSource)
                .distinct()
                .toList();
    }

    private ChatDtos.Citation toCitation(Document d) {
        Map<String, Object> md = metadata(d);
        String lawId = str(md.getOrDefault("law_code", md.getOrDefault("sourceId", d.getId())));
        String title = str(md.getOrDefault("law_name", md.getOrDefault("filename", "Untitled")));
        return new ChatDtos.Citation(lawId, title);
    }

    private ChatDtos.Chunk toChunk(Document d) {
        Map<String, Object> md = metadata(d);
        String chunkId = str(md.getOrDefault("chunkId", d.getId()));
        String excerpt = excerpt(d.getText(), 500);
        return new ChatDtos.Chunk(chunkId, excerpt, md);
    }

    private Source toSource(Document d) {
        Map<String, Object> md = metadata(d);
        String url = str(md.getOrDefault("url", md.getOrDefault("source", "")));
        return new Source(url);
    }

    private Map<String, Object> metadata(Document d) {
        return Optional.ofNullable(d.getMetadata()).orElse(Map.of());
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String excerpt(String s, int max) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    public interface StreamCallbacks {
        void onToken(String token);
        void onSources(List<Source> sources);
        void onDone();
        void onError(Throwable t);
    }

    public record Source(String url) {
    }
}