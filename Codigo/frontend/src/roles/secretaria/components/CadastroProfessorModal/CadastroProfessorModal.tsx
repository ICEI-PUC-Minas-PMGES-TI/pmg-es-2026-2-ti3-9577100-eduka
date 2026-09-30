import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { Icon } from '../../../../components/Icon/Icon'
import { Select } from '../../../../components/Select/Select'
import { ApiError } from '../../../../services/api'
import {
  OPCOES_VINCULO,
  type Professor,
  type ProfessorRequest,
  type VinculoProfessor,
} from '../../../../types/professor'
import { cpfValido, mascararCpf, mascararTelefone, somenteDigitos } from '../../../../utils/formatters'
import styles from './CadastroProfessorModal.module.css'

type Campos = {
  nome: string
  cpf: string
  email: string
  telefone: string
  formacao: string
  vinculo: VinculoProfessor | ''
  /** ISO (AAAA-MM-DD), ou '' quando vazia. */
  dataAdmissao: string
}

type Erros = Partial<Record<keyof Campos, string>>

const CAMPOS_INICIAIS: Campos = {
  nome: '',
  cpf: '',
  email: '',
  telefone: '',
  formacao: '',
  // O regime mais comum já vem marcado, como no protótipo.
  vinculo: 'CLT_40H',
  dataAdmissao: '',
}

// Limites do ProfessorRequest no backend.
const MAX_NOME = 150
const MAX_EMAIL = 150
const MAX_FORMACAO = 150

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validar(campos: Campos): Erros {
  // Na ordem da tela, para o foco ir ao primeiro campo inválido.
  const erros: Erros = {}

  if (!campos.nome.trim()) erros.nome = 'Informe o nome do professor.'

  if (!campos.cpf) erros.cpf = 'Informe o CPF.'
  else if (!cpfValido(campos.cpf)) erros.cpf = 'CPF inválido.'

  // O e-mail vira o login do professor.
  if (!campos.email.trim()) erros.email = 'Informe o e-mail do professor.'
  else if (!EMAIL_REGEX.test(campos.email.trim())) erros.email = 'E-mail inválido.'

  // Telefone é opcional: só valida o que foi digitado.

  const telefone = somenteDigitos(campos.telefone)
  if (telefone && telefone.length < 10) erros.telefone = 'Telefone incompleto.'

  if (!campos.vinculo) erros.vinculo = 'Selecione o vínculo.'

  return erros
}

/** Campo opcional vazio vai como null, para o backend não gravar string vazia. */
function opcional(valor: string): string | null {
  const limpo = valor.trim()
  return limpo ? limpo : null
}

function paraRequest(campos: Campos): ProfessorRequest {
  return {
    nome: campos.nome.trim(),
    cpf: somenteDigitos(campos.cpf),
    email: campos.email.trim(),
    telefone: opcional(campos.telefone),
    formacao: opcional(campos.formacao),
    dataAdmissao: campos.dataAdmissao || null,
    // validar() já garante que um vínculo foi escolhido.
    vinculo: campos.vinculo as VinculoProfessor,
  }
}

type CadastroProfessorModalProps = {
  onFechar: () => void
  /** Envia o cadastro; deve lançar ApiError em caso de falha. */
  onSalvar: (dados: ProfessorRequest) => Promise<Professor>
  onCadastrado: (professor: Professor) => void
}

/**
 * Formulário de cadastro de professor da secretaria. A escola não aparece:
 * o backend usa a do token. Ao salvar, o backend cria o login do professor
 * (o e-mail) e envia a senha provisória. Montado só enquanto está aberto, então cada
 * abertura começa limpa.
 */
