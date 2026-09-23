import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { ApiError } from '../../../../services/api'
import { PLANOS_ESCOLA, type Escola, type EscolaRequest, type PlanoEscola } from '../../../../types/escola'
import {
  cnpjValido,
  mascararCep,
  mascararCnpj,
  mascararTelefone,
  somenteDigitos,
} from '../../../../utils/formatters'
import styles from './CadastroEscolaModal.module.css'

type Campos = {
  nome: string
  cnpj: string
  email: string
  telefone: string
  cep: string
  endereco: string
  plano: PlanoEscola | ''
  ativo: boolean
}

type Erros = Partial<Record<keyof Campos, string>>

const CAMPOS_INICIAIS: Campos = {
  nome: '',
  cnpj: '',
  email: '',
  telefone: '',
  cep: '',
  endereco: '',
  plano: '',
  ativo: true,
}

// Limites das colunas de ESCOLA no banco.
const MAX_NOME = 150
const MAX_EMAIL = 150
const MAX_ENDERECO = 200

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validar(campos: Campos): Erros {
  const erros: Erros = {}

  if (!campos.nome.trim()) erros.nome = 'Informe a razão social.'

  if (!campos.cnpj) erros.cnpj = 'Informe o CNPJ.'
  else if (!cnpjValido(campos.cnpj)) erros.cnpj = 'CNPJ inválido.'

  if (!campos.email.trim()) erros.email = 'Informe o e-mail do responsável.'
  else if (!EMAIL_REGEX.test(campos.email.trim())) erros.email = 'E-mail inválido.'

  const telefone = somenteDigitos(campos.telefone)
  if (telefone && telefone.length < 10) erros.telefone = 'Telefone incompleto.'

  const cep = somenteDigitos(campos.cep)
  if (cep && cep.length !== 8) erros.cep = 'CEP deve ter 8 dígitos.'

  if (!campos.plano) erros.plano = 'Selecione o plano contratado.'

  return erros
}

function paraRequest(campos: Campos): EscolaRequest {
  return {
    nome: campos.nome.trim(),
    cnpjCpf: somenteDigitos(campos.cnpj),
    email: campos.email.trim(),
    telefone: campos.telefone || undefined,
    cep: somenteDigitos(campos.cep) || undefined,
    endereco: campos.endereco.trim() || undefined,
    plano: campos.plano || undefined,
    ativo: campos.ativo,
  }
}

type CadastroEscolaModalProps = {
  onFechar: () => void
  /** Envia o cadastro; deve lançar ApiError em caso de falha. */
  onSalvar: (dados: EscolaRequest) => Promise<Escola>
  onCadastrada: (escola: Escola) => void
}

/**
 * Formulário de cadastro de escola (RF06), exclusivo do SUPER_ADMIN.
 * Montado só enquanto está aberto, então cada abertura começa limpa.
 */
