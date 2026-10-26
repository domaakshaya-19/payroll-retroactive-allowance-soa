import { defineConfig } from 'vite';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export default defineConfig(({ command }) => {
  let https;
  if (command === 'serve') {
    try {
      https = {
        cert: readFileSync(fileURLToPath(new URL('./.local/localhost.pem', import.meta.url))),
        key: readFileSync(fileURLToPath(new URL('./.local/localhost-key.pem', import.meta.url))),
      };
    } catch (error) {
      throw new Error('Frontend HTTPS certificate files are missing or unreadable. Create frontend/.local/localhost.pem and localhost-key.pem with mkcert; see README frontend setup.', { cause: error });
    }
  }
  const server = { host: 'localhost', port: 5173, strictPort: true, https };
  return {
    server: {
      ...server,
      proxy: {
        '/api': {
          target: 'https://localhost:8080',
          changeOrigin: true,
          secure: true,
          configure(proxy) {
            // This is a same-origin browser request. Do not forward its dev-server
            // Origin into Spring's cross-origin checks; keep Authorization intact.
            proxy.on('proxyReq', proxyRequest => proxyRequest.removeHeader('origin'));
          },
        },
      },
    },
    preview: server,
  };
});
