const state = {
  voices: [],
  selectedVoice: "zm_yunxi",
  selectedGender: "全部",
  lastAudioUrl: "",
  audioCache: new Map(),
};

const els = {
  status: document.getElementById("serviceStatus"),
  voiceCount: document.getElementById("voiceCount"),
  voiceList: document.getElementById("voiceList"),
  form: document.getElementById("ttsForm"),
  text: document.getElementById("textInput"),
  speed: document.getElementById("speedInput"),
  speedValue: document.getElementById("speedValue"),
  sampleRate: document.getElementById("sampleRateInput"),
  generateButton: document.getElementById("generateButton"),
  downloadButton: document.getElementById("downloadButton"),
  player: document.getElementById("player"),
  message: document.getElementById("message"),
  tabs: Array.from(document.querySelectorAll(".tab")),
};

function setStatus(text, className = "") {
  els.status.textContent = text;
  els.status.className = `status ${className}`.trim();
}

function setMessage(text, isError = false) {
  els.message.textContent = text;
  els.message.className = `message${isError ? " error" : ""}`;
}

function renderVoices() {
  const visible = state.voices.filter((voice) => {
    return state.selectedGender === "全部" || voice.gender === state.selectedGender;
  });

  els.voiceCount.textContent = `${state.voices.length} 种`;
  els.voiceList.innerHTML = "";

  for (const voice of visible) {
    const voiceNumber = visible.filter((item) => item.gender === voice.gender).indexOf(voice) + 1;
    const button = document.createElement("button");
    button.type = "button";
    button.className = `voice-card${voice.voice === state.selectedVoice ? " active" : ""}`;
    button.innerHTML = `
      <span>
        <span class="voice-name">${voice.gender || "音色"} ${voiceNumber}</span>
        <span class="voice-meta">点击试听</span>
      </span>
      <span class="pill">${voice.gender || "音色"}</span>
    `;
    button.addEventListener("click", () => {
      state.selectedVoice = voice.voice;
      renderVoices();
      generateAudio({ autoplay: true, useCache: true });
    });
    els.voiceList.appendChild(button);
  }
}

async function loadVoices() {
  try {
    const response = await fetch("/api/tts/voices");
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    state.voices = await response.json();
    if (state.voices.length > 0 && !state.voices.some((voice) => voice.voice === state.selectedVoice)) {
      state.selectedVoice = state.voices[0].voice;
    }
    setStatus("服务正常", "ok");
    renderVoices();
  } catch (error) {
    setStatus("服务异常", "error");
    setMessage(`读取音色失败：${error.message}`, true);
  }
}

function currentAudioKey() {
  return JSON.stringify({
    text: els.text.value.trim(),
    voice: state.selectedVoice,
    speed: Number(els.speed.value),
    sampleRate: Number(els.sampleRate.value),
  });
}

async function playCachedAudio(cacheEntry) {
  els.player.src = cacheEntry.url;
  els.downloadButton.disabled = false;
  await els.player.play();
}

async function generateAudio(options = {}) {
  const autoplay = options.autoplay === true;
  const useCache = options.useCache === true;
  const text = els.text.value.trim();
  if (!text) {
    setMessage("文字不能为空。", true);
    return;
  }

  const cacheKey = currentAudioKey();
  const cached = state.audioCache.get(cacheKey);
  if (useCache && cached) {
    state.lastAudioUrl = cached.url;
    try {
      if (autoplay) {
        await playCachedAudio(cached);
      } else {
        els.player.src = cached.url;
        els.downloadButton.disabled = false;
      }
      setMessage("已使用缓存音频，直接播放。");
    } catch (error) {
      setMessage("已生成，可点击播放器播放。");
    }
    return;
  }

  els.generateButton.disabled = true;
  els.downloadButton.disabled = true;
  setMessage(autoplay ? "生成并播放中..." : "生成中...");

  try {
    const response = await fetch("/api/tts/kokoro", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text,
        voice: state.selectedVoice,
        speed: Number(els.speed.value),
        sampleRate: Number(els.sampleRate.value),
        format: "wav",
      }),
    });

    if (!response.ok) {
      const detail = await response.text();
      throw new Error(detail || `HTTP ${response.status}`);
    }

    const blob = await response.blob();
    state.lastAudioUrl = URL.createObjectURL(blob);
    state.audioCache.set(cacheKey, {
      url: state.lastAudioUrl,
      blob,
    });
    els.player.src = state.lastAudioUrl;
    els.downloadButton.disabled = false;
    if (autoplay) {
      try {
        await els.player.play();
        setMessage(`正在播放 ${Math.round(blob.size / 1024)} KB WAV。`);
      } catch (error) {
        setMessage(`已生成 ${Math.round(blob.size / 1024)} KB WAV，可点击播放器播放。`);
      }
    } else {
      setMessage(`已生成 ${Math.round(blob.size / 1024)} KB WAV，可直接试听。`);
    }
  } catch (error) {
    setMessage(`生成失败：${error.message}`, true);
  } finally {
    els.generateButton.disabled = false;
  }
}

els.speed.addEventListener("input", () => {
  els.speedValue.value = Number(els.speed.value).toFixed(2);
});

els.form.addEventListener("submit", (event) => {
  event.preventDefault();
  generateAudio();
});

els.downloadButton.addEventListener("click", () => {
  if (!state.lastAudioUrl) return;
  const link = document.createElement("a");
  link.href = state.lastAudioUrl;
  link.download = `voice-${state.selectedVoice}-${els.sampleRate.value}hz.wav`;
  link.click();
});

for (const tab of els.tabs) {
  tab.addEventListener("click", () => {
    state.selectedGender = tab.dataset.gender;
    els.tabs.forEach((item) => item.classList.toggle("active", item === tab));
    renderVoices();
  });
}

loadVoices();
