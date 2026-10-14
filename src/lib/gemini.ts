import type { Message } from './storage';
import { historyToContents } from './storage';

export type GeminiOptions = {
  apiKey: string;
  model: string;
  language: string;
  messages: Message[];
  memories?: string[];
  signal?: AbortSignal;
};

export async function askGemini({ apiKey, model, language, messages, memories = [], signal }: GeminiOptions): Promise<string> {
  if (!apiKey.trim()) throw new Error('Add your Gemini API key in Settings to start a conversation.');
  const modelId = model.trim();
  if (!modelId || !/^[a-zA-Z0-9._-]+$/.test(modelId)) {
    throw new Error('Enter a valid Gemini model identifier in Settings.');
  }

  const timeout = AbortSignal.timeout(30_000);
  const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
  let response: Response;
  try {
    response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(modelId)}:generateContent`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey.trim() },
        body: JSON.stringify({
          system_instruction: {
            parts: [{ text: `You are Alya Assistant, a virtual assistant, not a human. Reply naturally and respectfully. Respond in ${language === 'Automatic' ? 'the language the user is using' : language}, unless asked to switch.${memories.length ? ` The user explicitly saved these memories for personalization: ${memories.join('; ')}.` : ''}` }],
          },
          contents: historyToContents(messages),
          generationConfig: { maxOutputTokens: 2048 },
        }),
        signal: requestSignal,
      },
    );
  } catch (error) {
    if (signal?.aborted) throw new DOMException('Request cancelled.', 'AbortError');
    if (error instanceof DOMException && error.name === 'TimeoutError') {
      throw new Error('Gemini took too long to respond. Please try again.');
    }
    throw new Error('Could not reach Gemini. Check your connection and try again.');
  }

  if (!response.ok) {
    if (response.status === 400 || response.status === 401 || response.status === 403) {
      throw new Error('Gemini rejected this API key or request. Check your key and model in Settings.');
    }
    if (response.status === 429) throw new Error('Gemini rate limit or quota reached. Wait a moment or check your Google AI plan.');
    if (response.status >= 500) throw new Error('Gemini is temporarily unavailable. Please try again later.');
    throw new Error(`Gemini request failed (HTTP ${response.status}).`);
  }

  const data: unknown = await response.json();
  const text = extractResponseText(data);
  if (!text) throw new Error('Gemini returned no text. The response may have been blocked; try rephrasing.');
  return text;
}

function extractResponseText(data: unknown): string {
  if (!data || typeof data !== 'object') return '';
  const candidates = (data as { candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }> }).candidates;
  return candidates?.[0]?.content?.parts?.map((part) => part.text ?? '').join('').trim() ?? '';
}