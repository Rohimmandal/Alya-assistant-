export type Message = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  createdAt: number;
};

export type Conversation = {
  id: string;
  title: string;
  updatedAt: number;
  messages: Message[];
};

const STORAGE_KEY = 'alya.conversations.v1';
const MEMORY_KEY = 'alya.memories.v1';

export function readMemories(): string[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(MEMORY_KEY) ?? '[]');
    return Array.isArray(stored) ? stored.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

export function writeMemories(memories: string[]): void {
  localStorage.setItem(MEMORY_KEY, JSON.stringify(memories));
}

export function readConversations(): Conversation[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(stored)) return [];
    return stored.filter((item): item is Conversation =>
      Boolean(item && typeof item.id === 'string' && typeof item.title === 'string' &&
        typeof item.updatedAt === 'number' && Array.isArray(item.messages)),
    ).sort((first, second) => second.updatedAt - first.updatedAt);
  } catch {
    return [];
  }
}

export function writeConversations(conversations: Conversation[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
}

export function historyToContents(messages: Message[]) {
  return messages.slice(-20).map(({ role, text }) => ({
    role: role === 'assistant' ? 'model' : 'user',
    parts: [{ text }],
  }));
}