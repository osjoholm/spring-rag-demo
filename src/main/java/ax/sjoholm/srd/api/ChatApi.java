package ax.sjoholm.srd.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ax.sjoholm.srd.services.chat.ChatDtos;

@Tag(name = "Chat", description = "RAG-powered chat API for querying legal documents")
public interface ChatApi {

    @Operation(
            summary = "Ask a question",
            description = "Submit a question and receive an answer based on retrieved document context, " +
                    "along with citations and optionally the relevant document chunks.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Question answered successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChatDtos.ChatResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - missing or malformed question",
                    content = @Content),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content)
    })
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ChatDtos.ChatResponse chat(@Valid @RequestBody ChatRequest request);

    @Operation(
            summary = "Stream a chat response",
            description = "Submit a question and receive a streaming response via Server-Sent Events. " +
                    "Tokens are sent incrementally as they are generated.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Streaming response initiated",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content)
    })
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @Parameter(description = "Unique conversation identifier for tracking the session", required = true)
            @RequestParam String conversationId,
            @Parameter(description = "The user's question or message", required = true)
            @RequestParam String message);
}
