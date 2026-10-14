import { afterEach, describe, expect, it, vi } from 'vitest';
import { askGemini } from './gemini';

afterEach(() => vi.unstubAllGlobals());

describe('Gemini requests', () => {
  it('uses the API-key header and returns the provider response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: 'Hello from Gemini.' }] } }],
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const answer = await askGemini({
      apiKey: 'user-key', model: 'gemini-2.5-flash', language: 'Automatic',
      messages: [{ id: '1', role: 'user', text: 'Hi', createdAt: 1 }],
    });
    expect(answer).toBe('Hello from Gemini.');
    expect(fetchMock.mock.calls[0][1].headers['x-goog-api-key']).toBe('user-key');
    expect(fetchMock.mock.calls[0][0]).not.toContain('user-key');
  });

  it('explains missing keys and quota errors without leaking provider details', async () => {
    await expect(askGemini({ apiKey: '', model: 'gemini-2.5-flash', language: 'Automatic', messages: [] }))
      .rejects.toThrow('Add your Gemini API key');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 429 })));
    await expect(askGemini({ apiKey: 'user-key', model: 'gemini-2.5-flash', language: 'Automatic', messages: [] }))
      .rejects.toThrow('rate limit or quota');
  });
});