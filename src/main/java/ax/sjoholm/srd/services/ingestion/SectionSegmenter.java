package ax.sjoholm.srd.services.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SectionSegmenter {

  // Chapter headings: "1 kap. Allmänna stadganden"
  private static final Pattern CHAPTER = Pattern.compile(
      "(?im)^\\s*(?<no>\\d+)\\s*kap\\.\\s*(?<title>.*)\\s*$"
  );

  // Section headings: "1 §.", "7a §", "12 §" etc.
  private static final Pattern SECTION = Pattern.compile(
      "(?im)^\\s*(?<no>\\d+[a-zA-Z]?)\\s*§\\.?\\s*(?<rest>.*)\\s*$"
  );

  // Optional: references like "Se 8 och 9 kap. ..." are not chapter headings,
  // because they don't start with number+kap. at line start (they start with "Se")
  // so we are safe.

  public static final class Config {
    public int minSectionChars = 250;            // if a section is shorter, we may merge it with next
    public int maxSectionChars = 12000;          // if a section is huge, split into subchunks
    public int maxSubchunks = 8;                 // avoid runaway splitting
    public boolean mergeShortSections = true;

    public static Config defaults() { return new Config(); }
  }

  private final Config config;

  public SectionSegmenter() {
    this(Config.defaults());
  }

  public SectionSegmenter(Config config) {
    this.config = config;
  }

  /**
   * Splits one "instrument" document into paragraph (§) documents.
   * Keeps track of current chapter (kap.) and adds metadata.
   */
  public List<Document> splitIntoSections(Document instrumentDoc) {
    String text = Optional.ofNullable(instrumentDoc.getText()).orElse("");
    if (text.isBlank()) return List.of();

    Map<String, Object> baseMd = new HashMap<>(instrumentDoc.getMetadata());
    baseMd.putIfAbsent("segment_type", "INSTRUMENT");

    List<String> lines = lines(text);

    String currentChapterNo = "";
    String currentChapterTitle = "";

    List<SectionBuilder> sections = new ArrayList<>();
    SectionBuilder current = null;

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);

      // Chapter?
      Matcher cm = CHAPTER.matcher(line);
      if (cm.matches()) {
        currentChapterNo = cm.group("no").trim();
        currentChapterTitle = normalizeSpaces(cm.group("title"));

        // keep chapter heading line inside text stream if you want it included in the next §
        // I prefer storing it in metadata and not injecting it, but we can optionally add it:
        continue;
      }

      // Section?
      Matcher sm = SECTION.matcher(line);
      if (sm.matches()) {
        // flush previous
        if (current != null && current.hasContent()) {
          sections.add(current);
        }

        current = new SectionBuilder(baseMd);
        current.chapterNo = currentChapterNo;
        current.chapterTitle = currentChapterTitle;

        current.sectionNo = sm.group("no").trim();
        String rest = normalizeSpaces(sm.group("rest"));

        // include the section header line in the section body
        String header = current.sectionNo + " §";
        if (!rest.isBlank()) header += ". " + rest;
        current.appendLine(header);

        continue;
      }

      // normal line
      if (current != null) {
        current.appendLine(line);
      }
    }

    if (current != null && current.hasContent()) {
      sections.add(current);
    }

    // Optionally merge too-short sections into next one (common in lawbooks: "1 § (Lika som...)" etc.)
    if (config.mergeShortSections) {
      sections = mergeShort(sections, config.minSectionChars);
    }

    // Materialize Documents
    List<Document> out = new ArrayList<>();
    for (SectionBuilder sb : sections) {
      String sectionText = sb.text();

      // Split very large sections (rare but happens with appendices)
      if (sectionText.length() > config.maxSectionChars) {
        out.addAll(splitLargeSection(sb, config.maxSectionChars, config.maxSubchunks));
      } else {
        out.add(sb.build(sectionText, 0, 1));
      }
    }

    return out;
  }

  /**
   * Convenience: split many instrument docs.
   */
  public List<Document> splitIntoSections(List<Document> instrumentDocs) {
    List<Document> out = new ArrayList<>();
    for (Document d : instrumentDocs) {
      out.addAll(splitIntoSections(d));
    }
    return out;
  }

  // --- helpers ---

  private List<String> lines(String text) {
    String t = text.replace("\r\n", "\n").replace('\r', '\n');
    // collapse crazy whitespace but keep newlines
    t = t.replaceAll("[\\t ]{2,}", " ");
    String[] arr = t.split("\n");
    List<String> out = new ArrayList<>(arr.length);
    for (String s : arr) {
      // keep empty lines only if you want paragraph breaks. Here we keep them lightly.
      out.add(s.trim());
    }
    return out;
  }

  private String normalizeSpaces(String s) {
    return s == null ? "" : s.replaceAll("\\s+", " ").trim();
  }

  private List<SectionBuilder> mergeShort(List<SectionBuilder> in, int minChars) {
    if (in.size() <= 1) return in;

    List<SectionBuilder> out = new ArrayList<>();
    int i = 0;
    while (i < in.size()) {
      SectionBuilder cur = in.get(i);

      // if too short and there is a next section, merge into next
      if (cur.text().length() < minChars && i + 1 < in.size()) {
        SectionBuilder next = in.get(i + 1);
        // prepend cur content to next (keep metadata of next §, but we attach note)
        next.prepend(cur.text() + "\n");
        next.mergedFrom.add(cur.sectionNo);
        i++; // skip cur, keep next for normal processing
        continue;
      }

      out.add(cur);
      i++;
    }
    return out;
  }

  private List<Document> splitLargeSection(SectionBuilder sb, int maxChars, int maxSubchunks) {
    String full = sb.text();
    List<Document> out = new ArrayList<>();

    int start = 0;
    int idx = 0;
    while (start < full.length() && idx < maxSubchunks) {
      int end = Math.min(full.length(), start + maxChars);

      // try split at nearest newline
      int nl = full.lastIndexOf('\n', end);
      if (nl > start + (maxChars / 2)) end = nl;

      String part = full.substring(start, end).trim();
      if (!part.isBlank()) {
        out.add(sb.build(part, idx, -1)); // total computed later
      }

      start = end;
      idx++;
    }

    // set total_subchunks
    int total = out.size();
    for (int i = 0; i < total; i++) {
      Map<String, Object> md = new HashMap<>(out.get(i).getMetadata());
      md.put("subchunk_index", i);
      md.put("subchunk_total", total);
      out.set(i, new Document(out.get(i).getText(), md));
    }

    return out;
  }

  private static final class SectionBuilder {
    final Map<String, Object> baseMd;
    final StringBuilder body = new StringBuilder();

    String chapterNo = "";
    String chapterTitle = "";
    String sectionNo = "";

    final List<String> mergedFrom = new ArrayList<>();

    SectionBuilder(Map<String, Object> baseMd) {
      this.baseMd = baseMd;
    }

    void appendLine(String line) {
      if (line == null) return;
      if (line.isBlank()) {
        // Keep at most one blank line in a row
        int len = body.length();
        if (len >= 2 && body.charAt(len - 1) == '\n' && body.charAt(len - 2) == '\n') return;
        body.append("\n");
        return;
      }
      body.append(line).append("\n");
    }

    void prepend(String prefix) {
      body.insert(0, prefix);
    }

    boolean hasContent() {
      return body.length() > 0 && !text().isBlank();
    }

    String text() {
      return body.toString().trim();
    }

    Document build(String text, int subchunkIndex, int subchunkTotal) {
      Map<String, Object> md = new HashMap<>(baseMd);

      md.put("segment_type", "SECTION");
      md.put("chapter_no", chapterNo);
      md.put("chapter_title", chapterTitle);
      md.put("section_no", sectionNo);

      if (!mergedFrom.isEmpty()) {
        md.put("merged_from_sections", String.join(",", mergedFrom));
      }

      if (subchunkTotal > 0) {
        md.put("subchunk_index", subchunkIndex);
        md.put("subchunk_total", subchunkTotal);
      }

      return new Document(text, md);
    }
  }
}
