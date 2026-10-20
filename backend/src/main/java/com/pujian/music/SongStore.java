package com.pujian.music;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class SongStore {
    private final Path file;
    private final ObjectMapper mapper;

    public SongStore(@Value("${studio.data-dir}") String dataDir, ObjectMapper mapper) {
        this.file = Path.of(dataDir).toAbsolutePath().normalize().resolve("songs.json");
        this.mapper = mapper;
    }

    public synchronized List<Song> all() {
        return read().stream().sorted(Comparator.comparing(Song::updatedAt).reversed()).toList();
    }

    public synchronized Song find(String id) {
        return read().stream().filter(song -> song.id().equals(id)).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "作品不存在"));
    }

    public synchronized Song create(SongInput input) {
        var now = Instant.now();
        var song = toSong(UUID.randomUUID().toString(), input, null, now, now);
        var songs = read();
        songs.add(song);
        write(songs);
        return song;
    }

    public synchronized Song update(String id, SongInput input) {
        var songs = read();
        for (int i = 0; i < songs.size(); i++) {
            var old = songs.get(i);
            if (old.id().equals(id)) {
                var updated = toSong(id, input, old.audioUrl(), old.createdAt(), Instant.now());
                songs.set(i, updated);
                write(songs);
                return updated;
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "作品不存在");
    }

    public synchronized Song setAudio(String id, String audioUrl) {
        var songs = read();
        for (int i = 0; i < songs.size(); i++) {
            var old = songs.get(i);
            if (old.id().equals(id)) {
                var updated = new Song(id, old.title(), old.style(), old.mood(), old.theme(),
                    old.description(), old.lyrics(), old.prompt(), audioUrl, old.createdAt(), Instant.now());
                songs.set(i, updated);
                write(songs);
                return updated;
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "作品不存在");
    }

    public synchronized void delete(String id) {
        var songs = read();
        if (!songs.removeIf(song -> song.id().equals(id))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "作品不存在");
        }
        write(songs);
    }

    private Song toSong(String id, SongInput input, String audioUrl, Instant createdAt, Instant updatedAt) {
        return new Song(id, clean(input.title()), clean(input.style()), clean(input.mood()),
            clean(input.theme()), clean(input.description()), clean(input.lyrics()),
            clean(input.prompt()), audioUrl, createdAt, updatedAt);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

    private List<Song> read() {
        if (!Files.exists(file)) return new ArrayList<>();
        try { return new ArrayList<>(mapper.readValue(file.toFile(), new TypeReference<List<Song>>() {})); }
        catch (IOException error) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取作品失败：" + error.getMessage()); }
    }

    private void write(List<Song> songs) {
        try {
            Files.createDirectories(file.getParent());
            var temp = Files.createTempFile(file.getParent(), "songs-", ".tmp");
            try {
                mapper.writeValue(temp.toFile(), songs);
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException error) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存作品失败：" + error.getMessage()); }
    }
}
