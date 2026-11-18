import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import logoEduka from '../../../../assets/eduka-icone.svg'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { VerificacaoCodigo } from '../../../../components/acesso/VerificacaoCodigo/VerificacaoCodigo'
import { rotaInicialPorPerfil } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import {
  autenticar,
  estaAutenticado,
  obterUsuarioLogado,
  type VerificacaoLogin,
} from '../../../../services/acesso/authService'
import styles from './LoginAdmin.module.css'

export function LoginAdmin() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  // Senha certa, falta o código enviado por e-mail (VerificacaoCodigo).
  const [verificacao, setVerificacao] = useState<VerificacaoLogin | null>(null)

  useEffect(() => {
    const usuario = obterUsuarioLogado()
    const destino = usuario && rotaInicialPorPerfil[usuario.perfil]
    if (estaAutenticado() && destino) {
      navigate(destino, { replace: true })
    }
  }, [navigate])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErro(null)
    setAviso(null)

    if (!email.trim() || !senha) {
      setErro('Informe o e-mail administrativo e a senha.')
      return
    }

    setCarregando(true)
    try {
      const resposta = await autenticar(email, senha)
      if (resposta.verificacao) {
        setVerificacao(resposta.verificacao)
        setMostrarSenha(false)
        return
      }
      navigate(rotaInicialPorPerfil[resposta.perfil])
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setErro('Credenciais inválidas. Verifique o e-mail e a senha informados.')
        } else if (err.status === 0) {
          setErro('Não foi possível conectar ao servidor. Verifique sua conexão ou se a API está online.')
        } else {
          setErro(err.message || 'Falha ao autenticar. Tente novamente.')
        }
      } else {
        setErro('Ocorreu um erro inesperado ao tentar entrar.')
      }
    } finally {
      setCarregando(false)
    }
  }

  function voltarParaEntrar() {
    setVerificacao(null)
    setSenha('')
    setErro(null)
  }

  function handleEsqueciSenha() {
    setAviso('Para recuperar a senha, consulte o operador de infraestrutura.')
    setErro(null)
  }

  return (
    <div className={styles.viewport}>
      <div className={styles.container}>
        {/* Painel Esquerdo com Identidade Visual */}
        <section className={styles.heroCard}>
          <header className={styles.brand}>
            <img src={logoEduka} alt="" className={styles.brandLogo} width={54} height={54} />
            <span className={styles.brandName}>eduka</span>
            <span className={styles.superBadge}>Super Console</span>
          </header>

          <div className={styles.heroContent}>
            <h1 className={styles.heroTitle}>
              Administração
              <br />
              da plataforma.
            </h1>
            <p className={styles.heroText}>
              Área restrita para cadastrar, acompanhar e suspender as escolas que usam o Eduka.
            </p>
          </div>

          <footer className={styles.heroSecurity}>
            <Icon name="shield" size={18} />
            <span>Acesso monitorado e protegido por criptografia.</span>
          </footer>
        </section>

        {/* Painel Direito com Formulário */}
        <main className={styles.formCard}>
          {verificacao ? (
            <VerificacaoCodigo
              verificacao={verificacao}
              login={email}
              onAutenticado={({ perfil }) => navigate(rotaInicialPorPerfil[perfil])}
              onVoltar={voltarParaEntrar}
            />
          ) : (
            <>
              <span className={styles.adminTag}>Painel administrativo global</span>

              <h2 className={styles.formTitle}>
                Console do
                <br />
                Administrador
              </h2>

              <p className={styles.formSubtitle}>
                Acesso restrito aos administradores da plataforma Eduka.
              </p>

              <form className={styles.form} onSubmit={handleSubmit} noValidate>
                <div className={styles.fieldGroup}>
                  <label htmlFor="admin-email" className={styles.label}>
                    E-mail administrativo
                  </label>
                  <input
                    id="admin-email"
                    type="email"
                    className={styles.input}
                    placeholder="admin@eduka.com.br"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoComplete="email"
                    disabled={carregando}
                    required
                  />
                </div>

                <div className={styles.fieldGroup}>
                  <label htmlFor="master-password" className={styles.label}>
                    Senha
                  </label>
                  <div className={styles.passwordRow}>
                    <input
                      id="master-password"
                      type={mostrarSenha ? 'text' : 'password'}
                      className={styles.input}
                      placeholder="Digite a senha"
                      value={senha}
                      onChange={(e) => setSenha(e.target.value)}
                      autoComplete="current-password"
                      disabled={carregando}
                      required
                    />
                    <button
                      type="button"
                      className={styles.togglePasswordBtn}
                      onClick={() => setMostrarSenha((prev) => !prev)}
                      aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                    >
                      {mostrarSenha ? 'Ocultar' : 'Mostrar'}
                    </button>
                  </div>
                </div>

                <button type="submit" className={styles.submitBtn} disabled={carregando}>
                  {carregando ? 'Entrando...' : 'Continuar'}
                </button>

                {erro && (
                  <div className={styles.errorMessage} role="alert">
                    {erro}
                  </div>
                )}

                {aviso && (
                  <div className={styles.infoBanner} role="status">
                    {aviso}
                  </div>
                )}

                <div className={styles.footerLinks}>
                  <button
                    type="button"
                    className={styles.forgotLink}
                    onClick={handleEsqueciSenha}
                  >
                    Esqueci a senha
                  </button>
                </div>
              </form>
            </>
          )}
        </main>
      </div>
    </div>
  )
}
