package ax.sjoholm.srd.services.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A custom document reader that is aware of the layout of the Åland Lagsamling
 * PDF documents.
 * It produces documents segmented by law code, merging pages that belong to the
 * same law.
 */
public class LagsamlingDocumentReader implements DocumentReader {

    private static final Pattern PAGE_SUB_HEADER_PATTERN = Pattern.compile(
            "(?s)\\A\\s*([\\p{L}]\\s*\\d{1,2})\\b\\s*(.*?)(?=\\R\\s*1\\s*kap\\.?\\b)", Pattern.DOTALL);
    private static final Pattern HEADER_PAGE_PATTERN = Pattern.compile("^([A-Z]+\\s[0-9]+)(.+)");

    // Page layout configurations for two-column PDF extraction
    private static final TwoColumnPageConfig HEADING_PAGE_CONFIG = new TwoColumnPageConfig(
            new Rectangle2D.Float(30f, 180f, 210f, 480f),
            new Rectangle2D.Float(240f, 180f, 210f, 480f));

    private static final TwoColumnPageConfig NORMAL_PAGE_CONFIG = new TwoColumnPageConfig(
            new Rectangle2D.Float(30f, 50f, 210f, 610f),
            new Rectangle2D.Float(240f, 50f, 210f, 610f));

    // Regions used to identify page type
    private static final Rectangle2D PAGE_SUB_HEADER_REGION = new Rectangle2D.Float(1f, 35f, 480f, 10f);
    private static final Rectangle2D HEADER_PAGE_REGION = new Rectangle2D.Float(1f, 130f, 480f, 30f);

    private final Resource resource;
    private final SectionSegmenter sectionSegmenter = new SectionSegmenter();

    public LagsamlingDocumentReader(final Resource resource) {
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    @Override
    public List<Document> get() {
        try (InputStream is = resource.getInputStream();
                PDDocument pdf = Loader.loadPDF(is.readAllBytes())) {

            List<DocumentText> pageTexts = new ArrayList<>(pdf.getNumberOfPages());
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                PDPage page = pdf.getPage(i);
                DocumentText text = extractPageText(page);
                pageTexts.add(text);
            }

            return sectionSegmenter.splitIntoSections(mergePagesByLawCode(pageTexts)).stream().map(d -> withHeader(d))
                    .toList();

        } catch (IOException e) {
            throw new IllegalStateException("Failed reading PDF: " + resource, e);
        }
    }

    private List<Document> mergePagesByLawCode(List<DocumentText> pageTexts) {
        return pageTexts.stream()
                .collect(Collectors.toMap(
                        DocumentText::lawCode,
                        Function.identity(),
                        (a, b) -> new DocumentText(
                                a.text() + "\n" + b.text(),
                                a.lawCode(),
                                a.lawName()),
                        LinkedHashMap::new))
                .values().stream()
                .map(dt -> new Document(dt.text(), Map.of(
                        "law_code", dt.lawCode(),
                        "law_name", dt.lawName())))
                .collect(Collectors.toList());
    }

    private DocumentText extractPageText(PDPage page) throws IOException {
        PDFTextStripperByArea pageTypeStripper = new PDFTextStripperByArea();
        pageTypeStripper.addRegion("SUBHEADER", PAGE_SUB_HEADER_REGION);
        pageTypeStripper.addRegion("HEADERPAGE", HEADER_PAGE_REGION);
        pageTypeStripper.extractRegions(page);

        String subHeader = pageTypeStripper.getTextForRegion("SUBHEADER");
        String headerPageText = pageTypeStripper.getTextForRegion("HEADERPAGE");

        boolean isHeaderPage = headerPageText.trim().isBlank();

        String leftText;
        String rightText;
        String lawName = null;
        String lawCode = null;

        if (isHeaderPage) {
            PDFTextStripperByArea stripper = getStripper(page, HEADING_PAGE_CONFIG);
            leftText = stripper.getTextForRegion("L");
            rightText = stripper.getTextForRegion("R");

            // For heading pages, extract the law title from the first paragraph
            Matcher matcher = PAGE_SUB_HEADER_PATTERN.matcher(leftText);
            if (matcher.find() && matcher.hasMatch()) {
                lawCode = matcher.group(1);
                lawName = matcher.group(2).replaceAll("\\R", "");
            }
        } else {
            PDFTextStripperByArea stripper = getStripper(page, NORMAL_PAGE_CONFIG);
            leftText = stripper.getTextForRegion("L");
            rightText = stripper.getTextForRegion("R");

            Matcher headerMatcher = HEADER_PAGE_PATTERN.matcher(subHeader);
            if (headerMatcher.find() && headerMatcher.hasMatch()) {
                lawCode = headerMatcher.group(1).trim();
                lawName = headerMatcher.group(2).trim();
            }
        }

        String mergedText = mergeColumns(leftText, rightText);
        return new DocumentText(
                normalize(mergedText),
                lawCode != null ? lawCode : "",
                lawName != null ? lawName : "");
    }

    private String mergeColumns(String leftText, String rightText) {
        StringBuilder merged = new StringBuilder();
        if (leftText != null && !leftText.isBlank()) {
            merged.append(leftText.trim());
        }
        if (rightText != null && !rightText.isBlank()) {
            if (!merged.isEmpty()) {
                merged.append("\n\n");
            }
            merged.append(rightText.trim());
        }
        return merged.toString();
    }

    private PDFTextStripperByArea getStripper(PDPage page, TwoColumnPageConfig pageConfig) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.addRegion("L", pageConfig.leftColumn());
        stripper.addRegion("R", pageConfig.rightColumn());
        stripper.extractRegions(page);
        return stripper;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        // Remove control chars except newline/tab
        normalized = normalized.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");

        // De-hyphenate across line breaks: "tillämp-\nning" -> "tillämpning"
        normalized = normalized.replaceAll("-\\s*\\n\\s*(?=\\p{L})", "");

        // Collapse extreme whitespace runs from PDF extraction
        normalized = normalized.replaceAll("[\\t ]{2,}", " ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");

        return normalized.trim();
    }

    static Document withHeader(Document d) {
        var md = d.getMetadata();
        String header = "[law=" + md.get("law_code")
                + " | " + md.get("law_name")
                + " | kap " + md.get("chapter_no")
                + " | § " + md.get("section_no")
                + " | url=" + md.get("url")
                + "]";
        return new Document(header + "\n" + d.getText(), md);
    }
    private record TwoColumnPageConfig(Rectangle2D leftColumn, Rectangle2D rightColumn) {
    }
    private record DocumentText(String text, String lawCode, String lawName) {
    }
}
