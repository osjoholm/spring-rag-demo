package ax.sjoholm.srd.services.ingestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class HeadingNormalizer {

  // E 1 Kommunallag (1997:73) för landskapet
  private static final Pattern LAW_HEADING_CORE = Pattern.compile(
      "(?i)^\\s*[A-ZÅÄÖ]\\s*\\d+\\s+.+\\(\\s*\\d{4}\\s*:\\s*\\d+\\s*\\)\\s*.*$"
  );

  private static final Pattern CHAPTER = Pattern.compile(
      "(?i)^\\s*\\d+\\s*kap\\..*$"
  );

  private static final Pattern SECTION = Pattern.compile(
      "(?i)^\\s*\\d+[a-zA-Z]?\\s*§\\.?\\s*.*$"
  );

  private static final Pattern NEW_STRUCTURE = Pattern.compile(
      "(?i)^\\s*(\\d+\\s*kap\\.|\\d+[a-zA-Z]?\\s*§\\.?\\s*).*$"
  );

  // list items inside a paragraph: "1)" or "1."
  private static final Pattern LIST_ITEM = Pattern.compile(
      "^\\s*\\d+\\)\\s+.*$"
  );

  public static String normalizeHeadings(String text) {
    if (text == null || text.isBlank()) return "";

    List<String> lines = new ArrayList<>(
        Arrays.asList(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1))
    );

    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < lines.size(); i++) {
      String line = rstrip(lines.get(i));
      if (line.isBlank()) {
        out.append("\n");
        continue;
      }

      // join LAW heading continuation lines
      if (LAW_HEADING_CORE.matcher(line).matches() && i + 1 < lines.size()) {
        String next = lines.get(i + 1).trim();
        if (shouldJoinHeadingContinuation(next)) {
          line = join(line, next);
          i++; // consume next
        }
      }

      // chapter heading continuation
      if (CHAPTER.matcher(line).matches() && i + 1 < lines.size()) {
        String next = lines.get(i + 1).trim();
        // chapter continuations often start lowercase or are short fragments
        if (!next.isBlank()
            && !NEW_STRUCTURE.matcher(next).matches()
            && !LIST_ITEM.matcher(next).matches()
            && looksLikeHeadingContinuation(next)) {
          line = join(line, next);
          i++;
        }
      }

      // section heading continuation
      if (SECTION.matcher(line).matches() && i + 1 < lines.size()) {
        // Example: "4 §. Förhållandet mellan ... och" + "kommunerna"
        String next = lines.get(i + 1).trim();
        if (!next.isBlank()
            && !NEW_STRUCTURE.matcher(next).matches()
            && !LIST_ITEM.matcher(next).matches()
            && looksLikeHeadingContinuation(next)) {
          line = join(line, next);
          i++;
        }
      }

      out.append(line).append("\n");
    }

    return out.toString();
  }

  private static boolean shouldJoinHeadingContinuation(String next) {
    if (next == null || next.isBlank()) return false;
    if (NEW_STRUCTURE.matcher(next).matches()) return false;
    if (LIST_ITEM.matcher(next).matches()) return false;
    return looksLikeHeadingContinuation(next);
  }

  private static boolean looksLikeHeadingContinuation(String next) {
    if (next.length() > 90) return false;
    char c0 = next.charAt(0);
    boolean startsLower = Character.isLetter(c0) && Character.isLowerCase(c0);
    boolean isShortSingleWord = next.length() <= 25 && !next.contains(" ");
    return startsLower || isShortSingleWord;
  }

  private static String join(String a, String b) {
    a = a.trim();
    b = b.trim();
    if (a.endsWith("-")) {
      return a.substring(0, a.length() - 1) + b;
    }
    return a + " " + b;
  }

  private static String rstrip(String s) {
    if (s == null) return "";
    int end = s.length();
    while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
    return s.substring(0, end);
  }

  private HeadingNormalizer() {}
}