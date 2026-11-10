import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  // API_PROXY_TARGET is intentionally server-only: it is never embedded in browser code.
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      allowedHosts: true,
      proxy: {
        '/v1': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
        '/health': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
      },
    },
    preview: { host: '0.0.0.0', allowedHosts: true },
    build: { sourcemap: false },
  };
});
