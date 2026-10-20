package com.pujian.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AiSettingsTest {
    @TempDir Path dir;
    @Test void savesLocalProviderWithoutCarryingCloudKeyAndPreservesLyricsKey() {
        var mapper = new ObjectMapper();
        var settings = new AiSettings(dir.toString(), mapper, "", "");
        settings.save(new AiSettings.Update("deepseek", "https://api.deepseek.com", "lyrics-secret", "music_v2_5",
            "https://api.elevenlabs.io/v1/music", "cloud-secret", false, false));
        settings.save(new AiSettings.Update("deepseek", "https://api.deepseek.com", "", "acestep-v15-turbo",
            "http://127.0.0.1:8001/", "", false, false, "acestep", 30));
        var reopened = new AiSettings(dir.toString(), mapper, "", "");
        assertEquals("acestep", reopened.current().provider());
        assertEquals("http://127.0.0.1:8001", reopened.current().musicEndpoint());
        assertEquals("", reopened.current().musicApiKey());
        assertEquals("lyrics-secret", reopened.current().textApiKey());
        assertEquals(30, reopened.current().durationSeconds());
        assertFalse(reopened.publicView().toString().contains("lyrics-secret"));
        assertThrows(ApiException.class, () -> settings.save(new AiSettings.Update("deepseek", "https://api.deepseek.com", "",
            "acestep-v15-turbo", "http://127.0.0.1:8001/release_task", "", false, false, "acestep", 30)));
        assertThrows(ApiException.class, () -> settings.save(new AiSettings.Update("deepseek", "https://api.deepseek.com", "",
            "acestep-v15-turbo", "http://127.0.0.1:8001", "", false, false, "acestep", 601)));
    }
    @Test void acceptsFullLengthLocalDefault() {
        var settings = new AiSettings(dir.toString(), new ObjectMapper(), "", "");
        settings.save(new AiSettings.Update("deepseek", "https://api.deepseek.com", "", "acestep-v15-turbo",
            "http://127.0.0.1:8001", "", false, false, "acestep", 600));
        assertEquals(600, settings.current().durationSeconds());
    }
    @Test void persistsConfigurationWithoutReturningSecretsAndSupportsClearing() {
        var mapper = new ObjectMapper();
        var settings = new AiSettings(dir.toString(), mapper, "", "");
        var response = settings.save(new AiSettings.Update("custom-model", "https://example.com/v1", "test-secret", "music-x", "https://example.com/music", "music-secret", false, false));
        assertFalse(response.toString().contains("test-secret"));
        assertFalse(response.toString().contains("music-secret"));
        var reopened = new AiSettings(dir.toString(), mapper, "", "");
        assertEquals("custom-model", reopened.current().textModel());
        reopened.save(new AiSettings.Update("custom-model", "https://example.com/v1", "", "music-x", "", "", false, true));
        assertEquals("test-secret", reopened.current().textApiKey());
        assertEquals("", reopened.current().musicApiKey());
        assertThrows(ApiException.class, () -> reopened.save(new AiSettings.Update("x", "file:///tmp/key", "", "", "", "", false, false)));
        assertThrows(ApiException.class, () -> reopened.save(new AiSettings.Update("deepseek-v4-pro", "https://api.deepseek.com", "", "deepseek-v4-pro", "https://api.deepseek.com", "", false, false)));
    }
}
