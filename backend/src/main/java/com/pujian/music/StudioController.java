package com.pujian.music;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class StudioController {
    private final SongStore songs;
    private final AgentStudioService agents;
    private final AudioService audio;

    public StudioController(SongStore songs, AgentStudioService agents, AudioService audio) {
        this.songs = songs;
        this.agents = agents;
        this.audio = audio;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("lyricsReady", agents.isReady(), "musicReady", audio.isReady(),
            "lyricsProvider", "AgentScope", "musicProvider", audio.providerName(),
            "musicProviderId", audio.providerId(), "musicDurationSeconds", audio.defaultDurationSeconds());
    }

    @GetMapping("/songs")
    public List<Song> list() { return songs.all(); }

    @GetMapping("/songs/{id}")
    public Song get(@PathVariable String id) { return songs.find(id); }

    @PostMapping("/songs")
    public Song create(@Valid @RequestBody SongInput input) { return songs.create(input); }

    @PutMapping("/songs/{id}")
    public Song update(@PathVariable String id, @Valid @RequestBody SongInput input) { return songs.update(id, input); }

    @DeleteMapping("/songs/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        songs.delete(id);
        audio.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lyrics/generate")
    public LyricsResult generateLyrics(@Valid @RequestBody LyricsRequest request) { return agents.generateLyrics(request); }

    @PostMapping("/songs/{id}/generate-music")
    public Song generateMusic(@PathVariable String id, @Valid @RequestBody(required = false) MusicGenerationRequest request) {
        return audio.generate(id, request == null ? null : request.durationSeconds());
    }

    @GetMapping("/songs/{id}/generation-status")
    public Map<String, Object> generationStatus(@PathVariable String id) { return audio.generationStatus(id); }

    @PostMapping(value = "/songs/{id}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Song uploadAudio(@PathVariable String id, @RequestParam("file") MultipartFile file) { return audio.upload(id, file); }

    @GetMapping("/songs/{id}/audio")
    public ResponseEntity<org.springframework.core.io.Resource> readAudio(@PathVariable String id) {
        var file = audio.resource(id);
        var type = switch (file.extension()) {
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            default -> "audio/mpeg";
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.noCache()).body(new org.springframework.core.io.FileSystemResource(file.path()));
    }
}
