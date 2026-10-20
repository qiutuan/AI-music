<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  PhArrowRight, PhCheck, PhCloudArrowUp, PhDownloadSimple, PhFloppyDisk, PhHeadphones,
  PhList, PhMagicWand, PhMusicNotes, PhPause, PhPlay, PhPlus,
  PhSlidersHorizontal, PhSparkle, PhTrash, PhWaveform, PhX,
} from '@phosphor-icons/vue'
import { api } from './api'
import DurationControl from './DurationControl.vue'
import type { GenerationStatus, Song, SongInput } from './types'

type View = 'studio' | 'library'
const view = ref<View>('studio')
const songs = ref<Song[]>([])
const currentId = ref<string | null>(null)
const status = ref<GenerationStatus | null>(null)
const durationSeconds = ref(30)
const isLocalMusic = computed(() => status.value?.musicProviderId === 'acestep')
const validDuration = computed(() => Number.isInteger(durationSeconds.value) && durationSeconds.value >= 10 && durationSeconds.value <= 600)
const busy = ref<'lyrics' | 'music' | 'save' | 'upload' | null>(null)
const toast = ref<{ text: string; tone: 'success' | 'error' } | null>(null)
const audio = ref<HTMLAudioElement | null>(null)
const isPlaying = ref(false)
const playbackTime = ref(0)
const playbackDuration = ref(0)
function syncPlayback() {
  const player = audio.value
  playbackTime.value = player?.currentTime || 0
  playbackDuration.value = player && Number.isFinite(player.duration) ? player.duration : 0
  isPlaying.value = !!player && !player.paused && !player.ended
}
function seekPlayback(event: Event) {
  if (!audio.value || playbackDuration.value <= 0) return
  const position = Number((event.target as HTMLInputElement).value)
  audio.value.currentTime = Math.max(0, Math.min(playbackDuration.value, position))
  playbackTime.value = audio.value.currentTime
}
function playbackLabel(seconds: number) {
  const value = Math.max(0, Math.floor(seconds))
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, '0')}`
}
watch(audio, () => { playbackTime.value = 0; playbackDuration.value = 0; isPlaying.value = false })
const downloading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const mobileMenu = ref(false)
const settingsOpen = ref(false)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const settingsError = ref('')
const musicProgress = ref('')
let progressTimer: ReturnType<typeof setInterval> | undefined
onUnmounted(() => { if (progressTimer) clearInterval(progressTimer) })
const aiConfig = reactive({ textModel: '', textBaseUrl: '', textApiKey: '', textKeyConfigured: false,
  musicModel: '', musicEndpoint: '', musicApiKey: '', musicKeyConfigured: false, clearTextKey: false, clearMusicKey: false,
  musicProvider: 'elevenlabs', musicDurationSeconds: 30 })
function changeMusicProvider() {
  aiConfig.musicApiKey = ''
  aiConfig.musicKeyConfigured = false
  aiConfig.clearMusicKey = false
  if (aiConfig.musicProvider === 'acestep') {
    aiConfig.musicEndpoint = 'http://127.0.0.1:8001'
    aiConfig.musicModel = 'acestep-v15-turbo'
    aiConfig.musicDurationSeconds = 30
  } else if (aiConfig.musicProvider === 'elevenlabs') {
    aiConfig.musicEndpoint = 'https://api.elevenlabs.io/v1/music'
    aiConfig.musicModel = 'music_v2_5'
  } else {
    aiConfig.musicEndpoint = ''
    aiConfig.musicModel = ''
  }
}
async function openSettings() {
  settingsOpen.value = true
  settingsLoading.value = true
  settingsError.value = ''
  try {
    const response = await fetch('/api/settings')
    if (!response.ok) throw new Error('无法读取 AI 配置')
    Object.assign(aiConfig, await response.json(), { textApiKey: '', musicApiKey: '', clearTextKey: false, clearMusicKey: false })
  } catch (error) { settingsError.value = (error as Error).message }
  finally { settingsLoading.value = false }
}
async function saveSettings() {
  settingsSaving.value = true
  settingsError.value = ''
  try {
    const response = await fetch('/api/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(aiConfig) })
    const data = await response.json()
    if (!response.ok) throw new Error(data.message || '保存失败')
    Object.assign(aiConfig, data, { textApiKey: '', musicApiKey: '', clearTextKey: false, clearMusicKey: false })
    status.value = await api.status()
    durationSeconds.value = status.value.musicDurationSeconds || 30
    notify('AI 配置已保存，后续生成将使用新配置')
    settingsOpen.value = false
  } catch (error) { settingsError.value = (error as Error).message }
  finally { settingsSaving.value = false }
}

const styles = [
  { name: '流行', en: 'POP', symbol: '✦', color: 'peach' },
  { name: '说唱', en: 'HIP-HOP', symbol: '♬', color: 'lavender' },
  { name: '古典', en: 'CLASSICAL', symbol: '◈', color: 'blue' },
  { name: '民谣', en: 'FOLK', symbol: '✳', color: 'green' },
  { name: '电子', en: 'ELECTRONIC', symbol: '⌁', color: 'pink' },
  { name: '爵士', en: 'JAZZ', symbol: '◌', color: 'yellow' },
]
const moods = ['温暖', '治愈', '快乐', '忧伤', '浪漫', '热烈', '宁静', '神秘']
const inspirations = [
  { label: '写给夏夜的风', hint: '一首关于海边、晚风与重逢的歌', style: '流行', mood: '浪漫' },
  { label: '城市的凌晨三点', hint: '把深夜独行的心事写成节拍', style: '说唱', mood: '忧伤' },
  { label: '森林里的第一束光', hint: '为崭新的一天写一段旋律', style: '民谣', mood: '治愈' },
]

const blank = (): SongInput => ({ title: '', style: '流行', mood: '温暖', theme: '', description: '', lyrics: '', prompt: '' })
const draft = reactive<SongInput>(blank())
const currentSong = computed(() => songs.value.find(song => song.id === currentId.value) || null)
const hasChanges = computed(() => currentSong.value ?
  (Object.keys(blank()) as (keyof SongInput)[]).some(key => draft[key] !== currentSong.value?.[key]) :
  Object.values(draft).some(value => value && value !== '流行' && value !== '温暖'))
const canGenerateLyrics = computed(() => !!draft.theme.trim() && !busy.value)
const canGenerateMusic = computed(() => !!draft.lyrics.trim() && !busy.value && (!isLocalMusic.value || validDuration.value))

function notify(text: string, tone: 'success' | 'error' = 'success') {
  toast.value = { text, tone }
  window.setTimeout(() => { if (toast.value?.text === text) toast.value = null }, 4500)
}
function clearDraft() {
  Object.assign(draft, blank())
  currentId.value = null
  isPlaying.value = false
  view.value = 'studio'
  mobileMenu.value = false
}
function selectSong(song: Song) {
  currentId.value = song.id
  Object.assign(draft, {
    title: song.title, style: song.style, mood: song.mood, theme: song.theme,
    description: song.description, lyrics: song.lyrics, prompt: song.prompt,
  })
  view.value = 'studio'
  mobileMenu.value = false
  isPlaying.value = false
}
function useInspiration(item: typeof inspirations[number]) {
  clearDraft()
  draft.theme = item.label
  draft.description = item.hint
  draft.style = item.style
  draft.mood = item.mood
  document.getElementById('theme-input')?.focus()
}
async function refreshSongs() {
  songs.value = await api.songs()
}
async function saveSong(silent = false): Promise<Song | null> {
  if (!draft.title.trim() && !draft.theme.trim()) {
    notify('先写下歌曲标题或创作主题', 'error')
    return null
  }
  busy.value = 'save'
  try {
    const input = { ...draft, title: draft.title.trim() || draft.theme.trim() }
    const saved = currentId.value ? await api.updateSong(currentId.value, input) : await api.createSong(input)
    currentId.value = saved.id
    Object.assign(draft, input)
    await refreshSongs()
    if (!silent) notify('作品已保存')
    return saved
  } catch (error) {
    notify((error as Error).message, 'error')
    return null
  } finally { busy.value = null }
}
async function generateLyrics() {
  if (!canGenerateLyrics.value) return
  busy.value = 'lyrics'
  try {
    const result = await api.generateLyrics({
      theme: draft.theme, style: draft.style, mood: draft.mood, description: draft.description,
    })
    draft.title = result.title || draft.theme
    draft.lyrics = result.lyrics
    notify('歌词已生成，可以直接修改')
  } catch (error) { notify((error as Error).message, 'error') }
  finally { busy.value = null }
}
async function generateMusic() {
  if (!canGenerateMusic.value) return
  const saved = await saveSong(true)
  if (!saved) return
  busy.value = 'music'
  musicProgress.value = '正在提交生成请求…'
  progressTimer = setInterval(async () => {
    try {
      const progress = await api.musicProgress(saved.id)
      if (busy.value === 'music' && progress.message) musicProgress.value = progress.message
    } catch { /* The generation request reports connection errors. */ }
  }, 2000)
  try {
    const result = await api.generateMusic(saved.id, isLocalMusic.value ? durationSeconds.value : undefined)
    await refreshSongs()
    selectSong(result)
    notify('歌曲已生成，点击播放试听')
  } catch (error) { notify((error as Error).message, 'error') }
  finally {
    if (progressTimer) clearInterval(progressTimer)
    progressTimer = undefined
    musicProgress.value = ''
    busy.value = null
  }
}
async function uploadAudio(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('audio/')) { notify('请选择音频文件', 'error'); return }
  if (file.size > 30 * 1024 * 1024) { notify('音频文件不能超过 30 MB', 'error'); return }
  const saved = await saveSong(true)
  if (!saved) return
  busy.value = 'upload'
  try {
    const updated = await api.uploadAudio(saved.id, file)
    await refreshSongs()
    selectSong(updated)
    notify('音频已替换')
  } catch (error) { notify((error as Error).message, 'error') }
  finally { busy.value = null; if (fileInput.value) fileInput.value.value = '' }
}
async function deleteSong(song: Song) {
  if (!window.confirm(`删除「${song.title}」？此操作无法撤销。`)) return
  try {
    await api.deleteSong(song.id)
    await refreshSongs()
    if (currentId.value === song.id) clearDraft()
    notify('作品已删除')
  } catch (error) { notify((error as Error).message, 'error') }
}
async function togglePlay() {
  if (!audio.value) return
  if (audio.value.paused) {
    try { await audio.value.play(); isPlaying.value = true }
    catch { notify('音频播放失败', 'error') }
  } else { audio.value.pause(); isPlaying.value = false }
}
async function downloadSong() {
  const song = currentSong.value
  if (!song?.audioUrl || downloading.value) return
  downloading.value = true
  try {
    const response = await fetch(song.audioUrl)
    if (!response.ok) throw new Error('下载失败，请稍后重试')
    const blob = await response.blob()
    if (!blob.size) throw new Error('音频文件为空，无法下载')
    const type = blob.type.split(';')[0]
    const extensions: Record<string, string> = {
      'audio/wav': 'wav', 'audio/x-wav': 'wav', 'audio/wave': 'wav',
      'audio/mpeg': 'mp3', 'audio/mp3': 'mp3', 'audio/ogg': 'ogg',
      'audio/mp4': 'm4a', 'audio/x-m4a': 'm4a', 'audio/flac': 'flac',
    }
    const pathExtension = new URL(song.audioUrl, window.location.origin).pathname.match(/\.(mp3|wav|ogg|m4a|flac)$/i)?.[1]
    const extension = extensions[type] || pathExtension
    if (!extension) throw new Error('音频格式无法识别，请检查音频来源')
    const title = song.title.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 100).replace(/[. ]+$/, '') || '我的歌曲'
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${title}.${extension}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 60000)
    notify('已开始下载歌曲')
  } catch (error) {
    notify(error instanceof TypeError ? '无法下载音频，请检查网络或音频来源的下载权限' : (error as Error).message, 'error')
  } finally { downloading.value = false }
}
function showLibrary() { view.value = 'library'; mobileMenu.value = false }
function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value))
}
onMounted(async () => {
  const results = await Promise.allSettled([api.status(), refreshSongs()])
  if (results[0].status === 'fulfilled') {
    status.value = results[0].value
    durationSeconds.value = status.value.musicDurationSeconds || 30
  }
  if (results[1].status === 'rejected') notify('无法连接后端服务，请先启动 Spring Boot', 'error')
})
</script>

<template>
  <div class="app-shell">
    <button class="mobile-menu-button" aria-label="打开菜单" @click="mobileMenu = !mobileMenu"><PhList :size="24" /></button>
    <aside class="sidebar" :class="{ 'sidebar-open': mobileMenu }">
      <div class="brand" @click="clearDraft">
        <span class="brand-mark"><PhWaveform :size="22" weight="bold" /></span>
        <span class="brand-text">谱间<span class="brand-dot">.</span></span>
      </div>
      <p class="sidebar-caption">YOUR CREATIVE SPACE</p>
      <nav class="side-nav" aria-label="主导航">
        <button class="nav-link" @click="openSettings"><PhSlidersHorizontal :size="19" />AI 模型配置</button>
        <button :class="['nav-link', { active: view === 'studio' }]" @click="view = 'studio'; mobileMenu = false"><PhMagicWand :size="19" />创作工作台<span v-if="view === 'studio'" class="nav-indicator"></span></button>
        <button :class="['nav-link', { active: view === 'library' }]" @click="showLibrary"><PhMusicNotes :size="19" />我的作品<span class="nav-count">{{ songs.length }}</span></button>
      </nav>
      <div class="sidebar-section-heading"><span>最近创作</span><button aria-label="新建作品" @click="clearDraft"><PhPlus :size="16" /></button></div>
      <div class="recent-list">
        <button v-for="song in songs.slice(0, 5)" :key="song.id" class="recent-item" @click="selectSong(song)">
          <span class="recent-note"><PhMusicNotes :size="15" /></span><span class="recent-name">{{ song.title }}</span>
        </button>
        <p v-if="!songs.length" class="recent-empty">灵感，从这里开始。</p>
      </div>
      <div class="sidebar-bottom">
        <div class="sidebar-illustration"><span class="orbit orbit-one"></span><span class="orbit orbit-two"></span><PhWaveform :size="35" weight="thin" /></div>
        <div class="sidebar-tip-title">让灵感，自由生长。</div>
        <div class="sidebar-tip-copy">每一段旋律，都始于一个小小的念头。</div>
        <div class="sidebar-footer"><span class="status-light"></span> {{ status?.lyricsReady && status?.musicReady ? 'AI 服务已配置' : '创作空间已就绪' }}</div>
      </div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div class="breadcrumb">创作空间 <span>/</span> <strong>{{ view === 'studio' ? '创作工作台' : '我的作品' }}</strong></div>
        <div class="top-actions"><span class="version-pill"><span></span> AI MUSIC STUDIO</span><button class="top-new" @click="clearDraft"><PhPlus :size="17" weight="bold" /> 新建作品</button></div>
      </header>

      <template v-if="view === 'studio'">
        <section class="hero">
          <div class="hero-copy"><div class="eyebrow"><PhSparkle :size="15" weight="fill" /> 让灵感有声可循</div><h1>把脑海里的旋律，<br /><em>变成一首歌。</em></h1><p>从一句想法开始，写下歌词、选择曲风，再让 AI 为它谱曲。<br class="desktop-break" />每一个细节，都由你决定。</p><div class="hero-scroll"><span class="hero-scroll-line"></span> 开始创作</div></div>
          <div class="hero-art" aria-hidden="true"><div class="art-disc art-disc-back"></div><div class="art-disc art-disc-front"><span class="disc-center"></span></div><div class="art-spark art-spark-one">✦</div><div class="art-spark art-spark-two">✳</div><div class="art-note">♫</div><div class="art-caption">MAKE SOMETHING<br />BEAUTIFUL TODAY</div></div>
        </section>

        <div class="workspace-grid">
          <section class="creator-column">
            <div class="section-title-row"><div><span class="section-index">01 / THE IDEA</span><h2>从一个灵感开始</h2><p>告诉我们你想写一首怎样的歌</p></div><span class="step-decoration">✳</span></div>
            <div class="card idea-card"><label class="field-label" for="theme-input">歌曲主题 <span>必填</span></label><input id="theme-input" v-model="draft.theme" maxlength="120" placeholder="例如：写给那个在雨天陪我等车的人" /><label class="field-label description-label" for="description-input">更多想法 <span>可选</span></label><textarea id="description-input" v-model="draft.description" rows="3" maxlength="1000" placeholder="描述故事、画面、想表达的情绪，让创作更贴近你的想法..." /><div class="idea-footer"><span><PhSparkle :size="15" /> 一个细节，也能点亮一首歌</span><span>{{ draft.description.length }}/1000</span></div></div>

            <div class="section-title-row style-heading"><div><span class="section-index">02 / THE SOUND</span><h2>挑选你的声音</h2><p>为这首歌找到恰好的氛围</p></div></div>
            <div class="style-grid"><button v-for="item in styles" :key="item.name" :class="['style-card', item.color, { selected: draft.style === item.name }]" @click="draft.style = item.name"><span class="style-symbol">{{ item.symbol }}</span><span class="style-info"><strong>{{ item.name }}</strong><small>{{ item.en }}</small></span><span v-if="draft.style === item.name" class="style-check"><PhCheck :size="13" weight="bold" /></span></button></div>
            <div class="mood-section"><div class="field-label">此刻的心情</div><div class="mood-options"><button v-for="mood in moods" :key="mood" :class="['mood-chip', { selected: draft.mood === mood }]" @click="draft.mood = mood">{{ mood }}</button></div></div>

            <div class="action-card"><div class="action-icon"><PhMagicWand :size="21" /></div><div><strong>准备好写下第一句了吗？</strong><p>AI 会根据你的想法和风格，创作一份可编辑的歌词。</p></div><button class="primary-button" :disabled="!canGenerateLyrics" @click="generateLyrics">{{ busy === 'lyrics' ? '正在写词...' : draft.lyrics ? '重新生成歌词' : '生成歌词' }} <PhArrowRight :size="17" /></button></div>
          </section>

          <section class="editor-column">
            <div class="editor-header"><div><span class="section-index">03 / YOUR SONG</span><h2>创作手稿</h2></div><span class="editable-badge"><span></span> 随时可编辑</span></div>
            <div class="card editor-card"><div class="editor-top"><span class="editor-top-label"><PhMusicNotes :size="17" /> SONG DRAFT</span><span class="editor-top-right">{{ currentId ? '已保存作品' : '未保存草稿' }}</span></div><input v-model="draft.title" class="song-title-input" maxlength="120" placeholder="给你的歌起个名字" aria-label="歌曲名称" /><div class="song-meta"><span>{{ draft.style }}</span><span class="meta-dot">·</span><span>{{ draft.mood }}</span><span class="meta-dot">·</span><span>原创作品</span></div><div class="editor-divider"></div><div class="lyrics-heading"><span>歌词</span><span><PhSlidersHorizontal :size="15" /> 自由修改</span></div><textarea v-model="draft.lyrics" class="lyrics-editor" maxlength="10000" placeholder="[主歌]&#10;在这里写下你的歌词...&#10;&#10;或者，点击左侧「生成歌词」开启灵感。&#10;&#10;[副歌]" aria-label="歌词编辑器"></textarea><div class="editor-divider lower"></div><label class="field-label prompt-label" for="prompt-input">编曲补充 <span>可选</span></label><input id="prompt-input" v-model="draft.prompt" maxlength="500" placeholder="例如：女声、温柔钢琴、慢节奏..." /><DurationControl v-if="isLocalMusic" v-model="durationSeconds" :disabled="!!busy" /><p class="generation-info" aria-live="polite">{{ busy === 'music' ? musicProgress : `音乐服务：${status?.musicProvider || '尚未配置'}` }}</p><div class="editor-actions"><button class="text-button" :disabled="!!busy || !hasChanges" @click="saveSong()"><PhFloppyDisk :size="18" /> 保存作品</button><span class="editor-actions-spacer"></span><button class="music-button" :disabled="!canGenerateMusic" @click="generateMusic"><PhWaveform :size="18" /> {{ busy === 'music' ? '正在生成歌曲...' : currentSong?.audioUrl ? '重新生成歌曲' : '生成歌曲' }}</button></div></div>
            <div class="audio-card"><div class="audio-card-icon"><PhHeadphones :size="20" /></div><div class="audio-card-text"><strong>{{ currentSong?.audioUrl ? '试听你的作品' : '你的音乐，正在等待诞生' }}</strong><span>{{ currentSong?.audioUrl ? '聆听、替换音频，或继续调整歌词后重新生成' : '生成歌曲后，音频会出现在这里' }}</span></div><button v-if="currentSong?.audioUrl" class="download-song-button" :disabled="downloading || busy === 'music' || busy === 'upload'" @click="downloadSong"><PhDownloadSimple :size="18" />{{ downloading ? '下载中…' : '下载歌曲' }}</button><button v-if="currentSong?.audioUrl" class="play-button" aria-label="播放或暂停" @click="togglePlay"><PhPause v-if="isPlaying" :size="19" weight="fill" /><PhPlay v-else :size="19" weight="fill" /></button><span v-else class="audio-waves"><i></i><i></i><i></i><i></i><i></i></span><div v-if="currentSong?.audioUrl" class="playback-progress"><div class="playback-times"><span>播放进度</span><span>{{ playbackLabel(playbackTime) }} / {{ playbackLabel(playbackDuration) }}</span></div><input type="range" aria-label="播放进度" :aria-valuetext="`${playbackLabel(playbackTime)} / ${playbackLabel(playbackDuration)}`" min="0" :max="playbackDuration || 1" step="0.1" :value="playbackTime" :disabled="playbackDuration <= 0" @input="seekPlayback" /><span class="playback-hint">拖动或点击进度条，跳转到想听的位置</span></div><audio v-if="currentSong?.audioUrl" :key="currentSong.audioUrl" ref="audio" :src="currentSong.audioUrl" preload="metadata" @loadedmetadata="syncPlayback" @durationchange="syncPlayback" @timeupdate="syncPlayback" @seeked="syncPlayback" @play="syncPlayback" @pause="syncPlayback" @ended="syncPlayback" @emptied="syncPlayback" @error="syncPlayback" /></div>
            <div class="editor-bottom-actions"><input ref="fileInput" type="file" accept="audio/*" hidden @change="uploadAudio" /><button :disabled="!!busy" @click="fileInput?.click()"><PhCloudArrowUp :size="17" /> {{ busy === 'upload' ? '正在上传...' : '上传 / 替换音频' }}</button><span>支持 MP3、WAV 等常见格式 · 最大 30 MB</span></div>
          </section>
        </div>

        <section class="inspiration-section"><div class="inspiration-heading"><div><span class="section-index">A LITTLE SPARK</span><h2>找不到灵感？从这里开始。</h2></div><span>点击一个灵感，开始创作 <PhArrowRight :size="16" /></span></div><div class="inspiration-grid"><button v-for="(item, index) in inspirations" :key="item.label" class="inspiration-card" @click="useInspiration(item)"><span class="inspiration-number">0{{ index + 1 }}</span><span class="inspiration-flower">{{ ['✳', '✦', '✺'][index] }}</span><strong>{{ item.label }}</strong><small>{{ item.hint }}</small><span class="inspiration-arrow"><PhArrowRight :size="17" /></span></button></div></section>
      </template>

      <section v-else class="library-view"><div class="library-hero"><div><span class="section-index">YOUR COLLECTION</span><h1>我的作品<span class="library-period">.</span></h1><p>每一首歌，都是某一刻的你。继续打磨它们吧。</p></div><button class="primary-button" @click="clearDraft"><PhPlus :size="18" /> 创作新歌</button></div><div class="library-toolbar"><span>全部作品 <strong>{{ songs.length }}</strong></span><span>按最近编辑排序</span></div><div v-if="songs.length" class="library-grid"><article v-for="(song, index) in songs" :key="song.id" class="library-card"><div :class="['library-cover', ['cover-peach', 'cover-lavender', 'cover-blue', 'cover-green'][index % 4]]"><span class="cover-glyph">{{ ['✳', '♫', '✦', '◈'][index % 4] }}</span><span class="cover-index">NO. {{ String(songs.length - index).padStart(2, '0') }}</span></div><div class="library-card-body"><div class="library-card-kicker">{{ song.style }} <span>·</span> {{ song.mood }} <span>·</span> {{ formatDate(song.updatedAt) }}</div><h3>{{ song.title }}</h3><p>{{ song.theme || song.description || '属于你的原创作品' }}</p><div class="library-card-bottom"><span :class="['audio-state', { ready: song.audioUrl }]">{{ song.audioUrl ? '● 已生成音频' : '○ 歌词草稿' }}</span><div><button :aria-label="`编辑 ${song.title}`" @click="selectSong(song)"><PhArrowRight :size="18" /></button><button :aria-label="`删除 ${song.title}`" @click="deleteSong(song)"><PhTrash :size="17" /></button></div></div></div></article></div><div v-else class="library-empty"><span>♫</span><h2>这里还空着，等你的第一首歌。</h2><p>哪怕只有一句想法，也可以成为创作的起点。</p><button class="primary-button" @click="clearDraft">开始创作 <PhArrowRight :size="17" /></button></div></section>
      <footer class="page-footer"><span>谱间 · 让每个灵感都有回声</span><span>MADE FOR THE MUSIC IN YOU ✳</span></footer>
    </main>
    <div v-if="mobileMenu" class="mobile-overlay" @click="mobileMenu = false"></div>
    <div v-if="settingsOpen" class="settings-overlay" @keydown.esc="settingsOpen = false" @click.self="settingsOpen = false">
      <section class="settings-dialog" role="dialog" aria-modal="true" aria-labelledby="settings-title">
        <header><div><h2 id="settings-title">绑定你的 AI</h2><p>填写模型和服务地址，保存后立即用于创作。</p></div><button class="text-button" aria-label="关闭配置" @click="settingsOpen = false"><PhX :size="22" /></button></header>
        <p v-if="settingsLoading">正在读取配置…</p>
        <form v-else @submit.prevent="saveSettings">
          <fieldset :disabled="settingsSaving"><legend>歌词模型</legend>
            <p class="settings-hint">支持 OpenAI 兼容接口，包括百炼、DeepSeek 和提供兼容接口的本地模型。</p>
            <label>模型名称<input v-model="aiConfig.textModel" required placeholder="例如 qwen-plus、deepseek-chat" /></label>
            <label>接口地址（Base URL）<input v-model="aiConfig.textBaseUrl" type="url" required placeholder="https://服务地址/v1" /></label>
            <label>API Key <span>{{ aiConfig.textKeyConfigured ? '已配置，留空保留原密钥' : '本地无鉴权服务可留空' }}</span><input v-model="aiConfig.textApiKey" type="password" autocomplete="new-password" placeholder="填写新密钥" /></label>
            <label v-if="aiConfig.textKeyConfigured" class="settings-check"><input v-model="aiConfig.clearTextKey" type="checkbox" />清除已保存的歌词密钥</label>
          </fieldset>
          <fieldset :disabled="settingsSaving"><legend>歌曲生成</legend>
            <label>音乐服务<select v-model="aiConfig.musicProvider" @change="changeMusicProvider"><option value="acestep">ACE-Step 本地服务（本机生成）</option><option value="elevenlabs">ElevenLabs Music（云端付费）</option><option value="generic">自定义音乐接口</option></select></label>
            <template v-if="aiConfig.musicProvider === 'acestep'">
              <p class="settings-hint">先运行 D:\ACE-Step-1.5\start-local-api.bat。使用编辑后的歌词在本机生成，完成后自动保存 WAV。创作页可随时调整本次时长。</p>
              <label>音乐模型<input v-model="aiConfig.musicModel" required placeholder="acestep-v15-turbo" /></label>
              <label>服务地址<input v-model="aiConfig.musicEndpoint" type="url" required placeholder="http://127.0.0.1:8001" /></label>
              <label>默认时长（秒）<input v-model.number="aiConfig.musicDurationSeconds" type="number" min="10" max="600" step="1" required /><span>10～600 秒；每次生成可在创作页单独调整。</span></label>
              <label>API Key <span>{{ aiConfig.musicKeyConfigured ? '已配置，留空保留' : '当前本地部署无需填写' }}</span><input v-model="aiConfig.musicApiKey" type="password" autocomplete="new-password" placeholder="可留空" /></label>
            </template>
            <template v-else-if="aiConfig.musicProvider === 'elevenlabs'">
              <p class="settings-hint">使用编辑后的歌词生成歌曲并保存 MP3。需要账户支持 Music API，生成会消耗服务商额度。</p>
              <label>音乐模型<select v-model="aiConfig.musicModel"><option value="music_v2_5">Eleven Music v2.5（music_v2_5）</option><option value="music_v2">Eleven Music v2（music_v2）</option></select></label>
              <label>官方生成接口<input v-model="aiConfig.musicEndpoint" type="url" readonly /></label>
              <label>ElevenLabs API Key <span>{{ aiConfig.musicKeyConfigured ? '已配置，留空保留原密钥' : '启用歌曲生成时必填' }}</span><input v-model="aiConfig.musicApiKey" type="password" autocomplete="new-password" placeholder="填写 ElevenLabs 密钥" /></label>
              <p class="settings-hint"><a href="https://elevenlabs.io/app/settings/api-keys" target="_blank" rel="noopener noreferrer">前往 ElevenLabs 创建 API Key ↗</a></p>
            </template>
            <template v-else>
              <p class="settings-hint">填写返回 audioUrl 或 audioBase64 的完整生成接口地址。</p>
              <label>音乐模型<input v-model="aiConfig.musicModel" /></label>
              <label>生成接口<input v-model="aiConfig.musicEndpoint" type="url" /></label>
              <label>API Key <span>可选</span><input v-model="aiConfig.musicApiKey" type="password" autocomplete="new-password" /></label>
            </template>
            <p class="settings-hint">切换服务时请使用对应服务的密钥；原音乐密钥不会带入新服务。</p>
            <label v-if="aiConfig.musicKeyConfigured" class="settings-check"><input v-model="aiConfig.clearMusicKey" type="checkbox" />清除已保存的歌曲密钥</label>
          </fieldset>
          <p v-if="settingsError" class="settings-error" role="alert">{{ settingsError }}</p>
          <footer><span>配置保存在本机，密钥不会回显。保存不代表连接验证。</span><button class="primary-button" :disabled="settingsSaving">{{ settingsSaving ? '保存中…' : '保存并绑定' }}</button></footer>
        </form>
      </section>
    </div>
    <Transition name="toast"><div v-if="toast" :class="['toast', toast.tone]"><PhCheck v-if="toast.tone === 'success'" :size="17" /><PhX v-else :size="17" />{{ toast.text }}<button aria-label="关闭提示" @click="toast = null"><PhX :size="14" /></button></div></Transition>
  </div>
</template>
