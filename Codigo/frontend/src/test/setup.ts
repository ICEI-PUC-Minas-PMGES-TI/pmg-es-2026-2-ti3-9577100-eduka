import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// findBy e waitFor esperam 1 s por padrão: sob carga, a resposta simulada chegava depois disso.
configure({ asyncUtilTimeout: 4000 })

// O jsdom ainda não abre <dialog> como modal; o Modal só precisa do atributo open.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.open = false
  }
}

// O jsdom não tem scrollIntoView; trocar de página (Paginacao) rola até o topo da lista.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = function scrollIntoView() {}
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
