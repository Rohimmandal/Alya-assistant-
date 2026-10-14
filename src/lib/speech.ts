type SpeechResult = { results: ArrayLike<{ 0: { transcript: string } }> };
type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechResult) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
};

export function createSpeechRecognition(): SpeechRecognitionLike | null {
  const browserWindow = window as Window & {
    SpeechRecognition?: new () => SpeechRecognitionLike;
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
  };
  const Recognition = browserWindow.SpeechRecognition ?? browserWindow.webkitSpeechRecognition;
  if (!Recognition) return null;
  const recognition = new Recognition();
  recognition.continuous = false;
  recognition.interimResults = false;
  return recognition;
}

export function availableVoices(): SpeechSynthesisVoice[] {
  if (!('speechSynthesis' in window)) return [];
  return window.speechSynthesis.getVoices();
}

export function speak(text: string, voiceName: string): boolean {
  if (!('speechSynthesis' in window)) return false;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  const voice = availableVoices().find((candidate) => candidate.name === voiceName);
  if (voice) utterance.voice = voice;
  window.speechSynthesis.speak(utterance);
  return true;
}