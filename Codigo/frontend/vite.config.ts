/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Testes de tela (npm test): DOM do jsdom, nada de navegador nem backend.
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Cada arquivo sobe o próprio jsdom, e um por núcleo disputa CPU com o IDE, o Maven e o
    // Docker: com a máquina ocupada, testes estouravam os 5 s padrão sem nada errado na tela.
    // Metade dos núcleos e 15 s por teste deixam a suíte estável sem esconder espera infinita.
    maxWorkers: '50%',
    testTimeout: 15_000,
  },
})
