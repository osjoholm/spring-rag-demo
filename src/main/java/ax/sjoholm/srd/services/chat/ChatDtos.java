package ax.sjoholm.srd.services.chat;

import java.util.List;
import java.util.Map;

public final class ChatDtos {

  public record ChatResponse(
      String answer,
      Verification verification,
      List<Citation> citations,
      List<Chunk> chunks
  ) {}

  public record Verification(
      String status,
      String reason
  ) {}

  public record Citation(
      String lawId,
      String title
  ) {}

  public record Chunk(
      String chunkId,
      String contentExcerpt,
      Map<String, Object> metadata
  ) {}
}
