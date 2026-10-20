package com.pujian.music;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioServiceTest {
    @TempDir Path temp;

    @Test
    void sendsEditedLyricsAndStoresGeneratedAudio() throws Exception {
        var received = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/music", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var encoded = Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3, 4 });
            var response = ("{\"audioBase64\":\"" + encoded + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            var store = new SongStore(temp.toString(), mapper);
            var song = store.create(new SongInput("回声", "流行", "温暖", "重逢", "", "手动修改过的歌词", "女声钢琴"));
            var audio = new AudioService("http://127.0.0.1:" + server.getAddress().getPort() + "/music",
                "", temp.toString(), store, new AgentStudioService(""), mapper);
            var generated = audio.generate(song.id());

            assertTrue(received.get().contains("手动修改过的歌词"));
            assertTrue(generated.audioUrl().startsWith("/api/songs/"));
            assertArrayEquals(new byte[] { 1, 2, 3, 4 }, audio.read(song.id()).bytes());
        } finally { server.stop(0); }
    }
}
