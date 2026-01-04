package ax.sjoholm.srd.configuration;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import ax.sjoholm.srd.services.ingestion.LagsamlingDocumentReader;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class AiConfiguration {

  @Value("${rag.classpath-file}")
  private String fileName;
    
  @Bean
  ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("""
          Du är en hjälpsam assistent för Ålands Lagsamling.
          Du svarar på frågor gällande Åländsk lagstiftning.
          Svara endast med hjälp av den givna kontexten (dokumentutdrag ur Ålands lagsamling). Svara på svenska. Svara kortfattat och koncist. Referera till den lag och paragraf där du hittat svaret.
          Om kontexten inte innehåller svaret, säg att du inte kan svara utgående från de nuvarande dokumenten.
          """)
        .build();
  }

  @Bean
  DocumentReader lagsamlingDocumentReader() {
    return new LagsamlingDocumentReader(new ClassPathResource(fileName));
  }

   @Component
    public class MyTokenTextSplitter {

        public List<Document> splitDocuments(List<Document> documents) {
            TokenTextSplitter splitter = new TokenTextSplitter();
            return splitter.apply(documents);
        }

        public List<Document> splitCustomized(List<Document> documents) {

            /**
             * defaultChunkSize: The target size of each text chunk in tokens (default:800).
             * minChunkSizeChars: The minimum size of each text chunk in characters(default:
             * 350).
             * minChunkLengthToEmbed: The minimum length of a chunk to be included
             * (default:5).
             * maxNumChunks: The maximum number of chunks to generate from a text
             * (default:10000).
             * keepSeparator: Whether to keep separators (like newlines) in the chunks
             * (default: true).
             */

            TokenTextSplitter splitter =
                    TokenTextSplitter.builder()
                            .withChunkSize(600)
                            .withMinChunkSizeChars(250)
                            .withMinChunkLengthToEmbed(8)
                            .withMaxNumChunks(5000)
                            .withKeepSeparator(true)
                            .build();
            return splitter.apply(documents);
        }
    }
}
