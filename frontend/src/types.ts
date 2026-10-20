export interface Song {
  id: string
  title: string
  style: string
  mood: string
  theme: string
  description: string
  lyrics: string
  prompt: string
  audioUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface SongInput {
  title: string
  style: string
  mood: string
  theme: string
  description: string
  lyrics: string
  prompt: string
}

export interface GenerationStatus {
  lyricsReady: boolean
  musicReady: boolean
  lyricsProvider: string
  musicProvider: string
  musicProviderId: string
  musicDurationSeconds: number
}
