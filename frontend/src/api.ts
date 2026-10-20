import type { GenerationStatus, Song, SongInput } from './types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: { ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...init?.headers },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `请求失败 (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  status: () => request<GenerationStatus>('/status'),
  songs: () => request<Song[]>('/songs'),
  createSong: (input: SongInput) => request<Song>('/songs', { method: 'POST', body: JSON.stringify(input) }),
  updateSong: (id: string, input: SongInput) => request<Song>(`/songs/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  deleteSong: (id: string) => request<void>(`/songs/${id}`, { method: 'DELETE' }),
  generateLyrics: (input: { theme: string; style: string; mood: string; description: string }) =>
    request<{ title: string; lyrics: string }>('/lyrics/generate', { method: 'POST', body: JSON.stringify(input) }),
  generateMusic: (id: string, durationSeconds?: number) => request<Song>(`/songs/${id}/generate-music`, {
    method: 'POST', body: durationSeconds === undefined ? undefined : JSON.stringify({ durationSeconds }),
  }),
  musicProgress: (id: string) => request<{ running: boolean; message: string }>(`/songs/${id}/generation-status`),
  uploadAudio: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<Song>(`/songs/${id}/audio`, { method: 'POST', body: form })
  },
}