export function CadastroEscolaModal({ onFechar, onSalvar, onCadastrada }: CadastroEscolaModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tituloId = useId()
  const [campos, setCampos] = useState<Campos>(CAMPOS_INICIAIS)
  const [erros, setErros] = useState<Erros>({})
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function fechar() {
    if (!salvando) onFechar()
  }

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErroEnvio(null)

    const novosErros = validar(campos)
    setErros(novosErros)
    const primeiroInvalido = Object.keys(novosErros)[0]
    if (primeiroInvalido) {
      dialogRef.current?.querySelector<HTMLElement>(`[name="${primeiroInvalido}"]`)?.focus()
      return
    }

    setSalvando(true)
    try {
      const escola = await onSalvar(paraRequest(campos))
      onCadastrada(escola)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setErros({ cnpj: 'Já existe uma escola cadastrada com este CNPJ.' })
        dialogRef.current?.querySelector<HTMLElement>('[name="cnpj"]')?.focus()
      } else if (e instanceof ApiError && e.status === 400) {
        setErroEnvio('Os dados foram recusados pelo servidor. Revise os campos e tente novamente.')
      } else {
        setErroEnvio(e instanceof ApiError ? e.message : 'Erro inesperado ao cadastrar a escola.')
      }
      setSalvando(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby={tituloId}
      onCancel={(e) => {
        e.preventDefault()
        fechar()
      }}
    >
      <form className={styles.form} noValidate onSubmit={handleSubmit}>
        <header className={styles.header}>
          <h2 id={tituloId} className={styles.title}>
            Cadastrar nova instituição
          </h2>
          <button
            type="button"
            className={styles.close}
            aria-label="Fechar"
            disabled={salvando}
            onClick={fechar}
          >
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className={styles.body}>
          <Secao titulo="Dados básicos">
            <Campo label="Razão social" erro={erros.nome} obrigatorio largo>
              {(props) => (
                <input
                  {...props}
                  name="nome"
                  maxLength={MAX_NOME}
                  autoComplete="organization"
                  placeholder="Ex.: Colégio Objetivo Matriz Ltda"
                  value={campos.nome}
                  onChange={(e) => alterar('nome', e.target.value)}
                />
              )}
            </Campo>
            <Campo label="CNPJ" erro={erros.cnpj} obrigatorio>
              {(props) => (
                <input
                  {...props}
                  name="cnpj"
                  inputMode="numeric"
                  placeholder="00.000.000/0000-00"
                  value={campos.cnpj}
                  onChange={(e) => alterar('cnpj', mascararCnpj(e.target.value))}
                />
              )}
            </Campo>
            <Campo label="E-mail do responsável" erro={erros.email} obrigatorio>
              {(props) => (
                <input
                  {...props}
                  name="email"
                  type="email"
                  maxLength={MAX_EMAIL}
                  autoComplete="email"
                  placeholder="diretoria@escola.com.br"
                  value={campos.email}
                  onChange={(e) => alterar('email', e.target.value)}
                />
              )}
            </Campo>
          </Secao>

          <Secao titulo="Contato e endereço">
            <Campo label="Telefone" erro={erros.telefone}>
              {(props) => (
                <input
                  {...props}
                  name="telefone"
                  type="tel"
                  autoComplete="tel"
                  placeholder="(31) 99999-9999"
                  value={campos.telefone}
                  onChange={(e) => alterar('telefone', mascararTelefone(e.target.value))}
                />
              )}
            </Campo>
            <Campo label="CEP" erro={erros.cep}>
              {(props) => (
                <input
                  {...props}
                  name="cep"
                  inputMode="numeric"
                  autoComplete="postal-code"
                  placeholder="00000-000"
                  value={campos.cep}
                  onChange={(e) => alterar('cep', mascararCep(e.target.value))}
                />
              )}
            </Campo>
            <Campo label="Endereço" erro={erros.endereco} largo>
              {(props) => (
                <input
                  {...props}
                  name="endereco"
                  maxLength={MAX_ENDERECO}
                  autoComplete="street-address"
                  placeholder="Rua, número, bairro, cidade - UF"
                  value={campos.endereco}
                  onChange={(e) => alterar('endereco', e.target.value)}
                />
              )}
            </Campo>
          </Secao>

          <Secao titulo="Assinatura">
            <Campo label="Plano contratado" erro={erros.plano} obrigatorio>
              {(props) => (
                <select
                  {...props}
                  name="plano"
                  value={campos.plano}
                  onChange={(e) => alterar('plano', e.target.value as PlanoEscola | '')}
                >
                  <option value="" disabled>
                    Selecione…
                  </option>
                  {PLANOS_ESCOLA.map((plano) => (
                    <option key={plano} value={plano}>
                      {plano}
                    </option>
                  ))}
                </select>
              )}
            </Campo>
            <div className={styles.field}>
              <span className={styles.label} id={`${tituloId}-status`}>
                Status inicial
              </span>
              <label className={styles.switch}>
                <input
                  type="checkbox"
                  role="switch"
                  name="ativo"
                  aria-labelledby={`${tituloId}-status`}
                  checked={campos.ativo}
                  onChange={(e) => alterar('ativo', e.target.checked)}
                />
                <span className={styles.track} aria-hidden="true" />
                <span className={campos.ativo ? styles.statusAtivo : styles.statusInativo}>
                  {campos.ativo ? 'Ativo' : 'Inativo'}
                </span>
              </label>
            </div>
          </Secao>

          {erroEnvio && (
            <p className={styles.alerta} role="alert">
              {erroEnvio}
            </p>
          )}
        </div>

        <footer className={styles.footer}>
          <Button className={styles.footerButton} disabled={salvando} onClick={fechar}>
            Cancelar
          </Button>
          <Button type="submit" variant="accent" className={styles.footerButton} disabled={salvando}>
            {salvando ? 'Salvando…' : campos.ativo ? 'Salvar e ativar' : 'Salvar como inativa'}
          </Button>
        </footer>
      </form>
    </dialog>
  )
}

function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <fieldset className={styles.section}>
      <legend className={styles.sectionTag}>{titulo}</legend>
      <div className={styles.grid}>{children}</div>
    </fieldset>
  )
}

type PropsControle = {
  id: string
  className: string
  required: boolean
  'aria-invalid': boolean
  'aria-describedby': string | undefined
}

type CampoProps = {
  label: string
  erro?: string
  obrigatorio?: boolean
  /** Ocupa a linha inteira do grid. */
  largo?: boolean
  children: (props: PropsControle) => ReactNode
}

function Campo({ label, erro, obrigatorio = false, largo = false, children }: CampoProps) {
  const id = useId()
  const erroId = `${id}-erro`

  return (
    <div className={`${styles.field} ${largo ? styles.fieldWide : ''}`}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {obrigatorio && (
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children({
        id,
        className: styles.control,
        required: obrigatorio,
        'aria-invalid': Boolean(erro),
        'aria-describedby': erro ? erroId : undefined,
      })}
      {erro && (
        <span id={erroId} className={styles.error}>
          {erro}
        </span>
      )}
    </div>
  )
}
