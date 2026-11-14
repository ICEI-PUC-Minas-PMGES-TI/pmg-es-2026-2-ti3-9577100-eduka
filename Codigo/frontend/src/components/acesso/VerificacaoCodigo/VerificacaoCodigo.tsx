import {
  useEffect,
  useRef,
  useState,
  type ClipboardEvent,
  type FormEvent,
  type KeyboardEvent,
} from 'react'
import { useNavigate } from 'react-router'
import { ApiError, ehEscolaSuspensa, motivoDaSuspensao, rotaAcessoSuspenso } from '../../../services/api'
import {
  confirmarCodigo,
  reenviarCodigo,
  type LoginResponse,
  type VerificacaoLogin,
} from '../../../services/acesso/authService'
import { Button } from '../../ui/Button/Button'
import { Icon } from '../../ui/Icon/Icon'
import styles from './VerificacaoCodigo.module.css'

// Mesmo tamanho do código gerado pelo backend (VerificacaoLoginService).
const TAMANHO_CODIGO = 6
const CODIGO_VAZIO: string[] = Array.from({ length: TAMANHO_CODIGO }, () => '')

type VerificacaoCodigoProps = {
  /** Verificação aberta pelo login (ou pelo primeiro acesso). */
  verificacao: VerificacaoLogin
  /** Login digitado na etapa anterior; fica guardado na sessão. */
  login: string
  onAutenticado: (resposta: LoginResponse) => void
  onVoltar: () => void
}

// O backend manda os prazos em segundos a partir da resposta; aqui viram
// horários do relógio local. Assim o cronômetro continua certo mesmo se a aba
// ficar em segundo plano e o navegador atrasar os ticks.
function prazosDe(verificacao: VerificacaoLogin) {
  const agora = Date.now()
  return {
    agora,
    expiraEm: agora + verificacao.validadeSegundos * 1000,
    reenvioEm: agora + verificacao.reenvioSegundos * 1000,
  }
}

function formatarTempo(segundos: number): string {
  const minutos = Math.floor(segundos / 60)
  return `${String(minutos).padStart(2, '0')}:${String(segundos % 60).padStart(2, '0')}`
}

