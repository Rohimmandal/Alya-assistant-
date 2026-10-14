import { useEffect, useRef, useState } from 'react';
import {
  ArrowDownToLine, ArrowLeft, ArrowUp, Check, ChevronDown, CircleStop, Download,
  ExternalLink, Headphones, Languages, Mic, Moon, MoreHorizontal, Plus, Radio,
  Settings2, ShieldCheck, Sparkles, Sun, Volume2, X,
} from 'lucide-react';
import { askGemini } from './lib/gemini';
import { createSpeechRecognition, availableVoices, speak } from './lib/speech';
import { readConversations, readMemories, writeConversations, writeMemories, type Conversation, type Message } from './lib/storage';

type Section = 'download' | 'website' | 'android';
type Theme = 'light' | 'dark';

const languages = ['Automatic', 'English', 'Español', 'Français', 'Deutsch', 'हिन्दी', 'বাংলা', 'العربية', 'Português', '日本語', '한국어', '中文'];
const speechLanguageTags: Record<string, string> = {
  English: 'en-US', Español: 'es-ES', Français: 'fr-FR', Deutsch: 'de-DE', हिन्दी: 'hi-IN',
  বাংলা: 'bn-BD', العربية: 'ar-SA', Português: 'pt-BR', 日本語: 'ja-JP', 한국어: 'ko-KR', 中文: 'zh-CN',
};
const modelDefault = 'gemini-2.5-flash';
const websiteUrl = import.meta.env.VITE_OFFICIAL_WEBSITE_URL as string | undefined;
const apkUrl = import.meta.env.VITE_APK_URL as string | undefined;

function newConversation(): Conversation {
  return { id: crypto.randomUUID(), title: 'New conversation', updatedAt: Date.now(), messages: [] };
}

function shortTitle(text: string): string {
  const clean = text.trim().replace(/\s+/g, ' ');
  return clean.length > 38 ? `${clean.slice(0, 38)}…` : clean || 'New conversation';
}

