package com.pujian.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Official ElevenLabs Music v2 composition-plan and binary-audio adapter. */
public class ElevenLabsMusicClient {
    public static final String ENDPOINT = "https://api.elevenlabs.io/v1/music";
    private final HttpClient client;
    private final URI uri;
    private final ObjectMapper mapper;
    public ElevenLabsMusicClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), URI.create(ENDPOINT));
    }
    ElevenLabsMusicClient(ObjectMapper mapper, HttpClient client, URI uri) {
        this.mapper = mapper; this.client = client; this.uri = uri;
    }
    public static boolean isOfficial(String endpoint) {
        return ENDPOINT.equals(endpoint) || (ENDPOINT + "/").equals(endpoint);
    }
    static Map<String, Object> payload(Song song, String model, String arrangement) {
        if (!Set.of("music_v2", "music_v2_5").contains(model))
            throw new ApiException(HttpStatus.BAD_REQUEST, "ElevenLabs 请选择 music_v2 或 music_v2_5 模型");
        List<List<String>> groups = new ArrayList<>();
        List<String> group = new ArrayList<>();
        for (String raw : song.lyrics().split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "歌词每行不能超过 200 字，请换行后再生成");
            if ((line.startsWith("[") && line.endsWith("]") && !group.isEmpty()) || group.size() >= 30) {
                groups.add(group); group = new ArrayList<>();
            }
            group.add(line);
        }
        if (!group.isEmpty()) groups.add(group);
        if (groups.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请先填写歌词");
        var chunks = new ArrayList<Map<String, Object>>();
        int total = 0;
        String genre = Map.of("流行", "pop", "说唱", "hip hop", "古典", "classical", "民谣", "folk", "电子", "electronic", "爵士", "jazz").getOrDefault(song.style(), song.style());
        for (var lines : groups) {
            int duration = Math.min(120000, Math.max(12000, lines.size() * 4500));
            total += duration;
            chunks.add(Map.of("text", String.join("\n", lines), "duration_ms", duration,
                "positive_styles", List.of(genre, song.mood(), arrangement, "Mandarin vocals", "clear vocals", "cohesive arrangement", "studio production"),
                "negative_styles", List.of()));
        }
        if (total > 600000) throw new ApiException(HttpStatus.BAD_REQUEST, "歌词过长，预计时长超过 10 分钟，请拆成多个作品生成");
        return Map.of("model_id", model, "composition_plan", Map.of("chunks", chunks));
    }
    public byte[] generate(Song song, String model, String key, String arrangement) {
        if (key == null || key.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "请在 AI 模型配置中填写 ElevenLabs API Key");
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(8))
                .header("xi-api-key", key).header("Content-Type", "application/json").header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload(song, model, arrangement)))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                int code = response.statusCode();
                if (code < 200 || code >= 300) {
                    String message = switch(code) {
                        case 401 -> "ElevenLabs 密钥无效或已失效，请更新 API Key";
                        case 402 -> "ElevenLabs 额度不足或套餐不支持音乐生成，请检查账户";
                        case 403 -> "ElevenLabs 拒绝访问，请检查密钥的 Music 权限和账户权限";
                        case 422 -> "ElevenLabs 不接受当前歌词、模型或编曲参数，请调整后重试";
                        case 429 -> "ElevenLabs 请求频率或并发数已达上限，请稍后重试";
                        default -> "ElevenLabs 音乐生成失败（HTTP " + code + "），请稍后重试";
                    };
                    throw new ApiException(HttpStatus.BAD_GATEWAY, message);
                }
                String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!type.startsWith("audio/") && !type.startsWith("application/octet-stream"))
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "ElevenLabs 未返回音频文件");
                byte[] audio = stream.readNBytes(30 * 1024 * 1024 + 1);
                if (audio.length == 0 || audio.length > 30 * 1024 * 1024)
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "ElevenLabs 返回的音频为空或超过 30 MB");
                return audio;
            }
        } catch (ApiException error) { throw error; }
        catch (HttpTimeoutException error) { throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "ElevenLabs 生成超时，请稍后检查账户用量再决定是否重试"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.BAD_GATEWAY, "音乐生成已中断"); }
        catch (Exception error) { throw new ApiException(HttpStatus.BAD_GATEWAY, "无法连接 ElevenLabs，请检查网络后重试"); }
    }
}
