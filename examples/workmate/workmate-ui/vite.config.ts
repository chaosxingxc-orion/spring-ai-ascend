import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const electron = process.env.VITE_TARGET === 'electron' || mode === 'electron';
  return {
    plugins: [react()],
    base: electron ? './' : '/',
    server: {
      port: 5174,
      strictPort: false,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/actuator': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    preview: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/actuator': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
