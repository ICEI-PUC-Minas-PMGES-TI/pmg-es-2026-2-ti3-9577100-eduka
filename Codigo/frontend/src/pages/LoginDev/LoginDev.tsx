import { useState } from 'react'
import { useNavigate } from 'react-router'
import iconeEduka from '../../assets/eduka-icone.svg'
import { Icon, type IconName } from '../../components/Icon/Icon'
import { rotaInicialPorPerfil } from '../../routes/paths'
import { ApiError } from '../../services/api'
import { autenticar, type PerfilUsuario } from '../../services/authService'
import styles from './LoginDev.module.css'

// Só existe em dev: a rota /login/dev nem entra no build de produção (ver
// router.tsx). As contas são criadas pelo DevDataSeeder do backend, no perfil
// "dev", então o token é real e o backend continua aplicando os 403.
const SENHA_DEV = 'Eduka-dev123'

const contas: { perfil: PerfilUsuario; rotulo: string; login: string; senha: string; icon: IconName }[] = [
  { perfil: 'SUPER_ADMIN', rotulo: 'Super Admin', login: 'admin@eduka.com.br', senha: 'Eduka-admin123', icon: 'shield' },
  { perfil: 'MASTER', rotulo: 'Master', login: 'master@dev.eduka.com.br', senha: SENHA_DEV, icon: 'settings' },
  { perfil: 'SECRETARIA', rotulo: 'Secretaria', login: 'secretaria@dev.eduka.com.br', senha: SENHA_DEV, icon: 'book' },
  { perfil: 'FINANCEIRO', rotulo: 'Financeiro', login: 'financeiro@dev.eduka.com.br', senha: SENHA_DEV, icon: 'card' },
  { perfil: 'PROFESSOR', rotulo: 'Professor', login: 'professor@dev.eduka.com.br', senha: SENHA_DEV, icon: 'graduationCap' },
  { perfil: 'ALUNO', rotulo: 'Aluno', login: 'aluno@dev.eduka.com.br', senha: SENHA_DEV, icon: 'users' },
]

export function LoginDev() {
  const navigate = useNavigate()
  const [entrando, setEntrando] = useState<PerfilUsuario | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  async function entrarComo(conta: (typeof contas)[number]) {
    setEntrando(conta.perfil)
    setErro(null)
    try {
      const { perfil } = await autenticar(conta.login, conta.senha)
      navigate(rotaInicialPorPerfil[perfil], { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setErro(`A conta de ${conta.rotulo} não existe. Confira se o backend subiu com o perfil "dev".`)
      } else {
        setErro(e instanceof ApiError ? e.message : 'Erro inesperado ao entrar.')
      }
      setEntrando(null)
    }
  }

  return (
    <div className={styles.page}>
      <main className={styles.card}>
        <img src={iconeEduka} alt="" className={styles.logo} width={48} height={48} />
        <span className={styles.badge}>Ambiente de desenvolvimento</span>
        <h1 className={styles.title}>Entrar como…</h1>
        <p className={styles.text}>Um clique entra com a conta de teste do perfil e abre a área dele.</p>

        <ul className={styles.lista}>
          {contas.map((conta) => (
            <li key={conta.perfil}>
              <button
                type="button"
                className={styles.conta}
                disabled={entrando !== null}
                onClick={() => entrarComo(conta)}
              >
                <span className={styles.icone} aria-hidden="true">
                  <Icon name={conta.icon} size={18} />
                </span>
                <span className={styles.textoConta}>
                  <strong>{entrando === conta.perfil ? 'Entrando…' : conta.rotulo}</strong>
                  <span className={styles.login}>{conta.login}</span>
                </span>
                <Icon name="arrowRight" size={16} />
              </button>
            </li>
          ))}
        </ul>

        {erro && (
          <p className={styles.erro} role="alert">
            {erro}
          </p>
        )}
      </main>
    </div>
  )
}
