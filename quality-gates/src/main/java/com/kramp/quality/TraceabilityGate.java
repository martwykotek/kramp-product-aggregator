package com.kramp.quality;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks that every requirement in {@code specs/**} states how it is proven and that every claimed
 * proof resolves: automated proofs to a {@code // proves:} marker in test sources, observed proofs
 * to a TC row in CR-001. A requirements-to-proof gate, plus an orphan-marker check.
 */
public final class TraceabilityGate {

  static final String ID = "(?:REQ|RC)-[A-Z]+(?:-[A-Z_]+)*-\\d{3}";
  private static final Pattern ID_ONLY = Pattern.compile("^" + ID + "$");
  private static final Pattern OWNS = Pattern.compile("^Owns:\\s*(.+)$");
  private static final Pattern BULLET = Pattern.compile("^- (" + ID + ") — .+$");
  private static final Pattern ROW = Pattern.compile("^\\|\\s*(" + ID + ")\\s*\\|(.*)\\|\\s*$");
  private static final Pattern MARKER = Pattern.compile("//\\s*proves:\\s*(.+)$");
  // Validation steps may carry a letter suffix (TC-11a, TC-11b).
  private static final Pattern TC_ROW = Pattern.compile("^\\|\\s*(TC-\\d{2}[a-z]?):");
  private static final Pattern TC_REF = Pattern.compile("TC-\\d{2}[a-z]?");
  private static final String GAP = "❌ no evidence — GAP";

  /** One gate failure, printed as a fix-it line. */
  public record Problem(String id, String location, String message) {
    @Override
    public String toString() {
      return "✗ proof: " + id + " (" + location + ") " + message;
    }
  }

  /** Gate outcome. */
  public record Result(List<Problem> problems, int leaves, int requirements, int gaps) {
    public boolean passed() {
      return problems.isEmpty();
    }

    public String report() {
      StringBuilder out = new StringBuilder();
      problems.forEach(p -> out.append(p).append(System.lineSeparator()));
      out.append("proof: ")
          .append(passed() ? "PASS" : "FAIL")
          .append(" — ")
          .append(problems.size())
          .append(" problem(s), ")
          .append(requirements)
          .append(" requirement(s) across ")
          .append(leaves)
          .append(" leaf/leaves, ")
          .append(gaps)
          .append(gaps == 1 ? " gap" : " gaps");
      return out.toString();
    }
  }

  private record Row(String resolvedBy, String proof, String status, String location) {}

  private final Path repoRoot;
  private final boolean strict;

  public TraceabilityGate(Path repoRoot, boolean strict) {
    this.repoRoot = repoRoot;
    this.strict = strict;
  }

  public Result check() throws IOException {
    List<Problem> problems = new ArrayList<>();
    Set<String> testCases = readTestCases(problems);
    Map<String, Set<Path>> markers = scanMarkers(problems);

    Map<String, Row> rows = new LinkedHashMap<>();
    Set<String> declared = new HashSet<>();
    List<Path> leaves = listLeaves();
    for (Path leaf : leaves) {
      readLeaf(leaf, declared, rows, problems);
    }

    int gaps = 0;
    for (Map.Entry<String, Row> entry : rows.entrySet()) {
      String id = entry.getKey();
      Row row = entry.getValue();
      if (!declared.contains(id)) {
        problems.add(new Problem(id, row.location(), "has a row but no requirement bullet"));
        continue;
      }
      if (row.status().equals(GAP)) {
        gaps++;
        if (strict) {
          problems.add(new Problem(id, row.location(), "is an open gap (strict mode)"));
        }
      }
      checkRow(id, row, declared, markers, testCases, problems);
    }

    for (Map.Entry<String, Set<Path>> marker : markers.entrySet()) {
      if (!declared.contains(marker.getKey())) {
        problems.add(
            new Problem(
                marker.getKey(),
                repoRoot.relativize(marker.getValue().iterator().next()).toString(),
                "a proves: marker cites an id no leaf declares"));
      }
    }
    return new Result(List.copyOf(problems), leaves.size(), declared.size(), gaps);
  }

  private void checkRow(
      String id,
      Row row,
      Set<String> declared,
      Map<String, Set<Path>> markers,
      Set<String> testCases,
      List<Problem> problems) {
    String kind = row.resolvedBy();
    boolean knownKind =
        kind.equals("automated") || kind.equals("observed") || kind.startsWith("covered-by ");
    if (!knownKind) {
      problems.add(new Problem(id, row.location(), "unknown evidence kind '" + kind + "'"));
    }
    String status = row.status();
    boolean warning = status.startsWith("⚠️");
    boolean knownStatus =
        status.equals("✅") || warning || status.equals(GAP) || status.equals("de-scoped");
    if (!knownStatus) {
      problems.add(new Problem(id, row.location(), "unknown status '" + status + "'"));
      return;
    }
    if (warning && status.substring("⚠️".length()).isBlank()) {
      problems.add(new Problem(id, row.location(), "⚠️ status must name its limitation"));
    }
    boolean claimsProof = status.equals("✅") || warning;
    if (!claimsProof || !knownKind) {
      return;
    }
    if (kind.equals("automated") && !markers.containsKey(id)) {
      problems.add(
          new Problem(
              id,
              row.location(),
              "claims proof ("
                  + (warning ? "⚠️" : "✅")
                  + ") but no test carries a proves: marker for it"));
    }
    if (kind.equals("observed")) {
      Matcher tc = TC_REF.matcher(row.proof());
      if (!tc.find() || !testCases.contains(tc.group())) {
        problems.add(
            new Problem(
                id, row.location(), "observed proof must cite a TC-nn from CR-001 § Validation"));
      }
    }
    if (kind.startsWith("covered-by ")) {
      String target = kind.substring("covered-by ".length()).trim();
      if (target.equals(id) || !declared.contains(target)) {
        problems.add(
            new Problem(
                id, row.location(), "covered-by points to an unknown or own id: " + target));
      }
    }
  }

  private void readLeaf(
      Path leaf, Set<String> declared, Map<String, Row> rows, List<Problem> problems)
      throws IOException {
    String location = repoRoot.relativize(leaf).toString();
    List<String> lines = Files.readAllLines(leaf);
    List<String> owned = new ArrayList<>();
    for (String line : lines) {
      Matcher owns = OWNS.matcher(line.trim());
      if (owns.matches()) {
        for (String part : owns.group(1).split(",")) {
          owned.add(part.trim().replace("*", ""));
        }
      }
    }
    if (owned.isEmpty()) {
      problems.add(new Problem("-", location, "leaf has no Owns: line"));
    }
    for (String line : lines) {
      Matcher bullet = BULLET.matcher(line.trim());
      if (bullet.matches()) {
        String id = bullet.group(1);
        if (!declared.add(id)) {
          problems.add(new Problem(id, location, "is declared more than once"));
        }
        if (owned.stream().noneMatch(id::startsWith)) {
          problems.add(new Problem(id, location, "is outside the leaf's Owns: namespace"));
        }
        continue;
      }
      Matcher row = ROW.matcher(line.trim());
      if (row.matches()) {
        String[] cells = row.group(2).split("\\|", -1);
        if (cells.length != 3) {
          problems.add(new Problem(row.group(1), location, "row must have 4 columns"));
          continue;
        }
        rows.put(
            row.group(1), new Row(cells[0].trim(), cells[1].trim(), cells[2].trim(), location));
      }
    }
    Set<String> leafIds = new HashSet<>();
    for (String line : lines) {
      Matcher bullet = BULLET.matcher(line.trim());
      if (bullet.matches()) {
        leafIds.add(bullet.group(1));
      }
    }
    for (String id : leafIds) {
      if (!rows.containsKey(id)) {
        problems.add(new Problem(id, location, "has no row in How it's proven"));
      }
    }
  }

  private List<Path> listLeaves() throws IOException {
    Path specs = repoRoot.resolve("specs");
    if (!Files.isDirectory(specs)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(specs)) {
      return files
          .filter(p -> p.toString().endsWith(".md"))
          .filter(p -> !p.getFileName().toString().equals("index.md"))
          .sorted()
          .toList();
    }
  }

  private Set<String> readTestCases(List<Problem> problems) throws IOException {
    Path record = repoRoot.resolve(".changes/CR-001_product-aggregator.md");
    if (!Files.exists(record)) {
      problems.add(new Problem("-", ".changes", "CR-001_product-aggregator.md is missing"));
      return Set.of();
    }
    Set<String> cases = new HashSet<>();
    for (String line : Files.readAllLines(record)) {
      Matcher tc = TC_ROW.matcher(line.trim());
      if (tc.find()) {
        cases.add(tc.group(1));
      }
    }
    return cases;
  }

  private Map<String, Set<Path>> scanMarkers(List<Problem> problems) throws IOException {
    Map<String, Set<Path>> markers = new HashMap<>();
    try (Stream<Path> files = Files.walk(repoRoot)) {
      List<Path> testSources =
          files
              .filter(p -> p.toString().endsWith(".java"))
              .filter(p -> p.toString().contains("/src/test/java/"))
              .filter(p -> !p.toString().contains("/target/"))
              .toList();
      for (Path source : testSources) {
        for (String line : Files.readAllLines(source)) {
          Matcher marker = MARKER.matcher(line);
          if (!marker.find()) {
            continue;
          }
          for (String raw : marker.group(1).split(",")) {
            String id = raw.trim();
            if (!ID_ONLY.matcher(id).matches()) {
              problems.add(
                  new Problem(
                      id,
                      repoRoot.relativize(source).toString(),
                      "malformed id in proves: marker"));
              continue;
            }
            markers.computeIfAbsent(id, k -> new HashSet<>()).add(source);
          }
        }
      }
    }
    return markers;
  }
}
