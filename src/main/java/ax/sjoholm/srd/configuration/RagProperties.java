package ax.sjoholm.srd.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
    int topK,
    double similarityThreshold
) {
  public RagProperties {
    if (topK < 1 || topK > 50) throw new IllegalArgumentException("rag.top-k must be 1..50");
    if (similarityThreshold < 0.0 || similarityThreshold > 1.0)
      throw new IllegalArgumentException("rag.similarity-threshold must be 0..1");
  }
}