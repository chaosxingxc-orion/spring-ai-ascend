import { MemorySettingsPanel } from '../settings/MemorySettingsPanel';

/** Standalone memory page — prefer SettingsView /settings/memory. */
export function MemorySettingsView() {
  return (
    <main className="memory-settings-page">
      <header className="memory-settings-header">
        <h1>长期记忆</h1>
      </header>
      <MemorySettingsPanel />
    </main>
  );
}
