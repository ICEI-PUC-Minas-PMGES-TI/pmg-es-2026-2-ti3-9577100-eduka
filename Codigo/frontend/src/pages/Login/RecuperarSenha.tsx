import { useEffect, useState, type FormEvent } from 'react'
import { Icon } from '../../components/ui/Icon/Icon'
import { ApiError } from '../../services/api'
import { redefinirSenha, solicitarRecuperacaoSenha } from '../../services/acesso/authService'
import styles from './Login.module.css'

// Mesmo mínimo do backend (UsuarioService.validarSenha).
const SENHA_TAMANHO_MINIMO = 8
// Mesmo tamanho do código gerado pelo backend (RecuperacaoSenhaService).
const TAMANHO_CODIGO = 6

type RecuperarSenhaProps = {
  /** Login já digitado na tela de entrar, para não pedir de novo. */
  loginInicial: string
  /** Senha trocada: volta ao login com o login preenchido. */
  onConcluido: (login: string) => void
  onVoltar: () => void
}

function formatarTempo(segundos: number): string {
  const minutos = Math.floor(segundos / 60)
  return `${String(minutos).padStart(2, '0')}:${String(segundos % 60).padStart(2, '0')}`
}

function mensagemDeErro(err: unknown): string {
  if (!(err instanceof ApiError)) return 'Ocorreu um erro inesperado. Tente novamente.'
  if (err.status === 0) {
    return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.'
  }
  if (err.status === 400 && err.codigo === 'CODIGO_RECUPERACAO_INVALIDO') {
    return 'Código inválido ou expirado. Confira o código recebido ou peça um novo.'
  }
  if (err.status === 400 && err.detalhe) return err.detalhe
  return 'Não foi possível concluir agora. Tente novamente em instantes.'
}

