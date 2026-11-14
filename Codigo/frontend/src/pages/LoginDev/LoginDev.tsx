import { useState } from 'react'
import { useNavigate } from 'react-router'
import iconeEduka from '../../assets/eduka-icone.svg'
import { Icon, type IconName } from '../../components/ui/Icon/Icon'
import { rotaInicialPorPerfil } from '../../routes/paths'
import { ApiError, ehEscolaSuspensa, motivoDaSuspensao, rotaAcessoSuspenso } from '../../services/api'
import { autenticar, type PerfilUsuario } from '../../services/acesso/authService'
import styles from './LoginDev.module.css'

// Só existe em dev: a rota /login/dev nem entra no build de produção (ver
// router.tsx). As contas são criadas pelos seeders do backend no perfil "dev",
// então o token é real e o backend continua aplicando os 403.
const SENHA_DEV = 'Eduka-dev123'
// Escola de demonstração do MockDataSeeder, que só existe com MOCK_DATA=true.
const SENHA_MOCK = 'Eduka-mock123'

type Conta = { perfil: PerfilUsuario; rotulo: string; login: string; senha: string; icon: IconName }

const grupos: { titulo: string; descricao: string; seFaltar: string; contas: Conta[] }[] = [
  {
    titulo: 'Colégio Ipê Amarelo',
    descricao: 'Escola de demonstração: 50 alunos, turmas, notas e financeiro.',
    seFaltar: 'Confira se MOCK_DATA=true no .env e recrie o backend.',
    contas: [
      { perfil: 'MASTER', rotulo: 'Master · Helena Duarte', login: 'helena.duarte@mock.eduka.com.br', senha: SENHA_MOCK, icon: 'settings' },
      { perfil: 'SECRETARIA', rotulo: 'Secretaria · Patrícia Andrade', login: 'patricia.andrade@mock.eduka.com.br', senha: SENHA_MOCK, icon: 'book' },
      { perfil: 'FINANCEIRO', rotulo: 'Financeiro · Marcos Teixeira', login: 'marcos.teixeira@mock.eduka.com.br', senha: SENHA_MOCK, icon: 'card' },
      { perfil: 'PROFESSOR', rotulo: 'Professora · Ana Paula Ribeiro', login: 'ana.ribeiro@mock.eduka.com.br', senha: SENHA_MOCK, icon: 'graduationCap' },
      { perfil: 'ALUNO', rotulo: 'Aluna · Valentina Martins (6º ano)', login: 'valentina.martins@mock.eduka.com.br', senha: SENHA_MOCK, icon: 'users' },
    ],
  },
  {
    titulo: 'Escola Dev',
    descricao: 'Super admin e uma escola quase vazia, para testar do zero.',
    seFaltar: 'Confira se o backend subiu com o perfil "dev".',
    contas: [
      { perfil: 'SUPER_ADMIN', rotulo: 'Super Admin', login: 'admin@eduka.com.br', senha: 'Eduka-admin123', icon: 'shield' },
      { perfil: 'MASTER', rotulo: 'Master', login: 'master@dev.eduka.com.br', senha: SENHA_DEV, icon: 'settings' },
      { perfil: 'SECRETARIA', rotulo: 'Secretaria', login: 'secretaria@dev.eduka.com.br', senha: SENHA_DEV, icon: 'book' },
      { perfil: 'FINANCEIRO', rotulo: 'Financeiro', login: 'financeiro@dev.eduka.com.br', senha: SENHA_DEV, icon: 'card' },
      { perfil: 'PROFESSOR', rotulo: 'Professor', login: 'professor@dev.eduka.com.br', senha: SENHA_DEV, icon: 'graduationCap' },
      { perfil: 'ALUNO', rotulo: 'Aluno', login: 'aluno@dev.eduka.com.br', senha: SENHA_DEV, icon: 'users' },
    ],
  },
]

export function LoginDev() {
  const navigate = useNavigate()
  const [entrando, setEntrando] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  async function entrarComo(conta: Conta, seFaltar: string) {
    setEntrando(conta.login)
    setErro(null)
    try {
      const { perfil, verificacao } = await autenticar(conta.login, conta.senha)
      if (verificacao) {
        // O backend pediu o código por e-mail, e estas contas não têm caixa de
        // entrada. Um clique só funciona com a verificação desligada.
        setErro(
          'A verificação em duas etapas está ligada. Para entrar em um clique, defina ' +
            'LOGIN_VERIFICACAO_HABILITADA=false no .env e recrie o backend. Para testar o ' +
            'código, entre por /login e leia o código no log do backend.',
        )
        setEntrando(null)
        return
      }
      navigate(rotaInicialPorPerfil[perfil], { replace: true })
    } catch (e) {
      // Escola suspensa: o mesmo aviso (IU04) do login normal.
      if (ehEscolaSuspensa(e)) {
        navigate(rotaAcessoSuspenso(motivoDaSuspensao(e)), { replace: true })
        return
      }
      if (e instanceof ApiError && e.status === 401) {
        setErro(`A conta de ${conta.rotulo} não existe. ${seFaltar}`)
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
                    onClick={() => entrarComo(conta, grupo.seFaltar)}
                  >
                    <span className={styles.icone} aria-hidden="true">
                      <Icon name={conta.icon} size={18} />
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

        {erro && (
          <p className={styles.erro} role="alert">
            {erro}
          </p>
        )}
      </main>
    </div>
  )
}