function App() {
  const [section, setSection] = useState<Section>('website');
  const [conversations, setConversations] = useState(readConversations);
  const [memories, setMemories] = useState(readMemories);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [apiKey, setApiKey] = useState(() => sessionStorage.getItem('alya.gemini-key') ?? '');
  const [model, setModel] = useState(() => localStorage.getItem('alya.model') ?? modelDefault);
  const [language, setLanguage] = useState(() => localStorage.getItem('alya.language') ?? 'Automatic');
  const [theme, setTheme] = useState<Theme>(() => localStorage.getItem('alya.theme') === 'dark' ? 'dark' : 'light');
  const [voiceName, setVoiceName] = useState(() => localStorage.getItem('alya.voice') ?? '');
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([]);
  const [speakReplies, setSpeakReplies] = useState(() => localStorage.getItem('alya.speak') === 'true');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [listening, setListening] = useState(false);
  const [micSupported, setMicSupported] = useState(true);
  const [downloadMessage, setDownloadMessage] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const recognitionRef = useRef<ReturnType<typeof createSpeechRecognition>>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const activeConversation = conversations.find((conversation) => conversation.id === activeId) ?? null;
  const messages = activeConversation?.messages ?? [];

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('alya.theme', theme);
  }, [theme]);

  useEffect(() => {
    const updateVoices = () => {
      const available = availableVoices();
      setVoices(available);
      if (!voiceName) {
        const naturalVoice = available.find((voice) => /female|woman|samantha|victoria|zira|susan|aria|jenny/i.test(voice.name));
        if (naturalVoice) setVoiceName(naturalVoice.name);
      }
    };
    updateVoices();
    window.speechSynthesis?.addEventListener('voiceschanged', updateVoices);
    return () => window.speechSynthesis?.removeEventListener('voiceschanged', updateVoices);
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages.length, loading]);

  function persist(next: Conversation[]) {
    setConversations(next);
    writeConversations(next);
  }

  function startConversation() {
    const conversation = newConversation();
    persist([conversation, ...conversations]);
    setActiveId(conversation.id);
    setError('');
  }

  function updateMessages(conversation: Conversation, nextMessages: Message[]) {
    const updatedAt = Date.now();
    const updated = { ...conversation, messages: nextMessages, updatedAt };
    const next = [updated, ...conversations.filter((item) => item.id !== conversation.id)]
      .sort((first, second) => second.updatedAt - first.updatedAt);
    persist(next);
  }

  async function sendMessage(event?: React.FormEvent) {
    event?.preventDefault();
    const text = draft.trim();
    if (!text || loading) return;
    let conversation = activeConversation;
    if (!conversation) {
      conversation = newConversation();
      setActiveId(conversation.id);
      persist([conversation, ...conversations]);
    }
    const userMessage: Message = { id: crypto.randomUUID(), role: 'user', text, createdAt: Date.now() };
    const nextMessages = [...conversation.messages, userMessage];
    const renamed = { ...conversation, title: conversation.messages.length ? conversation.title : shortTitle(text), messages: nextMessages, updatedAt: Date.now() };
    persist([renamed, ...conversations.filter((item) => item.id !== renamed.id)]);
    setDraft('');
    setError('');
    setLoading(true);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const answer = await askGemini({ apiKey, model, language, messages: nextMessages, memories, signal: controller.signal });
      const response: Message = { id: crypto.randomUUID(), role: 'assistant', text: answer, createdAt: Date.now() };
      updateMessages(conversation, [...nextMessages, response]);
      if (speakReplies) speak(answer, voiceName);
    } catch (cause) {
      if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
        setError(cause instanceof Error ? cause.message : 'The request failed. Please try again.');
      }
    } finally {
      abortRef.current = null;
      setLoading(false);
    }
  }

  function beginListening() {
    const recognition = createSpeechRecognition();
    if (!recognition) {
      setMicSupported(false);
      return;
    }
    recognition.lang = language === 'Automatic' ? navigator.language : (speechLanguageTags[language] ?? navigator.language);
    recognition.onresult = (event) => {
      const transcript = Array.from(event.results).map((result) => result[0].transcript).join(' ');
      setDraft((current) => current ? `${current} ${transcript}` : transcript);
    };
    recognition.onerror = () => { setListening(false); setError('Speech recognition failed. Check microphone permission and try again.'); };
    recognition.onend = () => setListening(false);
    recognitionRef.current = recognition;
    setError('');
    setListening(true);
    try { recognition.start(); } catch { setListening(false); setError('Microphone could not start. Please try again.'); }
  }

  function toggleVoice() {
    if (listening) {
      recognitionRef.current?.stop();
      setListening(false);
    } else beginListening();
  }

  function deleteConversation(id: string) {
    persist(conversations.filter((conversation) => conversation.id !== id));
    if (activeId === id) setActiveId(null);
  }

  function renameConversation(conversation: Conversation) {
    const title = window.prompt('Conversation name', conversation.title)?.trim();
    if (title) persist(conversations.map((item) => item.id === conversation.id ? { ...item, title, updatedAt: Date.now() } : item));
  }

  function saveSettings() {
    if (apiKey.trim()) sessionStorage.setItem('alya.gemini-key', apiKey.trim());
    else sessionStorage.removeItem('alya.gemini-key');
    localStorage.setItem('alya.model', model.trim() || modelDefault);
    localStorage.setItem('alya.language', language);
    localStorage.setItem('alya.voice', voiceName);
    localStorage.setItem('alya.speak', String(speakReplies));
    setModel(model.trim() || modelDefault);
    setSettingsOpen(false);
  }

  async function downloadApk() {
    setDownloadMessage('');
    if (!apkUrl) {
      setDownloadMessage('No APK release is configured yet. Check back after a release is published.');
      return;
    }
    const anchor = document.createElement('a');
    anchor.href = apkUrl;
    anchor.download = '';
    anchor.rel = 'noopener noreferrer';
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setDownloadMessage('Download opened. The browser will report its progress and any errors.');
  }

  function clearHistory() {
    if (!window.confirm('Delete all locally saved conversations? This cannot be undone.')) return;
    persist([]);
    setActiveId(null);
  }

  function addMemory() {
    const text = window.prompt('What would you like Alya to remember?')?.trim();
    if (!text || memories.includes(text)) return;
    const next = [...memories, text];
    setMemories(next);
    writeMemories(next);
  }

  function deleteMemory(memory: string) {
    const next = memories.filter((item) => item !== memory);
    setMemories(next);
    writeMemories(next);
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#website" onClick={() => setSection('website')} aria-label="Alya Assistant home">
          <span className="brand-mark"><Sparkles size={19} strokeWidth={1.7} /></span>
          <span className="brand-name">alya<span>.</span></span>
        </a>
        <nav className="primary-nav" aria-label="Primary sections">
          <button className={section === 'download' ? 'nav-link active' : 'nav-link'} onClick={() => setSection('download')}>Download</button>
          <button className={section === 'website' ? 'nav-link active' : 'nav-link'} onClick={() => setSection('website')}>Website</button>
          <button className={section === 'android' ? 'nav-link active' : 'nav-link'} onClick={() => setSection('android')}>Android app</button>
        </nav>
        <div className="topbar-actions">
          <span className="privacy-indicator"><ShieldCheck size={15} /> Local history</span>
          <button className="icon-button settings-trigger" onClick={() => setSettingsOpen(true)} aria-label="Settings" title="Settings"><Settings2 size={19} /></button>
        </div>
      </header>

      {section === 'website' && (
        <section className="workspace" aria-label="Website assistant">
          <aside className="conversation-rail">
            <div className="rail-heading">
              <span>YOUR SPACE</span>
              <button className="icon-button small-icon" aria-label="New conversation" title="New conversation" onClick={startConversation}><Plus size={17} /></button>
            </div>
            <button className="new-chat-button" onClick={startConversation}><Plus size={16} /> New conversation</button>
            <div className="conversation-list">
              {conversations.length === 0 ? <p className="rail-empty">Your conversations will live here.</p> : conversations.map((conversation) => (
                <div className={activeId === conversation.id ? 'conversation-row selected' : 'conversation-row'} key={conversation.id}>
                  <button className="conversation-open" onClick={() => { setActiveId(conversation.id); setError(''); }} title={conversation.title}>{conversation.title}</button>
                  <details className="conversation-menu">
                    <summary aria-label={`Options for ${conversation.title}`}><MoreHorizontal size={16} /></summary>
                    <div className="menu-popover">
                      <button onClick={() => renameConversation(conversation)}>Rename</button>
                      <button onClick={() => deleteConversation(conversation.id)}>Delete</button>
                    </div>
                  </details>
                </div>
              ))}
            </div>
            <div className="rail-footer">
              <span className="status-dot" /> History stays on this device
              {conversations.length > 0 && <button className="text-button" onClick={clearHistory}>Clear all</button>}
            </div>
          </aside>

          <div className="chat-column">
            <div className="chat-header">
              <div className="chat-title-wrap">
                <div className="assistant-avatar"><Sparkles size={17} /></div>
                <div><h1>Alya Assistant</h1><p>{loading ? 'Thinking…' : 'A thoughtful space for what’s next'}</p></div>
              </div>
              <div className="chat-tools">
                <span className="language-chip"><Languages size={14} /> {language}</span>
                <button className="icon-button" onClick={() => setSettingsOpen(true)} aria-label="Open settings" title="Settings"><Settings2 size={18} /></button>
              </div>
            </div>

            <div className="message-scroll" aria-live="polite">
              {messages.length === 0 ? (
                <div className="welcome-block">
                  <div className="welcome-orbit"><Sparkles size={25} strokeWidth={1.4} /></div>
                  <p className="eyebrow">YOUR AI, YOUR SPACE</p>
                  <h2>Good to have you<br />here<span>.</span></h2>
                  <p className="welcome-copy">A clear mind starts with a conversation.<br className="desktop-break" /> What would you like to explore?</p>
                  {!apiKey && <button className="setup-note" onClick={() => setSettingsOpen(true)}><Settings2 size={15} /> Add a Gemini API key to begin</button>}
                </div>
              ) : (
                <div className="messages">
                  {messages.map((message) => <article className={`message ${message.role}`} key={message.id}>
                    {message.role === 'assistant' && <span className="message-avatar"><Sparkles size={14} /></span>}
                    <div className="message-content"><p>{message.text}</p>
                      {message.role === 'assistant' && 'speechSynthesis' in window && <button className="message-audio" aria-label="Read response aloud" title="Read aloud" onClick={() => speak(message.text, voiceName)}><Volume2 size={15} /></button>}
                    </div>
                  </article>)}
                  {loading && <div className="thinking"><span /><span /><span /> <span className="thinking-label">Alya is thinking</span></div>}
                  <div ref={endRef} />
                </div>
              )}
            </div>

            <div className="composer-area">
              {error && <div className="error-banner" role="alert"><span>{error}</span><button className="icon-button small-icon" aria-label="Dismiss error" onClick={() => setError('')}><X size={15} /></button></div>}
              {!micSupported && <p className="inline-note">Speech recognition is not available in this browser. You can still type your message.</p>}
              <form className="composer" onSubmit={sendMessage}>
                <textarea aria-label="Message Alya" placeholder="Write a message…" value={draft} rows={1} onChange={(event) => setDraft(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void sendMessage(); } }} />
                <div className="composer-actions">
                  <button type="button" className={listening ? 'icon-button mic-button listening' : 'icon-button mic-button'} onClick={toggleVoice} aria-label={listening ? 'Stop microphone' : 'Start speech input'} title={listening ? 'Stop listening' : 'Speech to text'}><Mic size={18} /></button>
                  {loading
                    ? <button type="button" className="send-button stop-button" onClick={() => abortRef.current?.abort()} aria-label="Stop response" title="Stop response"><CircleStop size={18} /></button>
                    : <button type="submit" className="send-button" disabled={!draft.trim()} aria-label="Send message" title="Send message"><ArrowUp size={19} /></button>}
                </div>
              </form>
              <div className="composer-foot"><span><ShieldCheck size={13} /> Private on this device</span><button className="live-status" title="Live audio streaming is not configured"> <Radio size={13} /> Live voice unavailable</button></div>
            </div>
          </div>
        </section>
      )}

      {section === 'download' && <section className="download-page">
        <button className="back-link" onClick={() => setSection('website')}><ArrowLeft size={16} /> Back to assistant</button>
        <div className="download-layout">
          <div className="download-copy">
            <p className="eyebrow">ALYA FOR ANDROID</p>
            <h1>A little more<br />room to <em>move.</em></h1>
            <p className="download-intro">Take your conversations with Alya along. The Android app is built separately to work with the features your device permits.</p>
            <div className="release-card">
              <div className="release-icon"><ArrowDownToLine size={20} /></div>
              <div className="release-info"><strong>{import.meta.env.VITE_RELEASE_VERSION || 'Release not published'}</strong><span>{import.meta.env.VITE_RELEASE_DATE || 'No published version yet'}</span></div>
              <button className="download-button" onClick={() => void downloadApk()} disabled={!apkUrl}><Download size={17} /> Download APK</button>
              {!apkUrl && <p className="release-unavailable">An APK download will appear here once a real release URL is configured.</p>}
              {downloadMessage && <p className="download-message" role="status">{downloadMessage}</p>}
              {import.meta.env.VITE_RELEASE_NOTES && <p className="release-notes">{import.meta.env.VITE_RELEASE_NOTES}</p>}
            </div>
            <div className="requirements-line"><span>Android 10 or newer</span><span>Version and size shown with release</span></div>
          </div>
          <div className="phone-art" aria-label="Illustration of the Alya Android app"><div className="phone-frame"><div className="phone-notch" /><div className="phone-screen"><div className="phone-status"><span>9:41</span><span>•••</span></div><div className="phone-app-title"><span><Sparkles size={14} /></span> alya</div><div className="phone-greeting">A fresh start,<br /><i>whenever you need.</i></div><div className="phone-bubble">What’s on your mind today?</div><div className="phone-input"><span>Write a message…</span><span><ArrowUp size={13} /></span></div><div className="phone-home" /></div></div><div className="phone-caption"><span className="status-dot" /> Designed for Android</div></div>
        </div>
        <div className="install-strip"><div><strong>Install an APK</strong><span>Allow installs from your browser or file manager when Android prompts you. Only install releases from a source you trust.</span></div><a href={websiteUrl || '#website'} onClick={() => !websiteUrl && setSection('website')} target={websiteUrl ? '_blank' : undefined} rel="noreferrer">Visit official website <ExternalLink size={15} /></a></div>
      </section>}

      {section === 'android' && <section className="android-page">
        <div className="android-hero"><p className="eyebrow">MADE FOR YOUR DEVICE</p><h1>Alya, closer<br />to <em>home.</em></h1><p>A native Android experience, designed around the permissions and controls your device actually supports.</p><button className="dark-cta" onClick={() => setSection('download')}>Explore the APK release <ArrowDownToLine size={16} /></button><div className="android-hero-art"><div className="app-tile"><Sparkles size={24} /><span>alya.</span></div><div className="floating-line line-one" /><div className="floating-line line-two" /></div></div>
        <div className="feature-list"><div className="feature-heading"><span>ON DEVICE</span><span>CAPABILITIES</span></div>
          <article><span className="feature-number">01</span><div><h2>Conversations that stay yours</h2><p>Chat history is stored locally on your device. No account or sign-up required.</p></div><ShieldCheck size={19} /></article>
          <article><span className="feature-number">02</span><div><h2>Voice, when you choose</h2><p>Speech recognition and text-to-speech are available through Android services. Live streaming and wake-word listening need additional providers and explicit setup.</p></div><Headphones size={19} /></article>
          <article><span className="feature-number">03</span><div><h2>Controls with clear limits</h2><p>Open supported settings and check device information. Sensitive actions require permission; Android keeps protected controls in your hands.</p></div><ShieldCheck size={19} /></article>
          <div className="feature-footer"><span>Android 10+ · No mandatory account · Your permissions, your choice</span><button className="text-button" onClick={() => setSettingsOpen(true)}>Configure web assistant <ChevronDown size={14} /></button></div>
        </div>
      </section>}

      <footer className="site-footer"><span>ALYA ASSISTANT <span className="footer-dot">·</span> A virtual assistant, not a person</span><span>Local-first by design</span></footer>

      {settingsOpen && <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setSettingsOpen(false); }}>
        <section className="settings-dialog" role="dialog" aria-modal="true" aria-labelledby="settings-title">
          <div className="settings-header"><div><p className="eyebrow">YOUR PREFERENCES</p><h2 id="settings-title">Settings</h2></div><button className="icon-button" onClick={() => setSettingsOpen(false)} aria-label="Close settings"><X size={19} /></button></div>
          <label className="field-label" htmlFor="api-key">Gemini API key</label><input id="api-key" type="password" autoComplete="off" placeholder="Paste your key" value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
          <p className="field-help">Sent directly to Google over HTTPS and kept in this browser session only. Create a key in Google AI Studio.</p>
          <label className="field-label" htmlFor="model">Gemini model</label><input id="model" value={model} onChange={(event) => setModel(event.target.value)} placeholder={modelDefault} />
          <p className="field-help">Default: {modelDefault}. Choose a model available to your API key.</p>
          <label className="field-label" htmlFor="language">Conversation language</label><select id="language" value={language} onChange={(event) => setLanguage(event.target.value)}>{languages.map((item) => <option key={item}>{item}</option>)}</select>
          <div className="memory-heading"><div><strong>Saved memories</strong><span>Only details you add are shared with Gemini.</span></div><button className="text-button" onClick={addMemory}><Plus size={13} /> Add</button></div>
          {memories.length === 0 ? <p className="field-help">Nothing saved. Alya will not add memories automatically.</p> : <ul className="memory-list">{memories.map((memory) => <li key={memory}><span>{memory}</span><button className="text-button" onClick={() => deleteMemory(memory)} aria-label={`Delete memory: ${memory}`}>Delete</button></li>)}</ul>}
          <p className="field-help">Memories stay in this browser and are included with Gemini requests. Delete them here to stop sharing them.</p>
          <label className="field-label" htmlFor="voice">Spoken voice</label><select id="voice" value={voiceName} onChange={(event) => setVoiceName(event.target.value)}><option value="">System default</option>{voices.map((voice) => <option key={`${voice.name}-${voice.lang}`} value={voice.name}>{voice.name} ({voice.lang})</option>)}</select>
          {voices.length === 0 && <p className="field-help">No browser voices detected. Available voices depend on your operating system.</p>}
          <div className="setting-row"><div><strong>Read replies aloud</strong><span>Use browser text-to-speech</span></div><button className={speakReplies ? 'toggle enabled' : 'toggle'} role="switch" aria-checked={speakReplies} aria-label="Read replies aloud" onClick={() => setSpeakReplies(!speakReplies)}><span /></button></div>
          <div className="setting-row"><div><strong>Appearance</strong><span>{theme === 'light' ? 'Light theme' : 'Dark theme'}</span></div><button className="icon-button" aria-label="Toggle theme" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>{theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}</button></div>
          <div className="settings-footer"><span><Check size={14} /> No account required</span><button className="save-button" onClick={saveSettings}>Save settings</button></div>
        </section>
      </div>}
    </main>
  );
}

export default App;