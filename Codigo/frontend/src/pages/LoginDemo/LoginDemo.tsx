import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import iconeEduka from '../../assets/eduka-icone.svg'
import { Button } from '../../components/Button/Button'
import { Campo } from '../../components/Campo/Campo'
import { Icon, type IconName } from '../../components/Icon/Icon'
import { rotaInicialPorPerfil } from '../../routes/paths'
import { ApiError, ehEscolaSuspensa, motivoDaSuspensao, rotaAcessoSuspenso } from '../../services/api'
import type { PerfilUsuario } from '../../services/authService'
import { entrarComoDemo, listarContasDemo, type ContaDemo, type GrupoDemo } from '../../services/demoService'
import styles from '../LoginDev/LoginDev.module.css'
import demoStyles from './LoginDemo.module.css'

const ICONE_POR_PERFIL: Record<PerfilUsuario, IconName> = {
  SUPER_ADMIN: 'shield',
  MASTER: 'settings',
  SECRETARIA: 'book',
  FINANCEIRO: 'card',
  PROFESSOR: 'graduationCap',
  ALUNO: 'users',
}

// Versão de apresentação do /login/dev: entra no build com VITE_DEMO=true (ver
// router.tsx). Primeiro pede o login e a senha do acesso de demonstração; o
// backend confere e devolve as contas de teste. Nenhuma senha de conta passa
// pelo front: o token da conta escolhida sai de POST /demo/entrar.
export function LoginDemo() {
  const navigate = useNavigate()
  const [login, setLogin] = useState('')
  const [senha, setSenha] = useState('')
  const [grupos, setGrupos] = useState<GrupoDemo[] | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [entrando, setEntrando] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  async function liberar(evento: FormEvent) {
    evento.preventDefault()
    setCarregando(true)
    setErro(null)
    try {
      setGrupos(await listarContasDemo(login, senha))
    } catch (e) {
      setErro(mensagemDeErro(e, 'Login ou senha do acesso de demonstração inválidos.'))
    } finally {
      setCarregando(false)
    }
  }

  async function entrarComo(conta: ContaDemo) {
    setEntrando(conta.login)
    setErro(null)
    try {
      const { perfil } = await entrarComoDemo(login, senha, conta.login)
      navigate(rotaInicialPorPerfil[perfil], { replace: true })
    } catch (e) {
      // Escola suspensa: o mesmo aviso (IU04) do login normal.
      if (ehEscolaSuspensa(e)) {
        navigate(rotaAcessoSuspenso(motivoDaSuspensao(e)), { replace: true })
        return
      }
      setErro(mensagemDeErro(e, `Não foi possível entrar como ${conta.rotulo}.`))
      setEntrando(null)
    }
  }

  return (
    <div className={styles.page}>
      <main className={styles.card}>
        <img src={iconeEduka} alt="" className={styles.logo} width={48} height={48} />
        <span className={styles.badge}>Ambiente de demonstração</span>

        {grupos === null ? (
          <>
            <h1 className={styles.title}>Acesso de teste</h1>
            <p className={styles.text}>Entre com o acesso de demonstração para ver as contas de cada perfil.</p>
            <form className={demoStyles.form} onSubmit={liberar} noValidate>
              <Campo label="E-mail" obrigatorio>
                {(props) => (
                  <input
                    {...props}
                    type="email"
                    autoComplete="username"
                    value={login}
                    onChange={(e) => setLogin(e.target.value)}
                  />
                )}
              </Campo>
              <Campo label="Senha" obrigatorio>
                {(props) => (
                  <input
                    {...props}
                    type="password"
                    autoComplete="current-password"
                    value={senha}
                    onChange={(e) => setSenha(e.target.value)}
                  />
                )}
              </Campo>
              <Button
                type="submit"
                variant="primary"
                icon="lock"
                disabled={carregando || !login.trim() || !senha}
              >
                {carregando ? 'Conferindo…' : 'Ver contas de teste'}
              </Button>
            </form>
          </>
        ) : (
          <>
            <h1 className={styles.title}>Entrar como…</h1>
            <p className={styles.text}>Um clique entra com a conta de teste do perfil e abre a área dele.</p>
            {grupos.length === 0 && (
              <p className={styles.text}>Nenhuma conta de teste no banco. Confira se o backend subiu com o perfil demo.</p>
            )}
            {grupos.map((grupo) => (
              <section key={grupo.titulo} className={styles.grupo} aria-label={grupo.titulo}>
                <h2 className={styles.tituloGrupo}>{grupo.titulo}</h2>
                <p className={styles.descricaoGrupo}>{grupo.descricao}</p>
                <ul className={styles.lista}>
                  {grupo.contas.map((conta) => (
                    <li key={conta.login}>
                      <button
                        type="button"
                        className={styles.conta}
                        disabled={entrando !== null}
                        onClick={() => entrarComo(conta)}
                      >
                        <span className={styles.icone} aria-hidden="true">
                          <Icon name={ICONE_POR_PERFIL[conta.perfil]} size={18} />
                        </span>
                        <span className={styles.textoConta}>
                          <strong>{entrando === conta.login ? 'Entrando…' : conta.rotulo}</strong>
                          <span className={styles.login}>{conta.login}</span>
                        </span>
                        <Icon name="arrowRight" size={16} />
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </>
        )}

        {erro && (
          <p className={styles.erro} role="alert">
            {erro}
          </p>
        )}
      </main>
    </div>
  )
}

function mensagemDeErro(e: unknown, seNaoAutorizado: string): string {
  if (e instanceof ApiError && e.status === 401) return seNaoAutorizado
  if (e instanceof ApiError && e.status === 404) {
    return 'O acesso de demonstração está desligado neste ambiente (backend sem o perfil demo).'
  }
  return e instanceof ApiError ? e.message : 'Erro inesperado. Tente de novo.'
}
