package com.pujian.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AceStepMusicClientTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private Song song() {
        return new Song("test", "回声", "流行", "温暖", "重逢", "雨夜",
            "[主歌]\n我修改过的歌词\n[副歌]\n请原样保留", "女声钢琴", null, Instant.now(), Instant.now());
    }
    private AceStepMusicClient client(Duration timeout) {
        return new AceStepMusicClient(mapper, HttpClient.newHttpClient(), timeout, Duration.ofMillis(5));
    }
    private void respond(com.sun.net.httpserver.HttpExchange exchange, Object data) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(Map.of("code", 200, "data", data));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    @Test void submitsExactLyricsPollsAndDownloadsAudio() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var payload = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var count = new AtomicInteger();
        byte[] wav = "RIFF0000WAVEtest-audio".getBytes(StandardCharsets.US_ASCII);
        server.createContext("/release_task", e -> {
            if (e.getRequestHeaders().getFirst("Upgrade") != null) {
                e.sendResponseHeaders(400, -1); e.close(); return;
            }
            payload.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(e.getRequestHeaders().getFirst("Authorization"));
            respond(e, Map.of("task_id", "task-1"));
        });
        server.createContext("/query_result", e -> {
            int state = count.incrementAndGet() == 1 ? 0 : 1;
            respond(e, java.util.List.of(Map.of("task_id", "task-1", "status", state,
                "result", "[{\"file\":\"/v1/audio?path=test.wav\"}]")));
        });
        server.createContext("/v1/audio", e -> {
            e.getResponseHeaders().set("Content-Type", "audio/wav");
            e.sendResponseHeaders(200, wav.length); e.getResponseBody().write(wav); e.close();
        });
        server.start();
        try {
            var progress = new java.util.ArrayList<String>();
            var result = client(Duration.ofSeconds(3)).generate(song(), "http://127.0.0.1:" + server.getAddress().getPort(),
                "acestep-v15-turbo", "local-test-key", 30, progress::add);
            assertArrayEquals(wav, result);
            var body = mapper.readTree(payload.get());
            assertEquals(song().lyrics(), body.path("lyrics").asText());
            assertEquals(30, body.path("audio_duration").asInt());
            assertEquals(1, body.path("batch_size").asInt());
            assertFalse(body.path("thinking").asBoolean());
            assertEquals("wav", body.path("audio_format").asText());
            assertTrue(body.path("prompt").asText().contains("女声钢琴"));
            assertEquals("Bearer local-test-key", auth.get());
            assertEquals(2, count.get());
            assertTrue(progress.stream().anyMatch(s -> s.contains("音频")));
        } finally { server.stop(0); }
    }
    @Test void rejectsFailedTasksAndDoesNotLeakProviderDetails() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/release_task", e -> respond(e, Map.of("task_id", "task-1")));
        server.createContext("/query_result", e -> respond(e, java.util.List.of(Map.of("status", 2, "result", "secret-detail"))));
        server.start();
        try {
            var error = assertThrows(ApiException.class, () -> client(Duration.ofSeconds(2)).generate(song(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "acestep-v15-turbo", "", 30, s -> {}));
            assertTrue(error.getMessage().contains("失败"));
            assertFalse(error.getMessage().contains("secret-detail"));
        } finally { server.stop(0); }
    }
    @Test void timesOutPendingTask() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/release_task", e -> respond(e, Map.of("task_id", "task-1")));
        server.createContext("/query_result", e -> respond(e, java.util.List.of(Map.of("status", 0))));
        server.start();
        try {
            var error = assertThrows(ApiException.class, () -> client(Duration.ofMillis(150)).generate(song(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "acestep-v15-turbo", "", 30, s -> {}));
            assertTrue(error.getMessage().contains("超时"));
        } finally { server.stop(0); }
    }
    @Test void rejectsForeignAudioUrlBeforeSendingCredentials() {
        assertThrows(ApiException.class, () -> AceStepMusicClient.audioUri("http://127.0.0.1:8001", "https://other.example/audio"));
        assertThrows(ApiException.class, () -> AceStepMusicClient.audioUri("http://127.0.0.1:8001", "file:///secret"));
    }
    @Test void streamsLongAudioAndUsesRequestedDuration() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var duration = new AtomicInteger();
        server.createContext("/release_task", e -> {
            duration.set(mapper.readTree(e.getRequestBody()).path("audio_duration").asInt());
            respond(e, Map.of("task_id", "long-task"));
        });
        server.createContext("/query_result", e -> respond(e, java.util.List.of(Map.of("status", 1,
            "result", "[{\"file\":\"/v1/audio?path=long.wav\"}]"))));
        server.createContext("/v1/audio", e -> {
            e.sendResponseHeaders(200, 33 * 1024 * 1024 + 12);
            e.getResponseBody().write("RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII));
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 33; i++) e.getResponseBody().write(chunk);
            e.getResponseBody().write(0);
            e.close();
        });
        server.start();
        try {
            var output = temp.resolve("long.wav");
            client(Duration.ofSeconds(10)).generateToFile(song(), "http://127.0.0.1:" + server.getAddress().getPort(),
                "acestep-v15-turbo", "", 180, output, s -> {});
            assertEquals(180, duration.get());
            assertTrue(java.nio.file.Files.size(output) > 30L * 1024 * 1024);
        } finally { server.stop(0); }
    }
}
