package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/** The customer guide must stay complete and only quote button labels the app really shows. */
public class AgvnGuideStringsTest {
    private static final String[] TOPICS = {"add", "play", "graphics", "controls", "mouse", "trouble", "expert"};
    private static final File RES = new File("src/main/res/values");
    private static final File JAVA = new File("src/main/java");

    @Test
    public void everyTopicHasTitleSummaryAndSteps() throws Exception {
        Document doc = parse(new File(RES, "agvn_guide_strings.xml"));
        for (String topic : TOPICS) {
            assertTrue(topic + " title", hasString(doc, "agvn_guide_" + topic + "_title"));
            assertTrue(topic + " summary", hasString(doc, "agvn_guide_" + topic + "_summary"));
            assertTrue(topic + " steps", steps(doc, topic).size() >= 3);
        }
    }

    @Test
    public void quotedLabelsExistInTheApp() throws Exception {
        Document doc = parse(new File(RES, "agvn_guide_strings.xml"));
        StringBuilder ui = new StringBuilder();
        for (String name : new String[]{"strings.xml", "agvn_strings.xml"}) ui.append(read(new File(RES, name)));
        appendSources(JAVA, ui);
        String haystack = ui.toString().replace("\\\"", "\"");
        List<String> missing = new ArrayList<>();
        Pattern quoted = Pattern.compile("\"([^\"]+)\"");
        for (String topic : TOPICS) {
            for (String step : steps(doc, topic)) {
                Matcher m = quoted.matcher(step);
                while (m.find()) {
                    String label = m.group(1);
                    if (!haystack.contains(label)) missing.add(topic + ": " + label);
                }
            }
        }
        assertEquals("guide quotes labels the app does not show", new ArrayList<String>(), missing);
    }

    private static List<String> steps(Document doc, String topic) {
        List<String> out = new ArrayList<>();
        NodeList arrays = doc.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (!("agvn_guide_" + topic + "_steps").equals(array.getAttribute("name"))) continue;
            NodeList items = array.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) out.add(items.item(j).getTextContent().replace("\\\"", "\""));
        }
        return out;
    }

    private static boolean hasString(Document doc, String name) {
        NodeList strings = doc.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element e = (Element) strings.item(i);
            if (name.equals(e.getAttribute("name")) && !e.getTextContent().trim().isEmpty()) return true;
        }
        return false;
    }

    private static void appendSources(File dir, StringBuilder out) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) appendSources(f, out);
            else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) out.append(read(f));
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static Document parse(File f) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
    }
}
