import { beforeEach, describe, expect, it } from 'vitest';
import { historyToContents, readConversations, writeConversations, type Message } from './storage';

describe('conversation storage', () => {
  beforeEach(() => localStorage.clear());

  it('persists, reopens, and deletes conversations', () => {
    const conversation = { id: 'one', title: 'Hello', updatedAt: 10, messages: [] };
    writeConversations([conversation]);
    expect(readConversations()).toEqual([conversation]);
    writeConversations([]);
    expect(readConversations()).toEqual([]);
  });

  it('recovers cleanly from invalid local data', () => {
    localStorage.setItem('alya.conversations.v1', '{');
    expect(readConversations()).toEqual([]);
  });

  it('limits context to the latest twenty messages and maps assistant roles', () => {
    const messages: Message[] = Array.from({ length: 25 }, (_, index) => ({
      id: String(index), role: index % 2 ? 'assistant' : 'user', text: String(index), createdAt: index,
    }));
    const contents = historyToContents(messages);
    expect(contents).toHaveLength(20);
    expect(contents[0]).toEqual({ role: 'model', parts: [{ text: '5' }] });
  });
});