export function CadastroProfessorModal({ onFechar, onSalvar, onCadastrado }: CadastroProfessorModalProps) {
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
      const professor = await onSalvar(paraRequest(campos))
      onCadastrado(professor)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        // O 409 vem do CPF repetido na escola ou do e-mail já usado como login.
        const campo = e.detalhe?.toLowerCase().includes('login') ? 'email' : 'cpf'
        setErros(
          campo === 'email'
            ? { email: 'Este e-mail já é usado por outro usuário.' }
            : { cpf: 'Já existe um professor cadastrado com este CPF.' },
        )
        dialogRef.current?.querySelector<HTMLElement>(`[name="${campo}"]`)?.focus()
      } else if (e instanceof ApiError && e.status === 400) {
        setErroEnvio('Os dados foram recusados pelo servidor. Revise os campos e tente novamente.')
      } else {
        setErroEnvio(e instanceof ApiError ? e.message : 'Erro inesperado ao cadastrar o professor.')
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
            Cadastrar novo professor
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
          <div className={styles.grid}>
            <Campo label="Nome completo" erro={erros.nome} obrigatorio className={styles.span4}>
              {(props) => (
                <input
                  {...props}
                  name="nome"
                  maxLength={MAX_NOME}
                  autoComplete="name"
                  placeholder="Nome do professor"
                  value={campos.nome}
                  onChange={(e) => alterar('nome', e.target.value)}
                />
              )}
            </Campo>
            <Campo label="CPF" erro={erros.cpf} obrigatorio className={styles.span2}>
              {(props) => (
                <input
                  {...props}
                  name="cpf"
                  inputMode="numeric"
                  placeholder="000.000.000-00"
                  value={campos.cpf}
                  onChange={(e) => alterar('cpf', mascararCpf(e.target.value))}
                />
              )}
            </Campo>

            <Campo
              label="E-mail"
              erro={erros.email}
              obrigatorio
              dica="O acesso ao Eduka é enviado para ele."
              className={styles.span2}
            >
              {(props) => (
                <input
                  {...props}
                  name="email"
                  type="email"
                  maxLength={MAX_EMAIL}
                  autoComplete="email"
                  placeholder="professor@escola.com.br"
                  value={campos.email}
                  onChange={(e) => alterar('email', e.target.value)}
                />
              )}
            </Campo>
            <Campo label="Telefone" erro={erros.telefone} className={styles.span2}>
              {(props) => (
                <input
                  {...props}
                  name="telefone"
                  type="tel"
                  autoComplete="tel"
                  placeholder="(00) 00000-0000"
                  value={campos.telefone}
                  onChange={(e) => alterar('telefone', mascararTelefone(e.target.value))}
                />
              )}
            </Campo>
            <Campo label="Formação" erro={erros.formacao} className={styles.span2}>
              {(props) => (
                <input
                  {...props}
                  name="formacao"
                  maxLength={MAX_FORMACAO}
                  placeholder="Ex.: Licenciatura em Matemática"
                  value={campos.formacao}
                  onChange={(e) => alterar('formacao', e.target.value)}
                />
              )}
            </Campo>

            <Campo label="Vínculo" erro={erros.vinculo} obrigatorio className={styles.span3}>
              {(props) => (
                <Select<VinculoProfessor | ''>
                  {...props}
                  name="vinculo"
                  opcoes={OPCOES_VINCULO}
                  valor={campos.vinculo}
                  placeholder="Selecione"
                  onChange={(vinculo) => alterar('vinculo', vinculo)}
                />
              )}
            </Campo>
            <Campo label="Data de admissão" erro={erros.dataAdmissao} className={styles.span3}>
              {(props) => (
                <CampoData
                  {...props}
                  name="dataAdmissao"
                  valor={campos.dataAdmissao}
                  onChange={(data) => alterar('dataAdmissao', data)}
                />
              )}
            </Campo>
          </div>

          {erroEnvio && (
            <p className={styles.alerta} role="alert">
              {erroEnvio}
            </p>
          )}
        </div>

        <footer className={styles.footer}>
          <Button disabled={salvando} onClick={fechar}>
            Cancelar
          </Button>
          <Button type="submit" variant="accent" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar professor'}
          </Button>
        </footer>
      </form>
    </dialog>
  )
}
