package com.pujian.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
public class AiSettings {
    private final Path file;
    private final ObjectMapper mapper;
    private Config config;
    public record Config(String textModel, String textBaseUrl, String textApiKey,
                         String musicModel, String musicEndpoint, String musicApiKey,
                         String musicProvider, Integer musicDurationSeconds) {
        public Config(String textModel, String textBaseUrl, String textApiKey, String musicModel, String musicEndpoint, String musicApiKey) {
            this(textModel, textBaseUrl, textApiKey, musicModel, musicEndpoint, musicApiKey, null, 30);
        }
        public String provider() {
            return musicProvider == null || musicProvider.isBlank()
                ? (ElevenLabsMusicClient.isOfficial(musicEndpoint) ? "elevenlabs" : "generic") : musicProvider;
        }
        public int durationSeconds() { return musicDurationSeconds == null ? 30 : musicDurationSeconds; }
    }
    public record Update(String textModel, String textBaseUrl, String textApiKey,
                         String musicModel, String musicEndpoint, String musicApiKey,
                         boolean clearTextKey, boolean clearMusicKey,
                         String musicProvider, Integer musicDurationSeconds) {
        public Update(String textModel, String textBaseUrl, String textApiKey, String musicModel, String musicEndpoint,
                      String musicApiKey, boolean clearTextKey, boolean clearMusicKey) {
            this(textModel, textBaseUrl, textApiKey, musicModel, musicEndpoint, musicApiKey, clearTextKey, clearMusicKey, null, 30);
        }
    }

    public AiSettings(@Value("${studio.data-dir}") String dir, ObjectMapper mapper,
                      @Value("${studio.music-endpoint:}") String endpoint,
                      @Value("${studio.music-api-key:}") String key) {
        this.file = Path.of(dir).resolve("ai-settings.json");
        this.mapper = mapper;
        this.config = new Config("qwen-plus", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            System.getenv().getOrDefault("DASHSCOPE_API_KEY", ""), "", endpoint, key);
        if (Files.exists(file)) {
            try { this.config = mapper.readValue(file.toFile(), Config.class); }
            catch (Exception e) { throw new IllegalStateException("无法读取 AI 配置文件", e); }
        }
    }
    public synchronized Config current() { return config; }
    public synchronized Map<String, Object> publicView() {
        return Map.of("textModel", config.textModel(), "textBaseUrl", config.textBaseUrl(),
            "textKeyConfigured", !config.textApiKey().isBlank(), "musicModel", config.musicModel(),
            "musicEndpoint", config.musicEndpoint(), "musicKeyConfigured", !config.musicApiKey().isBlank(),
            "musicProvider", config.provider(), "musicDurationSeconds", config.durationSeconds());
    }
    public synchronized Map<String, Object> save(Update input) {
        String model = clean(input.textModel()), url = clean(input.textBaseUrl());
        if (model.isEmpty() || url.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请填写歌词模型名称和接口地址");
        validateUrl(url);
        String musicUrl = clean(input.musicEndpoint());
        if (!musicUrl.isEmpty()) validateUrl(musicUrl);
        if (!musicUrl.isEmpty() && "api.deepseek.com".equalsIgnoreCase(java.net.URI.create(musicUrl).getHost()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "DeepSeek 应填写在歌词模型中。歌曲生成需要单独的音乐服务；暂未接入时请留空歌曲接口。");
        String musicModel = clean(input.musicModel());
        String provider = clean(input.musicProvider());
        if (provider.isEmpty()) provider = ElevenLabsMusicClient.isOfficial(musicUrl) ? "elevenlabs" : "generic";
        if (!java.util.Set.of("elevenlabs", "acestep", "generic").contains(provider))
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择有效的音乐服务");
        int duration = input.musicDurationSeconds() == null ? 30 : input.musicDurationSeconds();
        if (provider.equals("acestep")) {
            if (musicUrl.isBlank() || musicModel.isBlank())
                throw new ApiException(HttpStatus.BAD_REQUEST, "请填写 ACE-Step 服务地址和模型名称");
            var uri = java.net.URI.create(musicUrl);
            if (uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
                throw new ApiException(HttpStatus.BAD_REQUEST, "ACE-Step 请填写服务根地址，例如 http://127.0.0.1:8001");
            musicUrl = musicUrl.replaceAll("/+$", "");
            if (duration < 10 || duration > 600)
                throw new ApiException(HttpStatus.BAD_REQUEST, "本地生成时长请输入 10 到 600 秒（最多 10 分钟）");
        }
        if (provider.equals("elevenlabs") && !ElevenLabsMusicClient.isOfficial(musicUrl))
            throw new ApiException(HttpStatus.BAD_REQUEST, "ElevenLabs 请使用官方音乐生成接口");
        if (ElevenLabsMusicClient.isOfficial(musicUrl) && !java.util.Set.of("music_v2", "music_v2_5").contains(musicModel))
            throw new ApiException(HttpStatus.BAD_REQUEST, "ElevenLabs 模型请选择 music_v2 或 music_v2_5");
        var next = new Config(model, url, input.clearTextKey() ? "" : keep(input.textApiKey(), config.textApiKey()),
            musicModel, musicUrl, input.clearMusicKey() ? "" : keep(input.musicApiKey(),
                musicUrl.equals(config.musicEndpoint()) && provider.equals(config.provider()) ? config.musicApiKey() : ""), provider, duration);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "settings-", ".tmp");
            try {
                mapper.writeValue(temp.toFile(), next);
                Files.move(temp, file.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
            config = next;
        } catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存 AI 配置失败"); }
        return publicView();
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String keep(String value, String old) { return clean(value).isEmpty() ? old : clean(value); }
    private void validateUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "接口地址必须是有效的 HTTP 或 HTTPS 地址"); }
    }
}

@RestController
@RequestMapping("/api/settings")
class AiSettingsController {
    private final AiSettings settings;
    AiSettingsController(AiSettings settings) { this.settings = settings; }
    @GetMapping public Map<String, Object> get() { return settings.publicView(); }
    @PutMapping public Map<String, Object> put(@RequestBody AiSettings.Update input) { return settings.save(input); }
}
