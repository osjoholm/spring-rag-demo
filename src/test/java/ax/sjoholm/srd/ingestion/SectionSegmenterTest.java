package ax.sjoholm.srd.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import ax.sjoholm.srd.services.ingestion.SectionSegmenter;

@DisplayName("SectionSegmenter")
class SectionSegmenterTest {

    private static final String SAMPLE_LAW_TEXT = """
            N 1 Landskapslag (2011:95) om radio- och televisionsverksamhet
            1 kap. Inledande bestämmelser
            1 §. Lagens innehåll (2020/119)
            I denna lag finns bestämmelser om de villkor som gäller för beställ-tv, \
            radio- och televisionssändningar och mottagande av sådana på Åland, \
            om de villkor som gäller för sändningar genom ledning eller någon \
            annan fast förbindelse som når fler än 200 bostäder, om de avgifter \
            som ska betalas för tillstånd att utöva verksamhet som innefattar \
            radio- och televisionssändning samt om de myndigheter som beslutar \
            i de frågor som hör till lagens tillämpningsområde. (2019/104)
            I 5 kap. finns särskilda bestämmelser om videodel-
            ningsplattformstjänster. (2020/119)
            I landskapslagen (2019:103) om medieavgift finns särskilda bestämmelser \
            om det särskilda uppdrag Ålands Radio och Tv Ab har att tillhandahålla \
            allmännyttig radio- och televisionsverksamhet. (2019/104)
            1a §. (2020/119) Lagens tillämpningsområde
            Lagen tillämpas på radiosändningar som kan tas emot på Åland då \
            leverantören är etablerad på Åland. Lagen tillämpas på \
            televisionssändningar och beställ-tv, som kan tas emot på Åland \
            och där leverantören av den audiovisuella medietjänsten är etablerad \
            på Åland enligt definitionen i artikel 2.3 i Europaparlamentets och \
            rådets direktiv 2010/13/EU om samordning av vissa bestämmelser som \
            fastställts i medlemsstaternas lagar och andra författningar om \
            tillhandahållandet av audiovisuella medietjänster, nedan benämnt AV-direktivet.
            Om leverantören av den audiovisuella medietjänsten inte är etablerad \
            på Åland eller i en stat som är bunden av avtalet om Europeiska \
            ekonomiska samarbetsområdet (EES-stat) ska lagen gälla om den som \
            sänder på Åland använder sig av en satellitupplänk som är belägen på Åland.
            """;

    private final SectionSegmenter segmenter = new SectionSegmenter();

    @Test
    @DisplayName("splits law into sections with correct metadata")
    void splitsLawIntoSectionsWithMetadata() {
        Document instrument = new Document(SAMPLE_LAW_TEXT, Map.of("source", "test-case"));

        List<Document> sections = segmenter.splitIntoSections(instrument);

        assertThat(sections).hasSize(2);
        assertFirstSection(sections.get(0));
        assertSecondSection(sections.get(1));
    }

    @Test
    @DisplayName("returns empty list for blank document")
    void returnsEmptyListForBlankDocument() {
        Document blankDoc = new Document("   ", Map.of());

        List<Document> sections = segmenter.splitIntoSections(blankDoc);

        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("handles document with no sections")
    void handlesDocumentWithNoSections() {
        Document noSectionsDoc = new Document("Some text without any sections.", Map.of());

        List<Document> sections = segmenter.splitIntoSections(noSectionsDoc);

        assertThat(sections).isEmpty();
    }

    private void assertFirstSection(Document section) {
        assertThat(section.getMetadata())
                .containsEntry("segment_type", "SECTION")
                .containsEntry("chapter_no", "1")
                .containsEntry("chapter_title", "Inledande bestämmelser")
                .containsEntry("section_no", "1")
                .containsEntry("source", "test-case");
        assertThat(section.getText())
                .startsWith("1 §. Lagens innehåll (2020/119)")
                .contains("beställ-tv")
                .contains("videodel-");
    }

    private void assertSecondSection(Document section) {
        assertThat(section.getMetadata())
                .containsEntry("segment_type", "SECTION")
                .containsEntry("section_no", "1a")
                .containsEntry("chapter_no", "1")
                .containsEntry("chapter_title", "Inledande bestämmelser");
        assertThat(section.getText())
                .startsWith("1a §. (2020/119) Lagens tillämpningsområde")
                .contains("satellitupplänk")
                .contains("AV-direktivet");
    }
}
