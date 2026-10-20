<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ modelValue: number; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()
const minutes = computed(() => Math.floor((Number(props.modelValue) || 0) / 60))
const seconds = computed(() => (Number(props.modelValue) || 0) % 60)
const valid = computed(() => Number.isInteger(props.modelValue) && props.modelValue >= 10 && props.modelValue <= 600)
const label = (value: number) => value < 60 ? `${value} 秒` : `${Math.floor(value / 60)} 分${value % 60 ? ` ${value % 60} 秒` : '钟'}`
const inputNumber = (event: Event) => Number((event.target as HTMLInputElement).value)
</script>

<template>
  <fieldset class="duration-panel" :disabled="disabled">
    <legend>歌曲时长</legend>
    <div class="duration-summary"><span>本次生成</span><strong>{{ valid ? label(modelValue) : '请输入有效时长' }}</strong></div>
    <div class="duration-fields">
      <label><input aria-label="生成分钟" type="number" min="0" max="10" step="1" :value="minutes" @input="emit('update:modelValue', inputNumber($event) * 60 + seconds)" /><span>分钟</span></label>
      <label><input aria-label="生成秒数" type="number" min="0" max="59" step="1" :value="seconds" @input="emit('update:modelValue', minutes * 60 + inputNumber($event))" /><span>秒</span></label>
    </div>
    <input class="duration-slider" aria-label="歌曲时长滑块" type="range" min="10" max="600" step="1" :value="modelValue" @input="emit('update:modelValue', inputNumber($event))" />
    <div class="duration-scale"><span>10 秒</span><span>10 分钟</span></div>
    <div class="duration-presets"><button v-for="value in [30, 60, 120, 180, 300, 600]" :key="value" type="button" :aria-pressed="modelValue === value" :class="{ selected: modelValue === value }" @click="emit('update:modelValue', value)">{{ label(value) }}</button></div>
    <p v-if="!valid" class="duration-error" role="alert">请输入 10 秒到 10 分钟之间的整数时长。</p>
    <p v-else class="duration-note">按此时长生成一次，歌词请与时长匹配。单次上限 10 分钟，超过上限需要分段制作。</p>
    <p v-if="valid && modelValue > 60" class="duration-note">长歌曲会增加生成时间和内存占用；你的 6GB 显存电脑不保证每个时长都能成功。</p>
  </fieldset>
</template>

<style scoped>
.duration-panel{margin:20px 24px 8px;padding:16px;border:1px solid #eadfd4;border-radius:12px;background:#fcf8f3;min-width:0}.duration-panel legend{padding:0 7px;font-size:13px;font-weight:700;color:#655345}.duration-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px;font-size:12px;color:#958575}.duration-summary strong{color:#b66a4c;font-size:18px;font-variant-numeric:tabular-nums}.duration-fields{display:flex;gap:12px}.duration-fields label{display:flex;align-items:center;gap:8px;flex:1;min-width:0;color:#8b7b6b;font-size:12px}.duration-fields input{width:100%;min-width:0;padding:9px 10px;border:1px solid #e5d8cb;border-radius:8px;background:#fff;color:#4f4034;font-size:16px}.duration-fields span{flex-shrink:0}.duration-slider{display:block;width:100%;margin:18px 0 5px;accent-color:#c77955;cursor:pointer}.duration-scale{display:flex;justify-content:space-between;font-size:10px;color:#9a8a7b}.duration-presets{display:flex;gap:6px;flex-wrap:wrap;margin-top:14px}.duration-presets button{border:1px solid #eadfd5;background:#fff;border-radius:7px;padding:6px 9px;color:#8b7665;font-size:11px;cursor:pointer}.duration-presets button.selected{border-color:#ce8767;background:#f5e5d9;color:#ac5c3d}.duration-note,.duration-error{font-size:11px;line-height:1.7;margin:10px 0 0;color:#9b8775}.duration-error{color:#b34737}.duration-panel:disabled{opacity:.65}.duration-panel :focus-visible{outline:2px solid #c77955;outline-offset:3px}@media(max-width:600px){.duration-panel{margin-left:16px;margin-right:16px;padding:12px}}
</style>
