import { useEffect, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { Button } from '../components/Button/Button'
import { ApiError } from '../services/api'
import { limparSessao } from '../services/authService'
import { buscarAvisoPrivacidade } from '../services/privacidadeService'
import { aceiteConferido, registrarAceitePrivacidade, type EstadoAvisoPrivacidade } from './aceitePrivacidade'
import { paths } from './paths'
import styles from './ExigeAceitePrivacidade.module.css'

type Situacao = 'conferindo' | 'aceito' | 'pendente' | 'sessaoVencida' | 'erro'

/**
 * Antes do painel de qualquer perfil, confere se o usuário aceitou o aviso de
 * privacidade em vigor (IU29). Quem não aceitou vai para a tela do aviso e
 * volta para onde ia depois de aceitar.
 */
export function ExigeAceitePrivacidade({ login, children }: { login: string; children: ReactNode }) {
  const location = useLocation()
  const [situacao, setSituacao] = useState<Situacao>(aceiteConferido() ? 'aceito' : 'conferindo')
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    if (situacao !== 'conferindo') return
    const controller = new AbortController()
    buscarAvisoPrivacidade(controller.signal)
      .then((aviso) => {
        if (aviso.aceitoEm) registrarAceitePrivacidade()
        setSituacao(aviso.aceitoEm ? 'aceito' : 'pendente')
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        // Token vencido: a sessão acabou, volta para o login da área.
        if (e instanceof ApiError && e.status === 401) {
          limparSessao()
          setSituacao('sessaoVencida')
          return
        }
        setSituacao('erro')
      })
    return () => controller.abort()
  }, [situacao, tentativa])

  if (situacao === 'aceito') return children
  if (situacao === 'sessaoVencida') return <Navigate to={login} replace />
  if (situacao === 'pendente') {
    const voltarPara = `${location.pathname}${location.search}`
    return (
      <Navigate to={paths.avisoPrivacidade} replace state={{ voltarPara } satisfies EstadoAvisoPrivacidade} />
    )
  }
  if (situacao === 'erro') {
    return (
      <div className={styles.mensagem} role="alert">
        <span>Não foi possível carregar o aviso de privacidade.</span>
        <Button
          size="sm"
          onClick={() => {
            setTentativa((t) => t + 1)
            setSituacao('conferindo')
          }}
        >
          Tentar novamente
        </Button>
      </div>
    )
  }
  return (
    <div className={styles.mensagem} role="status">
      Carregando…
    </div>
  )
}
