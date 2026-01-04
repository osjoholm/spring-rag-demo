package ax.sjoholm.srd.configuration;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class ApplicationConfiguration {

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
