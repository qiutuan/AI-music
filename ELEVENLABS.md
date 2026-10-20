# ElevenLabs Music 接入

打开网站左侧「AI 模型配置」，在「歌曲生成 · ElevenLabs Music」中选择 `music_v2_5`（默认）或 `music_v2`，填写自己的 ElevenLabs API Key，点击「保存并绑定」。官方接口已固定为 `https://api.elevenlabs.io/v1/music`，无需手动拼接地址。

歌词模型可以继续使用 DeepSeek。ElevenLabs 密钥与 DeepSeek 密钥不同，请从 ElevenLabs 账户创建，并确保账户套餐、额度和密钥权限允许调用 Music API。

生成流程：先生成或手动编辑歌词 → 保存或点击生成歌曲 → 等待音频返回 → 播放试听。歌词按段落传入官方 `composition_plan.chunks`；每行最多 200 字，较长的段落会分组，系统根据行数估算段落时长，超过 10 分钟的作品需拆分。不会自动改写你编辑的歌词。

后端使用 `xi-api-key` 请求头认证，返回的 MP3 保存到项目 `data/audio` 目录。模型生成音频仍可能与预期唱法或歌词存在差异，请试听确认。密钥不返回浏览器，但在本机 `data/ai-settings.json` 中以明文保存，请勿分享该文件。

配置保存不等于连接或额度验证。代码使用本地模拟接口验证协议、歌词传递和音频响应；没有有效 ElevenLabs 密钥时不执行真实付费生成。

官方接口文档：https://elevenlabs.io/docs/api-reference/music/compose
