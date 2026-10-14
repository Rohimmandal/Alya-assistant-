import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import App from './App';

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

afterEach(cleanup);

describe('primary app navigation', () => {
  it('opens directly to the website without login or sign-up', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Alya Assistant' })).toBeTruthy();
    expect(screen.queryByText(/sign up|log in|create account/i)).toBeNull();
    expect(screen.getByRole('navigation', { name: 'Primary sections' }).querySelectorAll('button')).toHaveLength(3);
  });

  it('shows the real unavailable state until an APK URL is configured', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Download' }));
    expect(screen.getByText('Release not published')).toBeTruthy();
    expect(screen.getByRole('button', { name: /download apk/i })).toHaveProperty('disabled', true);
  });

  it('exposes the native-app information as the third primary section', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Android app' }));
    expect(screen.getByRole('heading', { name: /Alya, closer to home\./ })).toBeTruthy();
    expect(screen.getByText(/wake-word listening need additional providers/i)).toBeTruthy();
  });
});