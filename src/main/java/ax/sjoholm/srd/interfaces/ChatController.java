package ax.sjoholm.srd.interfaces;

import java.io.IOException;
import java.util.List;

import javax.validation.Valid;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ax.sjoholm.srd.api.ChatApi;
import ax.sjoholm.srd.api.ChatRequest;
import ax.sjoholm.srd.services.chat.ChatDtos;
import ax.sjoholm.srd.services.chat.ChatService;

@RestController
public class ChatController implements ChatApi {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  public ChatDtos.ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return chatService.chat(request);
  }

  @Override
  public SseEmitter stream(
      @RequestParam String conversationId,
      @RequestParam String message) {
    SseEmitter emitter = new SseEmitter(0L);

    chatService.stream(conversationId, message, new ChatService.StreamCallbacks() {
      @Override
      public void onToken(String token) {
        try {
          emitter.send(SseEmitter.event().name("message").data(token));
        } catch (IOException e) {
          emitter.completeWithError(e); 
        }
      }

      @Override
      public void onSources(List<ChatService.Source> sources) {
        try {
          emitter.send(SseEmitter.event().name("sources").data(Json.toJson(sources)));
        } catch (IOException e) {
          emitter.completeWithError(e);
        }
      }

      @Override
      public void onDone() {
        try {
          emitter.send(SseEmitter.event().name("done").data("ok"));
        } catch (IOException ignored) {
        }
        emitter.complete();
      }

      @Override
      public void onError(Throwable t) {
        emitter.completeWithError(t);
      }
    });

    return emitter;
  }
}
