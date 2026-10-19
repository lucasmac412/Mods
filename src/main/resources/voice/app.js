"use strict";

(() => {
  const byId = (id) => document.getElementById(id);
  const token = new URLSearchParams(location.hash.slice(1)).get("token");
  // The fragment never reaches the server. Keep it in memory, outside history and browser storage.
  if (location.hash) history.replaceState(null, "", location.pathname);
  const activate = byId("activate"), pause = byId("pause"), status = byId("status");
  const input = byId("message"), send = byId("send"), mic = byId("microphone");
  const micStatus = byId("microphone-status"), voiceSelect = byId("voice"), speak = byId("speak");
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  const synthesis = window.speechSynthesis;
  let connected = false, connecting = false, cursor = null, timer = null, recognition = null;
  let portugueseVoices = [], listening = false, failures = 0;
  let generation = 0;
  const speechQueue = [];
  let currentUtterance = null;

  function transcript(who, text) {
    const item = document.createElement("li");
    const label = document.createElement("strong");
    label.textContent = who + ": ";
    item.append(label, document.createTextNode(text));
    const list = byId("conversation");
    list.append(item);
    while (list.children.length > 80) list.firstChild.remove();
  }

  function updateControls() {
    activate.disabled = connected || connecting || !token;
    pause.disabled = !connected && !connecting;
    input.disabled = !connected;
    send.disabled = !connected;
    mic.disabled = !connected || !Recognition;
  }

  function listVoices() {
    if (!synthesis) {
      speak.checked = false;
      speak.disabled = true;
      voiceSelect.disabled = true;
      return;
    }
    const previous = voiceSelect.value;
    portugueseVoices = synthesis.getVoices().filter((voice) => /^pt(?:[-_]|$)/i.test(voice.lang));
    portugueseVoices.sort((a, b) => Number(/^pt[-_]BR$/i.test(b.lang)) - Number(/^pt[-_]BR$/i.test(a.lang)));
    voiceSelect.replaceChildren(new Option("Voz padrão em português do sistema", ""));
    portugueseVoices.forEach((voice, index) => voiceSelect.add(new Option(voice.name + " · " + voice.lang + (voice.localService ? " · local" : " · serviço online"), String(index))));
    if (previous !== "" && Number(previous) < portugueseVoices.length) voiceSelect.value = previous;
  }

  function playNext() {
    if (!connected || listening || !speak.checked || !synthesis || currentUtterance || !speechQueue.length) return;
    const utterance = new SpeechSynthesisUtterance(speechQueue.shift());
    utterance.lang = "pt-BR";
    const preferred = voiceSelect.value === "" ? portugueseVoices[0] : portugueseVoices[Number(voiceSelect.value)];
    if (preferred) utterance.voice = preferred;
    utterance.rate = 1;
    currentUtterance = utterance;
    const complete = () => {
      if (currentUtterance !== utterance) return;
      currentUtterance = null;
      playNext();
    };
    utterance.onend = complete;
    utterance.onerror = complete;
    synthesis.speak(utterance);
  }

  function say(text) {
    if (!connected || !speak.checked || !synthesis) return;
    // Bound browser speech as well as the server history, so a long backlog cannot keep talking.
    if (speechQueue.length >= 8) speechQueue.shift();
    speechQueue.push(text);
    playNext();
  }

  function stopSpeech() {
    speechQueue.length = 0;
    currentUtterance = null;
    if (synthesis) synthesis.cancel();
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: { "Authorization": "Bearer " + token, ...(options.headers || {}) },
      credentials: "omit",
      cache: "no-store",
      signal: AbortSignal.timeout(5000)
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || "Não foi possível conversar com o jogo.");
    return body;
  }

  function stop(message = "Conexão e voz pausadas. Clique em ativar para continuar.") {
    generation++;
    connected = false;
    connecting = false;
    clearTimeout(timer);
    if (recognition) recognition.abort();
    recognition = null;
    listening = false;
    mic.textContent = "Falar pelo microfone";
    mic.setAttribute("aria-pressed", "false");
    stopSpeech();
    status.textContent = message;
    updateControls();
  }

  async function poll(activeGeneration) {
    if (!connected || activeGeneration !== generation) return;
    try {
      const data = await api("/api/messages?cursor=" + cursor);
      if (!connected || activeGeneration !== generation) return;
      for (const message of data.messages) {
        if (message.sequence <= cursor) continue;
        transcript("Luma", message.text);
        say(message.text);
        cursor = message.sequence;
      }
      cursor = Math.max(cursor, data.cursor);
      failures = 0;
      status.textContent = "Conectada ao jogo local. Pode conversar com a Luma.";
    } catch (error) {
      if (!connected || activeGeneration !== generation) return;
      failures++;
      if (failures >= 3) {
        stop("Conexão perdida. Verifique o jogo e abra um novo link com /luma voz. " + error.message);
        return;
      }
      status.textContent = "Tentando reconectar ao jogo local…";
    }
    if (connected && activeGeneration === generation) timer = setTimeout(() => poll(activeGeneration), 900);
  }

  activate.addEventListener("click", async () => {
    if (!token || connecting || connected) return;
    connecting = true;
    const activeGeneration = ++generation;
    updateControls();
    status.textContent = "Conectando ao jogo local…";
    // A speech call initiated by the click unlocks speech on browsers that require user activation.
    if (synthesis && speak.checked) {
      const greeting = new SpeechSynthesisUtterance("Voz da Luma ativada.");
      greeting.lang = "pt-BR";
      if (portugueseVoices[0]) greeting.voice = portugueseVoices[0];
      synthesis.speak(greeting);
    }
    try {
      const data = await api("/api/status");
      if (activeGeneration !== generation) return;
      if (!data.running) throw new Error("O serviço de voz está desligado.");
      if (cursor === null) cursor = data.cursor;
      connected = true;
      connecting = false;
      failures = 0;
      updateControls();
      input.focus();
      poll(activeGeneration);
    } catch (error) {
      if (activeGeneration === generation) stop(error.message + " Abra novamente o link recebido no jogo.");
    }
  });

  async function sendText(text) {
    if (!connected) return;
    const clean = text.trim();
    if (!clean || clean.length > 500) {
      status.textContent = "Digite uma mensagem de até 500 caracteres.";
      return;
    }
    send.disabled = true;
    try {
      await api("/api/text", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text: clean }) });
      if (!connected) return;
      transcript("Você", clean);
      input.value = "";
      status.textContent = "Mensagem enviada para a Luma.";
    } catch (error) {
      status.textContent = error.message;
    } finally {
      send.disabled = !connected;
    }
  }

  byId("message-form").addEventListener("submit", (event) => {
    event.preventDefault();
    sendText(input.value);
  });
  pause.addEventListener("click", () => stop());
  speak.addEventListener("change", () => { if (!speak.checked) stopSpeech(); });
  mic.addEventListener("click", () => {
    if (!connected || !Recognition) return;
    if (listening && recognition) {
      recognition.stop();
      return;
    }
    stopSpeech();
    const session = new Recognition();
    recognition = session;
    session.lang = "pt-BR";
    session.continuous = false;
    session.interimResults = false;
    session.maxAlternatives = 1;
    session.onstart = () => {
      if (!connected || recognition !== session) { session.abort(); return; }
      listening = true;
      mic.textContent = "Terminar gravação";
      mic.setAttribute("aria-pressed", "true");
      micStatus.textContent = "Ouvindo… Diga uma ordem curta; ao terminar, ela será enviada à Luma.";
    };
    session.onresult = (event) => {
      if (!connected || recognition !== session) return;
      const text = event.results[0][0].transcript.trim();
      input.value = text.slice(0, 500);
      micStatus.textContent = "Reconhecido: “" + text + "”";
      sendText(text);
    };
    session.onerror = (event) => {
      if (recognition !== session) return;
      const descriptions = {
        "not-allowed": "Permissão do microfone negada. Autorize nas configurações do navegador ou digite a mensagem.",
        "audio-capture": "Não foi possível abrir o microfone. Confira se ele está conectado.",
        "no-speech": "Não ouvi uma frase. Clique novamente para tentar.",
        "network": "O reconhecimento está indisponível. Confira a conexão do navegador ou use texto.",
        "language-not-supported": "Este navegador não oferece reconhecimento em português. Use texto.",
        "service-not-allowed": "O navegador bloqueou o serviço de reconhecimento. Use texto.",
        "aborted": "Gravação encerrada."
      };
      micStatus.textContent = descriptions[event.error] || "O reconhecimento falhou. Você pode digitar sua mensagem.";
    };
    session.onend = () => {
      if (recognition !== session) return;
      recognition = null;
      listening = false;
      mic.textContent = "Falar pelo microfone";
      mic.setAttribute("aria-pressed", "false");
      if (micStatus.textContent.startsWith("Ouvindo")) micStatus.textContent = "Gravação encerrada. Clique para falar outra vez.";
      playNext();
    };
    try { session.start(); } catch {
      recognition = null;
      listening = false;
      micStatus.textContent = "Não foi possível iniciar o reconhecimento. Use texto ou outro navegador compatível.";
    }
  });

  if (!Recognition) micStatus.textContent = "Seu navegador não oferece reconhecimento de voz. Use Chrome/Edge compatível ou digite sua mensagem.";
  if (!token) status.textContent = "Link sem chave de conexão. No jogo, digite /luma voz e abra o link completo. Não recarregue esta aba.";
  listVoices();
  if (synthesis) synthesis.addEventListener("voiceschanged", listVoices);
  window.addEventListener("pagehide", () => stop("Painel encerrado."));
  updateControls();
})();
