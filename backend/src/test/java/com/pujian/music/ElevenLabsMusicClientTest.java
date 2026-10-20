package com.pujian.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElevenLabsMusicClientTest {
    private Song song(String lyrics) {
        return new Song("test", "回声", "流行", "温暖", "重逢", "雨夜", lyrics, "钢琴", null, Instant.now(), Instant.now());
    }
    @Test void sendsExactEditedLyricsWithOfficialAuthAndReturnsBinaryAudio() throws Exception {
        var mapper = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var payload = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var bearer = new AtomicReference<String>();
        byte[] mp3 = new byte[]{73,68,51,4,0,0};
        server.createContext("/v1/music", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("xi-api-key"));
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, mp3.length);
            exchange.getResponseBody().write(mp3); exchange.close();
        });
        server.start();
        try {
            var client = new ElevenLabsMusicClient(mapper, HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/music"));
            assertArrayEquals(mp3, client.generate(song("[主歌]\n修改后的第一句\n[副歌]\n保留我的歌词"), "music_v2_5", "test-key", "gentle piano"));
            var body = mapper.readTree(payload.get());
            assertEquals("test-key", auth.get()); assertNull(bearer.get());
            assertEquals("music_v2_5", body.path("model_id").asText());
            assertFalse(body.has("prompt"));
            var chunks = body.path("composition_plan").path("chunks");
            assertEquals(2, chunks.size());
            assertEquals("[主歌]\n修改后的第一句", chunks.get(0).path("text").asText());
            assertEquals("[副歌]\n保留我的歌词", chunks.get(1).path("text").asText());
        } finally { server.stop(0); }
    }
    @Test void reportsInvalidKeyWithoutExposingResponseBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/music", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        try {
            var client = new ElevenLabsMusicClient(new ObjectMapper(), HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/music"));
            var error = assertThrows(ApiException.class, () -> client.generate(song("歌词"), "music_v2", "test-secret", "pop"));
            assertTrue(error.getMessage().contains("密钥无效"));
            assertFalse(error.getMessage().contains("test-secret"));
        } finally { server.stop(0); }
    }
    @Test void rejectsOversizedLinesRatherThanDroppingLyrics() {
        assertThrows(ApiException.class, () -> ElevenLabsMusicClient.payload(song("字".repeat(201)), "music_v2_5", "pop"));
        assertThrows(ApiException.class, () -> ElevenLabsMusicClient.payload(song("歌词"), "deepseek-v4-pro", "pop"));
    }
}
