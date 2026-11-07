import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import logoEduka from '../../assets/eduka-logo-branca.svg'
import { Icon } from '../../components/Icon/Icon'
import { VerificacaoCodigo } from '../../components/VerificacaoCodigo/VerificacaoCodigo'
import { rotaInicialPorPerfil } from '../../routes/paths'
import { ApiError, ehEscolaSuspensa, motivoDaSuspensao, rotaAcessoSuspenso } from '../../services/api'
import {
  autenticar,
  definirSenhaDefinitiva,
  estaAutenticado,
  obterUsuarioLogado,
  type LoginResponse,
  type VerificacaoLogin,
} from '../../services/authService'
import styles from './Login.module.css'

// Mesmo mínimo do backend (UsuarioService.validarSenha).
const SENHA_TAMANHO_MINIMO = 8

// Login da escola: master, secretaria, financeiro, professor e aluno entram
// por aqui e caem na área do próprio perfil. O console da plataforma tem a
// tela dele, em /login/admin.
//
// Quem entra com a senha provisória (recebida por e-mail no cadastro) passa
// antes pela etapa "novaSenha": o backend só emite o token depois da troca.
//
// Com a senha certa, a última etapa é "codigo": o backend manda um código por
// e-mail e só emite o token quando ele é confirmado (VerificacaoCodigo).
export function Login() {
  const navigate = useNavigate()
  const [etapa, setEtapa] = useState<'entrar' | 'novaSenha' | 'codigo'>('entrar')
  const [verificacao, setVerificacao] = useState<VerificacaoLogin | null>(null)
  const [login, setLogin] = useState('')
  const [senha, setSenha] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  useEffect(() => {
    const usuario = obterUsuarioLogado()
    const destino = usuario && rotaInicialPorPerfil[usuario.perfil]
    if (estaAutenticado() && destino) {
      navigate(destino, { replace: true })
    }
  }, [navigate])

  function mensagemDeErro(err: unknown): string {
    if (!(err instanceof ApiError)) return 'Ocorreu um erro inesperado ao tentar entrar.'
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.'
    }
    if (err.status === 401) {
      // O backend usa 401 também para a senha provisória vencida, e só o
      // detalhe diferencia os dois casos.
      return err.detalhe?.toLowerCase().includes('expirada')
        ? 'Sua senha provisória expirou. Peça um novo acesso à secretaria da escola.'
        : 'E-mail ou senha incorretos. Confira os dados e tente novamente.'
    }
    if (err.status === 400 && err.detalhe) return err.detalhe
    return 'Não foi possível entrar agora. Tente novamente em instantes.'
  }

  // Depois da senha (ou da troca da provisória): ou a sessão já abriu, ou
  // falta o código enviado por e-mail.
  function concluir(resposta: LoginResponse) {
    if (resposta.verificacao) {
      setVerificacao(resposta.verificacao)
      setMostrarSenha(false)
      setErro(null)
      setEtapa('codigo')
      return
    }
    navigate(rotaInicialPorPerfil[resposta.perfil], { replace: true })
  }

  async function entrar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErro(null)
    setAviso(null)

    if (!login.trim() || !senha) {
      setErro('Informe seu e-mail ou matrícula e a senha de acesso.')
      return
    }

    setCarregando(true)
    try {
      const resposta = await autenticar(login, senha)
      if (resposta.trocaSenhaObrigatoria) {
        setMostrarSenha(false)
        setEtapa('novaSenha')
        return
      }
      concluir(resposta)
    } catch (err) {
      if (ehEscolaSuspensa(err)) {
        navigate(rotaAcessoSuspenso(motivoDaSuspensao(err)), { replace: true })
        return
      }
      setErro(mensagemDeErro(err))
    } finally {
      setCarregando(false)
    }
  }

  async function salvarNovaSenha(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErro(null)

    if (novaSenha.length < SENHA_TAMANHO_MINIMO) {
      setErro(`A nova senha precisa ter ao menos ${SENHA_TAMANHO_MINIMO} caracteres.`)
      return
    }
    if (novaSenha !== confirmacao) {
      setErro('As senhas não conferem. Digite a mesma senha nos dois campos.')
      return
    }
    if (novaSenha === senha) {
      setErro('A nova senha precisa ser diferente da senha provisória.')
      return
    }

    setCarregando(true)
    try {
      concluir(await definirSenhaDefinitiva(login, senha, novaSenha))
    } catch (err) {
      if (ehEscolaSuspensa(err)) {
        navigate(rotaAcessoSuspenso(motivoDaSuspensao(err)), { replace: true })
        return
      }
      setErro(mensagemDeErro(err))
    } finally {
      setCarregando(false)
    }
  }

  function voltarParaEntrar() {
    setEtapa('entrar')
    setVerificacao(null)
    setSenha('')
    setNovaSenha('')
    setConfirmacao('')
    setMostrarSenha(false)
    setErro(null)
  }

  function handleEsqueciSenha() {
    setErro(null)
    setAviso('Para redefinir sua senha, procure a secretaria da sua escola.')
  }

  const tipoSenha = mostrarSenha ? 'text' : 'password'
  const botaoMostrar = (
    <button
      type="button"
      className={styles.botaoMostrar}
      onClick={() => setMostrarSenha((atual) => !atual)}
      aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
    >
      <Icon name={mostrarSenha ? 'eyeOff' : 'eye'} size={18} />
      {mostrarSenha ? 'Ocultar' : 'Mostrar'}
    </button>
  )

  return (
    <div className={styles.viewport}>
      <div className={styles.container}>
        <section className={styles.heroCard}>
          <img src={logoEduka} alt="eduka" className={styles.logo} width={175} height={46} />

          <div className={styles.heroContent}>
            <h1 className={styles.heroTitle}>
              Portal de Acesso
              <br />
              Acadêmico
            </h1>
            <p className={styles.heroText}>
              Matrículas, aulas e pagamentos da sua escola, de um jeito simples.
            </p>
          </div>

          <footer className={styles.heroSecurity}>
            <Icon name="shield" size={18} />
            <span>EDUKA Platform · Acesso seguro</span>
          </footer>
        </section>

        <main className={styles.formCard}>
          {etapa === 'codigo' && verificacao ? (
            <VerificacaoCodigo
              verificacao={verificacao}
              login={login}
              onAutenticado={concluir}
              onVoltar={voltarParaEntrar}
            />
          ) : etapa === 'entrar' ? (
            <form className={styles.form} onSubmit={entrar} noValidate>
              <h2 className={styles.formTitle}>Entrar</h2>

              <div className={styles.campo}>
                <label htmlFor="login-usuario" className={styles.label}>
                  E-mail ou matrícula
                </label>
                <div className={styles.inputComIcone}>
                  <Icon name="mail" size={18} className={styles.iconeCampo} />
                  <input
                    id="login-usuario"
                    type="text"
                    inputMode="email"
                    className={styles.input}
                    placeholder="usuario@escola.com.br ou matrícula"
                    value={login}
                    onChange={(e) => setLogin(e.target.value)}
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    disabled={carregando}
                    autoFocus
                  />
                </div>
              </div>

              <div className={styles.campo}>
                <label htmlFor="login-senha" className={styles.label}>
                  Senha de acesso
                </label>
                <div className={styles.linhaSenha}>
                  <div className={styles.inputComIcone}>
                    <Icon name="lock" size={18} className={styles.iconeCampo} />
                    <input
                      id="login-senha"
                      type={tipoSenha}
                      className={styles.input}
                      placeholder="Digite sua senha"
                      value={senha}
                      onChange={(e) => setSenha(e.target.value)}
                      autoComplete="current-password"
                      disabled={carregando}
                    />
                  </div>
                  {botaoMostrar}
                </div>
              </div>

              <button type="submit" className={styles.botaoEntrar} disabled={carregando}>
                {carregando ? 'Entrando…' : 'Acessar instituição'}
              </button>

              <button type="button" className={styles.linkSenha} onClick={handleEsqueciSenha}>
                Esqueceu sua senha?
              </button>

              {erro && (
                <div className={styles.erro} role="alert">
                  <Icon name="alert" size={18} />
                  <span>{erro}</span>
                </div>
              )}

              {aviso && (
                <div className={styles.aviso} role="status">
                  {aviso}
                </div>
              )}
            </form>
          ) : (
            <form className={styles.form} onSubmit={salvarNovaSenha} noValidate>
              <h2 className={styles.formTitle}>Crie sua senha</h2>
              <p className={styles.formSubtitle}>
                Este é o seu primeiro acesso. Troque a senha provisória por uma só sua para
                continuar.
              </p>

              <div className={styles.campo}>
                <label htmlFor="login-nova-senha" className={styles.label}>
                  Nova senha
                </label>
                <div className={styles.linhaSenha}>
                  <div className={styles.inputComIcone}>
                    <Icon name="lock" size={18} className={styles.iconeCampo} />
                    <input
                      id="login-nova-senha"
                      type={tipoSenha}
                      className={styles.input}
                      placeholder={`Ao menos ${SENHA_TAMANHO_MINIMO} caracteres`}
                      value={novaSenha}
                      onChange={(e) => setNovaSenha(e.target.value)}
                      autoComplete="new-password"
                      disabled={carregando}
                      autoFocus
                    />
                  </div>
                  {botaoMostrar}
                </div>
              </div>

              <div className={styles.campo}>
                <label htmlFor="login-confirmacao" className={styles.label}>
                  Confirme a nova senha
                </label>
                <div className={styles.inputComIcone}>
                  <Icon name="lock" size={18} className={styles.iconeCampo} />
                  <input
                    id="login-confirmacao"
                    type={tipoSenha}
                    className={styles.input}
                    placeholder="Repita a nova senha"
                    value={confirmacao}
                    onChange={(e) => setConfirmacao(e.target.value)}
                    autoComplete="new-password"
                    disabled={carregando}
                  />
                </div>
              </div>

              <button type="submit" className={styles.botaoEntrar} disabled={carregando}>
                {carregando ? 'Salvando…' : 'Salvar e entrar'}
              </button>

              <button
                type="button"
                className={styles.linkSenha}
                onClick={voltarParaEntrar}
                disabled={carregando}
              >
                Voltar para o login
              </button>

              {erro && (
                <div className={styles.erro} role="alert">
                  <Icon name="alert" size={18} />
                  <span>{erro}</span>
                </div>
              )}
            </form>
          )}
        </main>
      </div>
    </div>
  )
}
