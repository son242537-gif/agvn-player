/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.Gson;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The problems of assets/agvn/game-problems.json, and which one a game's end shows: the first whose engine, "when"
 * conditions and log lines all hold. Pure Java (JVM-testable).
 */
final class AgvnProblemCatalog {
    static final String ASSET = "agvn/game-problems.json";
    private static final List<String> DEFAULT_WHEN = Collections.singletonList("failed");

    /** One problem as the file writes it. */
    static final class Problem {
        String id, title, cause;
        List<String> when, engines, lines, fixes;
        /**
         * Lines that name the problem only when the game never started ({@link AgvnEvidence#noStart}): a game prints
         * DXVK's "Minimum required feature level D3D_FEATURE_LEVEL_11_1 not supported", then runs on 11_0; with the
         * line among "lines", every later crash of it read "Game báo thiếu DirectX" (audit of 20/11/2026).
         */
        List<String> linesNoStart;
        transient List<Pattern> patterns = Collections.emptyList(), noStartPatterns = Collections.emptyList();
    }

    /** A problem found, with the values its texts fill in ({1}, {crash}, {fps}, {gpu}, {cpu}). */
    static final class Finding {
        final Problem problem;
        final Map<String, String> params;

        Finding(Problem problem, Map<String, String> params) {
            this.problem = problem;
            this.params = params;
        }

        String id() {
            return problem.id;
        }

        String title() {
            return fill(problem.title, params);
        }

        String cause() {
            return fill(problem.cause, params);
        }

        List<String> fixes() {
            return problem.fixes != null ? problem.fixes : Collections.emptyList();
        }
    }

    private static final class Json {
        List<Problem> problems;
    }

    private final List<Problem> problems;

    private AgvnProblemCatalog(List<Problem> problems) {
        this.problems = problems;
    }

    /** Throws on a file that is not JSON or holds a bad pattern: a broken catalog must fail its unit test. */
    static AgvnProblemCatalog parse(Reader json) {
        Json file = new Gson().fromJson(json, Json.class);
        List<Problem> list = file != null && file.problems != null ? file.problems : new ArrayList<>();
        for (Problem p : list) {
            p.patterns = compiled(p.lines);
            p.noStartPatterns = compiled(p.linesNoStart);
        }
        return new AgvnProblemCatalog(list);
    }

    private static List<Pattern> compiled(List<String> lines) {
        List<Pattern> compiled = new ArrayList<>();
        if (lines != null) for (String re : lines) compiled.add(Pattern.compile(re, Pattern.CASE_INSENSITIVE));
        return compiled;
    }

    /**
     * The titles and causes of {@code json} (game-problems-&lt;lang&gt;.json: {"problems": {"id": {"title", "cause"}}})
     * in place of the Vietnamese ones, problem by problem; one it lacks stays Vietnamese. Never throws.
     */
    void localize(Reader json) {
        try {
            com.google.gson.JsonObject texts = new Gson().fromJson(json, com.google.gson.JsonObject.class)
                    .getAsJsonObject("problems");
            for (Problem p : problems) {
                com.google.gson.JsonObject t = texts != null ? texts.getAsJsonObject(p.id) : null;
                if (t == null) continue;
                if (t.has("title")) p.title = t.get("title").getAsString();
                if (t.has("cause")) p.cause = t.get("cause").getAsString();
            }
        } catch (RuntimeException e) {
            // the Vietnamese texts stay
        }
    }

    List<Problem> all() {
        return Collections.unmodifiableList(problems);
    }

    Problem byId(String id) {
        for (Problem p : problems) if (p.id.equals(id)) return p;
        return null;
    }

    /** The finding with this problem's texts and the given values, or null when the catalog has no such problem. */
    Finding finding(String id, Map<String, String> params) {
        Problem p = byId(id);
        return p != null ? new Finding(p, new LinkedHashMap<>(params)) : null;
    }

    /** The first problem that holds for {@code ev}, or null when the game ended well. */
    Finding find(AgvnEvidence ev) {
        for (Problem p : problems) {
            if (p.engines != null && !p.engines.contains(ev.engine)) continue;
            if (!ev.holdsAll(p.when != null ? p.when : DEFAULT_WHEN)) continue;
            Map<String, String> params = new LinkedHashMap<>(ev.params);
            if (!p.patterns.isEmpty() || !p.noStartPatterns.isEmpty()) {
                Matcher m = firstMatch(p.patterns, ev.lines);
                if (m == null && ev.noStart()) m = firstMatch(p.noStartPatterns, ev.lines);
                if (m == null) continue;
                if (m.groupCount() >= 1 && m.group(1) != null) params.put("1", m.group(1));
            }
            return new Finding(p, params);
        }
        return null;
    }

    private static Matcher firstMatch(List<Pattern> patterns, List<String> lines) {
        for (String line : lines) {
            for (Pattern pattern : patterns) {
                Matcher m = pattern.matcher(line);
                if (m.find()) return m;
            }
        }
        return null;
    }

    /** {@code text} with each "{name}" replaced by its value; a name without one becomes "?". */
    static String fill(String text, Map<String, String> params) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i), close = open < 0 ? -1 : text.indexOf('}', open);
            if (open < 0 || close < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, open);
            String value = params.get(text.substring(open + 1, close));
            out.append(value != null ? value : "?");
            i = close + 1;
        }
        return out.toString();
    }
}
