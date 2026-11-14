import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import logoEduka from '../../assets/eduka-logo.svg'
import { Button } from '../../components/ui/Button/Button'
import { Icon, type IconName } from '../../components/ui/Icon/Icon'
import { Modal, ModalAlerta } from '../../components/modal/Modal/Modal'
import { formatarData } from '../../utils/formatacao'
import { registrarAceitePrivacidade, type EstadoAvisoPrivacidade } from '../../routes/aceitePrivacidade'
import { paths, rotaInicialPorPerfil } from '../../routes/paths'
import { ApiError } from '../../services/api'
import { limparSessao, obterToken, obterUsuarioLogado } from '../../services/acesso/authService'
import { aceitarAvisoPrivacidade, buscarAvisoPrivacidade } from '../../services/privacidade/privacidadeService'
import type { AvisoPrivacidade as Aviso } from '../../types/privacidade/privacidade'
import styles from './AvisoPrivacidade.module.css'

const ICONES: readonly string[] = ['user', 'users', 'lock', 'shield', 'info', 'mail', 'eye', 'card']

function icone(nome: string): IconName {
  return (ICONES.includes(nome) ? nome : 'info') as IconName
}

/**
 * Aviso de privacidade (IU29): aparece depois do login, antes do painel, no
 * primeiro acesso e a cada versão nova. Aceitar grava a versão e a data;
 * Sair encerra a sessão sem aceitar.
 */
export function AvisoPrivacidade() {
  const navigate = useNavigate()
  const location = useLocation()
  const usuario = obterUsuarioLogado()
  const [aviso, setAviso] = useState<Aviso | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [leu, setLeu] = useState(false)
  const [lendo, setLendo] = useState(false)
  const [aceitando, setAceitando] = useState(false)
  const [versao, setVersao] = useState(0)

  const destino =
    (location.state as EstadoAvisoPrivacidade | null)?.voltarPara ??
    (usuario ? rotaInicialPorPerfil[usuario.perfil] : paths.login)

  useEffect(() => {
    if (!obterToken()) return
    const controller = new AbortController()
    buscarAvisoPrivacidade(controller.signal)
      .then((dados) => {
        // Já aceita (outra aba, ou voltou para cá pelo histórico): segue direto.
        if (dados.aceitoEm) {
          registrarAceitePrivacidade()
          navigate(destino, { replace: true })
          return
        }
        setAviso(dados)
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          setErro(e instanceof ApiError ? e.message : 'Não foi possível carregar o aviso de privacidade.')
        }
      })
    return () => controller.abort()
  }, [versao, destino, navigate])

  if (!usuario || !obterToken()) return <Navigate to={paths.login} replace />

  async function aceitar() {
    if (!aviso) return
    setAceitando(true)
    setErro(null)
    try {
      await aceitarAvisoPrivacidade(aviso.versao)
      registrarAceitePrivacidade()
      navigate(destino, { replace: true })
    } catch (e) {
      setAceitando(false)
      if (e instanceof ApiError && e.status === 400) {
        // O aviso mudou enquanto a tela estava aberta: mostra a versão nova para ler de novo.
        setLeu(false)
        setAviso(null)
        setVersao((v) => v + 1)
        setErro('O aviso de privacidade foi atualizado. Leia a nova versão para continuar.')
        return
      }
      setErro(e instanceof ApiError ? e.message : 'Não foi possível registrar o aceite. Tente de novo.')
    }
  }

  function sair() {
    const perfil = usuario?.perfil
    limparSessao()
    navigate(perfil === 'SUPER_ADMIN' ? paths.loginAdmin : paths.login, { replace: true })
  }

  return (
    <main className={styles.page}>
      <img src={logoEduka} alt="Edúka" className={styles.logo} />
      <section className={styles.card} aria-labelledby="titulo-aviso">
        <span className={styles.icone} aria-hidden="true">
          <Icon name="shield" size={28} />
        </span>
        <h1 id="titulo-aviso" className={styles.titulo}>
          Aviso de privacidade
        </h1>
        <p className={styles.texto}>
          Antes de continuar, veja como o Edúka e a sua escola cuidam dos seus dados pessoais.
        </p>

        {!aviso && !erro && <p role="status">Carregando o aviso…</p>}
        {erro && <ModalAlerta>{erro}</ModalAlerta>}

        {aviso && (
          <>
            <ul className={styles.resumo}>
              {aviso.resumo.map((item) => (
                <li key={item.titulo} className={styles.item}>
                  <span className={styles.itemIcone} aria-hidden="true">
                    <Icon name={icone(item.icone)} size={18} />
                  </span>
                  <span>
                    <strong>{item.titulo}</strong>
                    <span className={styles.itemTexto}>{item.texto}</span>
                  </span>
                </li>
              ))}
            </ul>

            <p className={styles.versao}>
              Versão {aviso.versao}, de {formatarData(aviso.publicadoEm)}
            </p>

            <button type="button" className={styles.lerCompleto} onClick={() => setLendo(true)}>
              <Icon name="fileText" size={16} />
              Ler o aviso completo
            </button>

            <label className={styles.confirmacao}>
              <input type="checkbox" checked={leu} onChange={(e) => setLeu(e.target.checked)} />
              <span>Li e entendi o aviso de privacidade.</span>
            </label>

            <div className={styles.acoes}>
              <Button onClick={sair} disabled={aceitando}>
                Sair
              </Button>
              <Button variant="primary" icon="check" onClick={() => void aceitar()} disabled={!leu || aceitando}>
                {aceitando ? 'Registrando…' : 'Aceitar e continuar'}
              </Button>
            </div>
          </>
        )}
        {!aviso && erro && (
          <Button size="sm" onClick={sair}>
            Sair
          </Button>
        )}
      </section>

      {lendo && aviso && (
        <Modal
          titulo="Aviso de privacidade"
          subtitulo={`Versão ${aviso.versao}, de ${formatarData(aviso.publicadoEm)}`}
          tamanho="grande"
          onFechar={() => setLendo(false)}
          rodape={
            <Button variant="primary" onClick={() => setLendo(false)}>
              Fechar
            </Button>
          }
        >
          <div className={styles.completo}>
            {aviso.secoes.map((secao) => (
              <section key={secao.titulo}>
                <h2>{secao.titulo}</h2>
                {secao.paragrafos.map((paragrafo) => (
                  <p key={paragrafo}>{paragrafo}</p>
                ))}
              </section>
            ))}
          </div>
        </Modal>
      )}
    </main>
  )
}
