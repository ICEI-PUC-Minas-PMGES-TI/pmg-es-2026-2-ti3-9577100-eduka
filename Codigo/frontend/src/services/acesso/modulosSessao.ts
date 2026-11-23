import { useEffect, useState } from 'react'
import type { PerfilUsuario } from './authService'
import { MODULOS_ESCOLA, type ModuloEscola } from '../../types/escola/escola'

// Fica aqui, e não no authService, porque o api.ts também grava nela (na
// renovação da sessão) e o authService importa o api.ts.
export const USUARIO_KEY = 'eduka.usuario'

/**
 * Disparado quando os módulos da sessão mudam: no login, na renovação ou
 * quando uma requisição volta MODULO_NAO_CONTRATADO. Os menus ouvem e se
 * redesenham sem o módulo.
 */
export const EVENTO_MODULOS_ALTERADOS = 'eduka:modulos-alterados'

/**
 * Módulo sem o qual o perfil não entra no sistema (igual ao RotasPorModulo do
 * backend). Perdido no meio da sessão, a sessão acaba.
 */
export const MODULO_DO_PERFIL: Partial<Record<PerfilUsuario, ModuloEscola>> = {
  FINANCEIRO: 'GESTAO_FINANCEIRA',
  ALUNO: 'PORTAL_ALUNO',
  PROFESSOR: 'PORTAL_PROFESSOR',
}

type DadosGuardados = { login?: string; modulos?: unknown }

function ler(): DadosGuardados {
  try {
    const raw = localStorage.getItem(USUARIO_KEY)
    return raw ? (JSON.parse(raw) as DadosGuardados) : {}
  } catch {
    return {}
  }
}

/**
 * Módulos contratados pela escola de quem está logado, como vieram do login.
 * Null quando não se sabe (SUPER_ADMIN, acesso de demonstração, sessão de
 * antes desta versão): aí a tela mostra tudo e quem barra é o backend.
 */
export function modulosDaSessao(): ModuloEscola[] | null {
  const { modulos } = ler()
  if (!Array.isArray(modulos)) return null
  return MODULOS_ESCOLA.filter((m) => modulos.includes(m))
}

export function temModulo(modulo: ModuloEscola): boolean {
  const modulos = modulosDaSessao()
  return modulos === null || modulos.includes(modulo)
}

/** Grava o login e os módulos da sessão; `modulos` nulo apaga a informação. */
export function gravarSessao(login: string, modulos: ModuloEscola[] | null | undefined): void {
  try {
    localStorage.setItem(USUARIO_KEY, JSON.stringify(modulos ? { login, modulos } : { login }))
  } catch {
    // Silencia erros de storage (ex: navegação anônima com quota cheia)
  }
  avisar()
}

/** Troca só os módulos, mantendo o login guardado. */
export function gravarModulos(modulos: ModuloEscola[] | null | undefined): void {
  gravarSessao(ler().login ?? '', modulos)
}

/** O backend respondeu que a escola não tem o módulo: a tela para de oferecê-lo. */
export function tirarModulo(modulo: ModuloEscola): void {
  const modulos = modulosDaSessao() ?? MODULOS_ESCOLA
  if (!modulos.includes(modulo)) return
  gravarModulos(modulos.filter((m) => m !== modulo))
}

function avisar(): void {
  window.dispatchEvent(new Event(EVENTO_MODULOS_ALTERADOS))
}

/**
 * `temModulo` que redesenha o componente quando os módulos da sessão mudam
 * (ver EVENTO_MODULOS_ALTERADOS), para menus e atalhos sumirem na hora.
 */
export function useModulos(): (modulo: ModuloEscola) => boolean {
  const [, setVersao] = useState(0)
  useEffect(() => {
    const redesenhar = () => setVersao((v) => v + 1)
    window.addEventListener(EVENTO_MODULOS_ALTERADOS, redesenhar)
    return () => window.removeEventListener(EVENTO_MODULOS_ALTERADOS, redesenhar)
  }, [])
  return temModulo
}
