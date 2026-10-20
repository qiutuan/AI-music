# 谱间 · AI 音乐创作空间

本地运行的 AI 音乐创作网站。前端采用 Vue 3 + Vite，后端采用 Spring Boot，歌词生成使用 AgentScope Java 2.0.4。无需登录。

## 功能

- 输入主题、补充想法，选择流行、说唱、古典、民谣、电子或爵士及情绪。
- 使用 AgentScope 作词智能体生成中文歌词，随后可直接修改标题和歌词。
- 保存作品、继续编辑、删除作品；数据保存在项目的 `data/songs.json`。
- 使用可配置的歌曲生成接口将当前歌词与编曲要求转成音频。AgentScope 制作人智能体会整理编曲提示词。
- 试听生成音频，或上传 MP3、WAV、OGG、M4A 文件替换音频；可修改参数后重新生成歌曲。

## 快速启动

需要 Java 17+、Maven 3.9+、Node.js 20+。项目在当前机器上也能使用 `build.ps1` 自动寻找现有工具链。

```powershell
cd E:\WORK\ai-music-studio
.\build.ps1
.\start.ps1
```

然后打开 <http://127.0.0.1:8080>。首次构建需要联网下载 Maven 和 npm 依赖。网站仅监听本机地址。

日常前端开发可以分别运行：

```powershell
cd E:\WORK\ai-music-studio\backend
mvn spring-boot:run

# 另开一个终端
cd E:\WORK\ai-music-studio\frontend
npm run dev
```

开发地址是 <http://127.0.0.1:5173>，Vite 会把 `/api` 代理到 Spring Boot。

## 配置 AI 服务

未提供密钥时，网站仍可手动写词、编辑作品、上传音频。页面不会生成虚假的歌词或歌曲。

### 歌词模型

默认使用 AgentScope 的 `dashscope:qwen-plus`。运行前设置环境变量：

```powershell
$env:DASHSCOPE_API_KEY = '你的密钥'
.\start.ps1
```

也可以切换为 `openai:<模型名>`，并设置 `OPENAI_API_KEY`；或切换为本地 `ollama:<模型名>`：

```powershell
$env:STUDIO_TEXT_MODEL = 'ollama:qwen3'
.\start.ps1
```

### 歌曲模型

网站现在已接入 ElevenLabs Music，推荐通过前端配置 `music_v2_5` 和 ElevenLabs API Key。具体操作见 [ELEVENLABS.md](ELEVENLABS.md)。以下通用接口说明仅供使用自建兼容服务的开发者参考。

设置 `STUDIO_MUSIC_ENDPOINT`，可选设置 `STUDIO_MUSIC_API_KEY`。后端向该地址发送 `POST application/json`；设置密钥时使用 `Authorization: Bearer <key>`。请求结构：

```json
{
  "title": "回声",
  "style": "流行",
  "mood": "温暖",
  "theme": "重逢",
  "description": "雨后的车站",
  "lyrics": "[主歌1]...",
  "prompt": "温柔女声、钢琴和轻鼓，慢速流行"
}
```

服务返回以下任一结构：

```json
{ "audioBase64": "<MP3 的 Base64 字符串>" }
```

```json
{ "audioUrl": "https://example.com/generated-song.mp3" }
```

`audioBase64` 会保存为本地 MP3；`audioUrl` 直接供浏览器播放。请把实际歌曲服务商的 API 包装成这个接口。不同服务商的请求和异步任务格式不一样，因此这里没有猜测或硬编码某家的私有协议。音频上限为 30 MB。

## 项目结构

```text
frontend/   Vue 3 工作台、作品库与音频播放器
backend/    Spring Boot API、AgentScope 作词/制作人、文件存储
data/       作品 JSON 和生成/上传的音频（运行后自动创建）
build.ps1   构建前端并打包为一个 Spring Boot JAR
start.ps1   启动本地网站
```

## API

`GET /api/status`、`GET /api/songs`、`POST /api/songs`、`PUT /api/songs/{id}`、`DELETE /api/songs/{id}`、`POST /api/lyrics/generate`、`POST /api/songs/{id}/generate-music`、`POST /api/songs/{id}/audio`。
