package com.pujian.music;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MusicDurationRequestTest {
    @Test void passesPerRequestDurationAndRejectsOutOfRangeValues() throws Exception {
        var audio = mock(AudioService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new StudioController(mock(SongStore.class), mock(AgentStudioService.class), audio)).build();
        mvc.perform(post("/api/songs/example/generate-music").contentType("application/json")
            .content("{\"durationSeconds\":180}")).andExpect(status().isOk());
        verify(audio).generate("example", 180);
        mvc.perform(post("/api/songs/example/generate-music").contentType("application/json")
            .content("{\"durationSeconds\":601}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/songs/example/generate-music").contentType("application/json")
            .content("{\"durationSeconds\":9}")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(audio);
    }
}