// "Esqueceu sua senha?" em duas etapas: o login, para o backend mandar um
// código ao e-mail do cadastro, e depois o código com a senha nova.
//
// O backend responde igual para qualquer login (para não revelar quais
// existem), então a tela também não diz se o e-mail saiu: só que, se a conta
// existir, o código chega.
export function RecuperarSenha({ loginInicial, onConcluido, onVoltar }: RecuperarSenhaProps) {
  const [etapa, setEtapa] = useState<'pedir' | 'redefinir'>('pedir')
  const [login, setLogin] = useState(loginInicial)
  const [codigo, setCodigo] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  // Horários do relógio local, como em VerificacaoCodigo: o cronômetro fica
  // certo mesmo com a aba em segundo plano.
  const [prazos, setPrazos] = useState({ expiraEm: 0, reenvioEm: 0 })
  const [agora, setAgora] = useState(() => Date.now())

  useEffect(() => {
    if (etapa !== 'redefinir') return
    const id = window.setInterval(() => setAgora(Date.now()), 500)
    return () => window.clearInterval(id)
  }, [etapa])

  const segundosRestantes = Math.max(0, Math.ceil((prazos.expiraEm - agora) / 1000))
  const segundosParaReenvio = Math.max(0, Math.ceil((prazos.reenvioEm - agora) / 1000))

  async function enviarCodigo(): Promise<boolean> {
    setCarregando(true)
    try {
      const resposta = await solicitarRecuperacaoSenha(login)
      const instante = Date.now()
      setAgora(instante)
      setPrazos({
        expiraEm: instante + resposta.validadeSegundos * 1000,
        reenvioEm: instante + resposta.reenvioSegundos * 1000,
      })
      return true
    } catch (err) {
      setErro(mensagemDeErro(err))
      return false
    } finally {
      setCarregando(false)
    }
  }

  async function pedirCodigo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErro(null)
    if (!login.trim()) {
      setErro('Informe seu e-mail ou matrícula.')
      return
    }
    if (await enviarCodigo()) {
      setAviso('Se houver uma conta com esse login, enviamos um código para o e-mail cadastrado.')
      setEtapa('redefinir')
    }
  }

  async function reenviar() {
    setErro(null)
    setAviso(null)
    if (await enviarCodigo()) {
      setCodigo('')
      setAviso('Pedimos um novo código. O anterior deixou de valer.')
    }
  }

  async function salvar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErro(null)
    setAviso(null)

    if (codigo.length !== TAMANHO_CODIGO) {
      setErro(`Digite os ${TAMANHO_CODIGO} números do código recebido por e-mail.`)
      return
    }
    if (novaSenha.length < SENHA_TAMANHO_MINIMO) {
      setErro(`A nova senha precisa ter ao menos ${SENHA_TAMANHO_MINIMO} caracteres.`)
      return
    }
    if (novaSenha !== confirmacao) {
      setErro('As senhas não conferem. Digite a mesma senha nos dois campos.')
      return
    }

    setCarregando(true)
    try {
      await redefinirSenha(login, codigo, novaSenha)
      onConcluido(login.trim())
    } catch (err) {
      setErro(mensagemDeErro(err))
    } finally {
      setCarregando(false)
    }
  }

  const mensagens = (
    <>
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
    </>
  )

  if (etapa === 'pedir') {
    return (
      <form className={styles.form} onSubmit={pedirCodigo} noValidate>
        <h2 className={styles.formTitle}>Recuperar senha</h2>
        <p className={styles.formSubtitle}>
          Informe seu login. Enviaremos um código para o e-mail do seu cadastro.
        </p>

        <div className={styles.campo}>
          <label htmlFor="recuperar-login" className={styles.label}>
            E-mail ou matrícula
          </label>
          <div className={styles.inputComIcone}>
            <Icon name="mail" size={18} className={styles.iconeCampo} />
            <input
              id="recuperar-login"
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

        <button type="submit" className={styles.botaoEntrar} disabled={carregando}>
          {carregando ? 'Enviando…' : 'Enviar código'}
        </button>

        <button type="button" className={styles.linkSenha} onClick={onVoltar} disabled={carregando}>
          Voltar para o login
        </button>

        {mensagens}
      </form>
    )
  }

  const tipoSenha = mostrarSenha ? 'text' : 'password'

  return (
    <form className={styles.form} onSubmit={salvar} noValidate>
      <h2 className={styles.formTitle}>Nova senha</h2>
      <p className={styles.formSubtitle}>
        Digite o código enviado por e-mail e escolha uma senha nova.{' '}
        {segundosRestantes > 0
          ? `O código vale por mais ${formatarTempo(segundosRestantes)}.`
          : 'O código expirou: peça um novo.'}
      </p>

      <div className={styles.campo}>
        <label htmlFor="recuperar-codigo" className={styles.label}>
          Código de verificação
        </label>
        <div className={styles.inputComIcone}>
          <Icon name="shield" size={18} className={styles.iconeCampo} />
          <input
            id="recuperar-codigo"
            type="text"
            inputMode="numeric"
            className={styles.input}
            placeholder="000000"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value.replace(/\D/g, '').slice(0, TAMANHO_CODIGO))}
            autoComplete="one-time-code"
            maxLength={TAMANHO_CODIGO}
            disabled={carregando}
            autoFocus
          />
        </div>
      </div>

      <div className={styles.campo}>
        <label htmlFor="recuperar-nova-senha" className={styles.label}>
          Nova senha
        </label>
        <div className={styles.linhaSenha}>
          <div className={styles.inputComIcone}>
            <Icon name="lock" size={18} className={styles.iconeCampo} />
            <input
              id="recuperar-nova-senha"
              type={tipoSenha}
              className={styles.input}
              placeholder={`Ao menos ${SENHA_TAMANHO_MINIMO} caracteres`}
              value={novaSenha}
              onChange={(e) => setNovaSenha(e.target.value)}
              autoComplete="new-password"
              disabled={carregando}
            />
          </div>
          <button
            type="button"
            className={styles.botaoMostrar}
            onClick={() => setMostrarSenha((atual) => !atual)}
            aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
          >
            <Icon name={mostrarSenha ? 'eyeOff' : 'eye'} size={18} />
            {mostrarSenha ? 'Ocultar' : 'Mostrar'}
          </button>
        </div>
      </div>

      <div className={styles.campo}>
        <label htmlFor="recuperar-confirmacao" className={styles.label}>
          Confirme a nova senha
        </label>
        <div className={styles.inputComIcone}>
          <Icon name="lock" size={18} className={styles.iconeCampo} />
          <input
            id="recuperar-confirmacao"
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
        {carregando ? 'Salvando…' : 'Salvar nova senha'}
      </button>

      <button
        type="button"
        className={styles.linkSenha}
        onClick={reenviar}
        disabled={carregando || segundosParaReenvio > 0}
      >
        {segundosParaReenvio > 0
          ? `Reenviar código em ${formatarTempo(segundosParaReenvio)}`
          : 'Reenviar código'}
      </button>

      <button type="button" className={styles.linkSenha} onClick={onVoltar} disabled={carregando}>
        Voltar para o login
      </button>

      {mensagens}
    </form>
  )
}
