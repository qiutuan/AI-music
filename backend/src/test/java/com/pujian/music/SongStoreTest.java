package com.pujian.music;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongStoreTest {
    @TempDir Path temp;

    @Test
    void savesEditsAndReloadsSongs() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var store = new SongStore(temp.toString(), mapper);
        var original = store.create(new SongInput("夏夜", "流行", "温暖", "海边", "晚风", "第一句", "钢琴"));
        var edited = store.update(original.id(), new SongInput("夏夜重写", "民谣", "治愈", "海边", "晚风", "修改后的歌词", "木吉他"));
        var reloaded = new SongStore(temp.toString(), mapper).find(original.id());

        assertEquals("夏夜重写", reloaded.title());
        assertEquals("修改后的歌词", reloaded.lyrics());
        assertEquals(original.createdAt(), reloaded.createdAt());
        assertEquals(edited.updatedAt(), reloaded.updatedAt());
        assertEquals(1, store.all().size());
    }

    @Test
    void separatesSongsAndDeletesOnlySelectedOne() {
        var store = new SongStore(temp.toString(), new ObjectMapper().registerModule(new JavaTimeModule()));
        var first = store.create(new SongInput("第一首", "说唱", "快乐", "", "", "", ""));
        var second = store.create(new SongInput("第二首", "古典", "宁静", "", "", "", ""));
        store.delete(first.id());
        assertEquals(second.id(), store.all().get(0).id());
        assertThrows(ApiException.class, () -> store.find(first.id()));
    }
}