// Segunda etapa do login: o código de 6 números enviado por e-mail. Só depois
// dele o backend emite o token. Usada pelo login da escola e pelo do console.
export function VerificacaoCodigo({
  verificacao,
  login,
  onAutenticado,
  onVoltar,
}: VerificacaoCodigoProps) {
  const navigate = useNavigate()
  const [digitos, setDigitos] = useState(CODIGO_VAZIO)
  const [prazos, setPrazos] = useState(() => prazosDe(verificacao))
  const [agora, setAgora] = useState(() => Date.now())
  const [carregando, setCarregando] = useState(false)
  const [reenviando, setReenviando] = useState(false)
  // Tentativas ou envios esgotados: só resta voltar e entrar de novo.
  const [encerrada, setEncerrada] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const campos = useRef<(HTMLInputElement | null)[]>([])
  // O último dígito dispara o envio sozinho; a trava evita mandar duas vezes
  // se o usuário também apertar Enter.
  const enviando = useRef(false)

  useEffect(() => {
    const id = window.setInterval(() => setAgora(Date.now()), 500)
    return () => window.clearInterval(id)
  }, [])

  const segundosRestantes = Math.max(0, Math.ceil((prazos.expiraEm - agora) / 1000))
  const segundosParaReenvio = Math.max(0, Math.ceil((prazos.reenvioEm - agora) / 1000))
  const expirado = segundosRestantes === 0
  const bloqueado = carregando || reenviando || encerrada
  const codigo = digitos.join('')

  function focar(indice: number) {
    const campo = campos.current[Math.max(0, Math.min(indice, TAMANHO_CODIGO - 1))]
    campo?.focus()
    campo?.select()
  }

  function limparCodigo() {
    setDigitos(CODIGO_VAZIO)
    // Espera o React reabilitar os campos antes de devolver o foco.
    window.setTimeout(() => focar(0), 0)
  }

  async function enviar(codigoDigitado: string) {
    if (enviando.current || encerrada) return
    setAviso(null)

    if (codigoDigitado.length < TAMANHO_CODIGO) {
      setErro(`Digite os ${TAMANHO_CODIGO} números do código.`)
      return
    }
    if (expirado) {
      setErro('O código expirou. Peça um novo código para continuar.')
      return
    }

    enviando.current = true
    setErro(null)
    setCarregando(true)
    try {
      onAutenticado(await confirmarCodigo(verificacao.id, codigoDigitado, login))
    } catch (err) {
      setCarregando(false)
      tratarErro(err, 'Código incorreto. Confira os números e tente novamente.')
      if (err instanceof ApiError && err.status === 401) limparCodigo()
    } finally {
      enviando.current = false
    }
  }

  async function reenviar() {
    if (bloqueado || segundosParaReenvio > 0) return
    setErro(null)
    setAviso(null)
    setReenviando(true)
    try {
      const nova = await reenviarCodigo(verificacao.id)
      const novos = prazosDe(nova)
      setPrazos(novos)
      setAgora(novos.agora)
      setAviso('Enviamos um novo código. O anterior deixou de valer.')
      limparCodigo()
    } catch (err) {
      tratarErro(err, 'Não foi possível reenviar o código agora.')
    } finally {
      setReenviando(false)
    }
  }

  // Os status são os do ApiExceptionHandler: 401 código errado, 410 expirado,
  // 404 verificação encerrada e 429 reenvio fora de hora.
  function tratarErro(err: unknown, mensagem401: string) {
    if (ehEscolaSuspensa(err)) {
      // A escola foi suspensa entre a senha e o código.
      navigate(rotaAcessoSuspenso(motivoDaSuspensao(err)), { replace: true })
    } else if (!(err instanceof ApiError)) {
      setErro('Ocorreu um erro inesperado. Tente novamente.')
    } else if (err.status === 0) {
      setErro('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.')
    } else if (err.status === 401) {
      setErro(mensagem401)
    } else if (err.status === 410) {
      setPrazos((atuais) => ({ ...atuais, expiraEm: 0 }))
      setErro('O código expirou. Peça um novo código para continuar.')
    } else if (err.status === 404) {
      setEncerrada(true)
      setErro('Esta verificação foi encerrada. Volte e entre novamente com sua senha.')
    } else if (err.status === 429) {
      // Limite de envios: não adianta esperar, só um novo login.
      if (err.detalhe?.toLowerCase().includes('limite')) {
        setEncerrada(true)
        setErro('Você atingiu o limite de códigos deste acesso. Volte e entre novamente.')
      } else {
        setErro('Aguarde um instante antes de pedir outro código.')
      }
    } else {
      setErro('Não foi possível concluir agora. Tente novamente em instantes.')
    }
  }

  // Escreve os números a partir de um campo. Serve para um dígito, para o
  // código colado e para o preenchimento automático, que entrega tudo de uma vez.
  function preencher(indice: number, texto: string) {
    const numeros = texto.replace(/\D/g, '').slice(0, TAMANHO_CODIGO - indice)
    if (!numeros) return

    const novos = [...digitos]
    for (let i = 0; i < numeros.length; i++) novos[indice + i] = numeros[i]
    setDigitos(novos)
    setErro(null)

    if (novos.every(Boolean)) {
      campos.current[TAMANHO_CODIGO - 1]?.blur()
      void enviar(novos.join(''))
    } else {
      focar(indice + numeros.length)
    }
  }

  function aoDigitar(indice: number, valor: string) {
    if (valor === '') {
      setDigitos(digitos.map((digito, i) => (i === indice ? '' : digito)))
      return
    }
    preencher(indice, valor)
  }

  function aoPressionarTecla(indice: number, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Backspace' && !digitos[indice] && indice > 0) {
      event.preventDefault()
      setDigitos(digitos.map((digito, i) => (i === indice - 1 ? '' : digito)))
      focar(indice - 1)
    } else if (event.key === 'ArrowLeft' && indice > 0) {
      event.preventDefault()
      focar(indice - 1)
    } else if (event.key === 'ArrowRight' && indice < TAMANHO_CODIGO - 1) {
      event.preventDefault()
      focar(indice + 1)
    }
  }

  function aoColar(indice: number, event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault()
    const colado = event.clipboardData.getData('text').replace(/\D/g, '')
    // Código inteiro colado em qualquer campo começa do primeiro.
    preencher(colado.length >= TAMANHO_CODIGO ? 0 : indice, colado)
  }

  function aoEnviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void enviar(codigo)
  }

  return (
    <form className={styles.form} onSubmit={aoEnviar} noValidate>
      <span className={styles.selo}>Verificação de segurança</span>

      <h2 className={styles.titulo}>Código de autenticação</h2>
      <p className={styles.subtitulo}>
        Digite o código de {TAMANHO_CODIGO} números que enviamos para o seu e-mail.
      </p>

      <div className={styles.campos} role="group" aria-label="Código de autenticação">
        {digitos.map((digito, indice) => (
          <input
            key={indice}
            ref={(el) => {
              campos.current[indice] = el
            }}
            className={styles.campo}
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            // Sem maxLength: o preenchimento automático entrega o código
            // inteiro no primeiro campo, e preencher() distribui.
            autoComplete={indice === 0 ? 'one-time-code' : 'off'}
            aria-label={`Dígito ${indice + 1} de ${TAMANHO_CODIGO}`}
            aria-invalid={erro ? true : undefined}
            value={digito}
            onChange={(e) => aoDigitar(indice, e.target.value)}
            onKeyDown={(e) => aoPressionarTecla(indice, e)}
            onPaste={(e) => aoColar(indice, e)}
            onFocus={(e) => e.target.select()}
            disabled={bloqueado}
            autoFocus={indice === 0}
          />
        ))}
      </div>

      <p
        className={`${styles.cronometro} ${expirado ? styles.cronometroExpirado : ''}`}
        role="timer"
        aria-live="off"
      >
        <Icon name="clock" size={16} />
        {expirado ? (
          <span>O código expirou</span>
        ) : (
          <span>
            O código vale por mais <strong>{formatarTempo(segundosRestantes)}</strong>
          </span>
        )}
      </p>

      <Button
        type="submit"
        variant="primary"
        className={styles.botao}
        disabled={bloqueado || expirado || codigo.length < TAMANHO_CODIGO}
      >
        {carregando ? 'Verificando…' : 'Acessar instituição'}
      </Button>

      {!encerrada && (
        <p className={styles.reenvio}>
          Não recebeu o código?{' '}
          {segundosParaReenvio > 0 ? (
            <span className={styles.reenvioEspera}>
              Reenviar em {formatarTempo(segundosParaReenvio)}
            </span>
          ) : (
            <button type="button" className={styles.link} onClick={reenviar} disabled={bloqueado}>
              {reenviando ? 'Reenviando…' : 'Reenviar código'}
            </button>
          )}
        </p>
      )}

      <button type="button" className={styles.voltar} onClick={onVoltar} disabled={carregando}>
        <Icon name="arrowLeft" size={15} />
        Voltar para o login
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
  )
}
