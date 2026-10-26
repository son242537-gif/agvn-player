/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** An Electron game's page for "Chạy nhẹ": its app.asar read in place, and the page its main script opens (26/10/2026). */
public class AgvnElectronGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** An app.asar as Electron's asar tool writes it; names in {@code unpacked} go to app.asar.unpacked/. */
    static void asar(File out, Map<String, byte[]> files, String... unpacked) throws IOException {
        JsonObject root = new JsonObject();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            JsonObject dir = root;
            String[] parts = e.getKey().split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if (!dir.has("files")) dir.add("files", new JsonObject());
                JsonObject children = dir.getAsJsonObject("files");
                if (!children.has(parts[i])) children.add(parts[i], new JsonObject());
                dir = children.getAsJsonObject(parts[i]);
            }
            if (!dir.has("files")) dir.add("files", new JsonObject());
            JsonObject file = new JsonObject();
            file.addProperty("size", e.getValue().length);
            if (Arrays.asList(unpacked).contains(e.getKey())) {
                file.addProperty("unpacked", true);
                File u = new File(out.getPath() + ".unpacked", e.getKey());
                u.getParentFile().mkdirs();
                Files.write(u.toPath(), e.getValue());
            } else {
                file.addProperty("offset", String.valueOf(data.size()));
                data.write(e.getValue());
            }
            dir.getAsJsonObject("files").add(parts[parts.length - 1], file);
        }
        byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
        int padded = (json.length + 3) & ~3, payload = 4 + padded, size = 4 + payload;
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        for (int v : new int[]{4, size, payload, json.length}) f.write(new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)});
        f.write(json);
        f.write(new byte[padded - json.length]);
        f.write(data.toByteArray());
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), f.toByteArray());
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] all(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[3];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private File electronGame(String name) throws IOException {
        File dir = tmp.newFolder(name);
        for (String f : new String[]{"Game.exe", "chrome_100_percent.pak", "v8_context_snapshot.bin"}) new File(dir, f).createNewFile();
        return dir;
    }

    @Test
    public void anAsarIsReadInPlaceIgnoringCase() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("package.json", utf8("{\"name\":\"g\",\"main\":\"main.js\"}"));
        files.put("index.html", utf8("<html><head></head></html>"));
        files.put("res/Hero.png", new byte[]{1, 2, 3, 4, 5, 6, 7});
        files.put("src/settings.js", utf8("window._CCSettings = {};"));
        files.put("big.bin", new byte[]{9, 9});
        File archive = new File(tmp.getRoot(), "app.asar");
        asar(archive, files, "big.bin");
        AgvnAsar a = AgvnAsar.open(archive);
        assertEquals("res/Hero.png", a.find("/RES/./hero.PNG"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7}, all(a.open("res/Hero.png")));
        assertEquals("window._CCSettings = {};", a.text("src/settings.js", 1000));
        assertEquals("cut at its limit", "window", a.text("src/settings.js", 6));
        assertArrayEquals("from app.asar.unpacked", new byte[]{9, 9}, all(a.open(a.find("big.bin"))));
        assertNull(a.find("../index.html"));
        assertNull(a.find("res"));
        assertNull(a.text("missing.js", 10));
        assertTrue(AgvnAsar.of(archive) == AgvnAsar.of(archive));
        assertEquals(archive, AgvnAsar.archiveOf(new File(archive, "src/settings.js")));
        assertEquals("src/settings.js", AgvnAsar.pathIn(archive, new File(archive, "src/settings.js")));
    }

    @Test
    public void notAnAsar() throws Exception {
        File f = tmp.newFile("app.asar");
        Files.write(f.toPath(), utf8("this is no asar at all"));
        assertNull(AgvnAsar.of(f));
    }

    @Test
    public void thePageOfAPackedApp() throws Exception {
        File game = electronGame("packed");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("package.json", utf8("{\"main\": \"./electron/main.js\"}"));
        files.put("electron/main.js", utf8("const w = new BrowserWindow({width: 960});\n"
                + "w.loadURL(isDev ? 'http://localhost:7456' : url.format({pathname: path.join(__dirname, '..', 'web-desktop', "
                + "'index.html'), protocol: 'file:', slashes: true}));"));
        files.put("web-desktop/index.html", utf8("<html></html>"));
        asar(new File(game, "resources/app.asar"), files);
        File page = AgvnElectronGame.page(game);
        assertEquals(new File(game, "resources/app.asar/web-desktop/index.html"), page);
        assertFalse(page.isFile());
        assertTrue(AgvnElectronGame.exists(page));
        assertFalse(AgvnElectronGame.exists(new File(game, "resources/app.asar/nope.html")));
        assertEquals(game, AgvnElectronGame.gameDirOf(page));
        assertEquals(page, AgvnElectronGame.pageOf(game));
        // the page's folder in the archive is the root its URLs start from
        AgvnHtmlAsar served = AgvnHtmlAsar.forPage(page);
        assertEquals("web-desktop/index.html", served.find("/"));
        assertEquals("web-desktop/index.html", served.find("/Index%2Ehtml"));
        assertNull("not above the page's folder", served.find("/../package.json"));
        assertNull(AgvnHtmlAsar.forPage(new File(game, "resources/app.asar/nope.html")));
        assertEquals("one host per game, not per app.asar", AgvnHtmlGame.hostFor(new File(game, "x.html")),
                AgvnHtmlGame.hostFor(page));
    }

    @Test
    public void thePageOfAnUnpackedApp() throws Exception {
        File game = electronGame("unpacked");
        File app = new File(game, "resources/app");
        new File(app, "game").mkdirs();
        Files.write(new File(app, "package.json").toPath(), utf8("{\"main\":\"boot.js\"}"));
        Files.write(new File(app, "boot.js").toPath(), utf8("win.loadFile('game/Index.html', {query: {lang: 'vi'}})"));
        Files.write(new File(app, "game/index.html").toPath(), utf8("<html></html>"));
        assertEquals("found ignoring case", new File(app, "game/index.html"), AgvnElectronGame.page(game));
        assertEquals(game, AgvnElectronGame.gameDirOf(new File(app, "game")));
        assertNull("no Electron above it", AgvnElectronGame.gameDirOf(tmp.newFolder("other", "resources", "app")));
    }

    @Test
    public void noPageForAWebAppOrAnotherGame() throws Exception {
        File game = electronGame("web");
        File app = new File(game, "resources/app");
        app.mkdirs();
        Files.write(new File(app, "package.json").toPath(), utf8("{\"main\":\"main.js\"}"));
        Files.write(new File(app, "main.js").toPath(), utf8("win.loadURL('https://example.com/play')"));
        assertNull(AgvnElectronGame.page(game));
        assertNull(AgvnElectronGame.page(tmp.newFolder("unity")));
    }

    @Test
    public void thePagesAMainScriptOpens() {
        assertEquals("index.html", AgvnElectronMain.pageIn("`file://${__dirname}/index.html`"));
        assertEquals("index.html", AgvnElectronMain.pageIn("'file://' + __dirname + '/index.html'"));
        assertEquals("www/index.html", AgvnElectronMain.pageIn("path.join(__dirname, 'www', 'index.html')"));
        assertEquals("../build/index.html", AgvnElectronMain.pageIn("`file://${path.join(__dirname, '../build/index.html')}`"));
        assertEquals("index.html", AgvnElectronMain.pageIn("\"index.html?debug=1\""));
        assertNull(AgvnElectronMain.pageIn("process.env.START_URL"));
        assertNull(AgvnElectronMain.pageIn("'https://example.com/index.html'"));
        assertEquals(Arrays.asList("build/index.html", "electron/index.html", "index.html"),
                AgvnElectronMain.pages("electron/main.js", "w.loadURL(`file://${path.join(__dirname, '../build/index.html')}`)"));
        assertEquals(Arrays.asList("page.html", "index.html"), AgvnElectronMain.pages("main.js", "a.loadFile(\"page.html\")"));
        assertEquals("index.js", AgvnElectronMain.mainOf("{\"name\": \"g\"}"));
        assertNull(AgvnElectronMain.normalize("../x.html"));
    }
}
