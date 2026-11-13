import { useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Campo } from '../../../../components/Campo/Campo'
import { Modal, ModalAlerta } from '../../../../components/Modal/Modal'
import { Select } from '../../../../components/Select/Select'
import { Switch } from '../../../../components/Switch/Switch'
import { ApiError } from '../../../../services/api'
import {
  OPCOES_PLANO,
  type Escola,
  type EscolaRequest,
  type PlanoEscola,
} from '../../../../types/escola'
import {
  cnpjValido,
  cpfValido,
  mascararCnpj,
  mascararCpf,
  mascararTelefone,
  somenteDigitos,
} from '../../../../utils/formatters'
import { CamposEndereco } from '../CamposEndereco/CamposEndereco'
import {
  ENDERECO_VAZIO,
  enderecoDosValores,
  validarEndereco,
  type ValoresEndereco,
} from '../CamposEndereco/endereco'
import styles from './CadastroEscolaModal.module.css'

type Campos = ValoresEndereco & {
  nome: string
  cnpj: string
  email: string
  telefone: string
  plano: PlanoEscola | ''
  ativo: boolean
  masterNome: string
  masterCpf: string
  masterEmail: string
}

type Erros = Partial<Record<keyof Campos, string>>

const CAMPOS_INICIAIS: Campos = {
  ...ENDERECO_VAZIO,
  nome: '',
  cnpj: '',
  email: '',
  telefone: '',
  plano: '',
  ativo: true,
  masterNome: '',
  masterCpf: '',
  masterEmail: '',
}

// Limites das colunas de ESCOLA no banco.
const MAX_NOME = 150
const MAX_EMAIL = 150

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validar(campos: Campos): Erros {
  // Na ordem da tela, para o foco ir ao primeiro campo inválido.
  const erros: Erros = {}

  if (!campos.nome.trim()) erros.nome = 'Informe a razão social.'

  if (!campos.cnpj) erros.cnpj = 'Informe o CNPJ.'
  else if (!cnpjValido(campos.cnpj)) erros.cnpj = 'CNPJ inválido.'

  if (!campos.email.trim()) erros.email = 'Informe o e-mail do responsável.'
  else if (!EMAIL_REGEX.test(campos.email.trim())) erros.email = 'E-mail inválido.'

  const telefone = somenteDigitos(campos.telefone)
  if (!telefone) erros.telefone = 'Informe o telefone.'
  else if (telefone.length < 10) erros.telefone = 'Telefone incompleto.'

  Object.assign(erros, validarEndereco(campos))

  if (!campos.plano) erros.plano = 'Selecione o plano contratado.'

  if (!campos.masterNome.trim()) erros.masterNome = 'Informe o nome do gestor.'

  if (!campos.masterCpf) erros.masterCpf = 'Informe o CPF do gestor.'
  else if (!cpfValido(campos.masterCpf)) erros.masterCpf = 'CPF inválido.'

  if (!campos.masterEmail.trim()) erros.masterEmail = 'Informe o e-mail de acesso do gestor.'
  else if (!EMAIL_REGEX.test(campos.masterEmail.trim())) erros.masterEmail = 'E-mail inválido.'

  return erros
}

function paraRequest(campos: Campos): EscolaRequest {
  return {
    nome: campos.nome.trim(),
    cnpjCpf: somenteDigitos(campos.cnpj),
    email: campos.email.trim(),
    telefone: campos.telefone,
    // validar() já garante que todos os campos do endereço foram preenchidos.
    endereco: enderecoDosValores(campos),
    // validar() já garante que um plano foi escolhido.
    plano: campos.plano as PlanoEscola,
    ativo: campos.ativo,
    master: {
      nome: campos.masterNome.trim(),
      cpf: somenteDigitos(campos.masterCpf),
      email: campos.masterEmail.trim(),
    },
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
  const statusId = useId()
  const [campos, setCampos] = useState<Campos>(CAMPOS_INICIAIS)
  const [erros, setErros] = useState<Erros>({})
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
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
        // O 409 vem do CNPJ repetido ou do e-mail do gestor já ser login de alguém.
        const loginRepetido = e.detalhe?.toLowerCase().includes('login') ?? false
        const campo = loginRepetido ? 'masterEmail' : 'cnpj'
        setErros({
          [campo]: loginRepetido
            ? 'Este e-mail já é login de outro usuário do Eduka.'
            : 'Já existe uma escola cadastrada com este CNPJ.',
        })
        dialogRef.current?.querySelector<HTMLElement>(`[name="${campo}"]`)?.focus()
      } else if (e instanceof ApiError && e.status === 400) {
        setErroEnvio('Os dados foram recusados pelo servidor. Revise os campos e tente novamente.')
      } else {
        setErroEnvio(e instanceof ApiError ? e.message : 'Erro inesperado ao cadastrar a escola.')
      }
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo="Cadastrar nova instituição"
      tamanho="grande"
      ocupado={salvando}
      onFechar={onFechar}
      onEnviar={handleSubmit}
      dialogRef={dialogRef}
      rodape={
        <>
          <Button disabled={salvando} onClick={onFechar}>
            Cancelar
          </Button>
          <Button type="submit" variant="accent" disabled={salvando}>
            {salvando ? 'Salvando…' : campos.ativo ? 'Salvar e ativar' : 'Salvar como inativa'}
          </Button>
        </>
      }
    >
      <Secao titulo="Dados básicos">
        <Campo label="Razão social" erro={erros.nome} obrigatorio>
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
        <Campo label="Telefone" erro={erros.telefone} obrigatorio>
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
      </Secao>

      <Secao titulo="Endereço">
        <CamposEndereco valores={campos} erros={erros} onAlterar={alterar} />
      </Secao>

      <Secao titulo="Assinatura">
        <Campo label="Plano contratado" erro={erros.plano} obrigatorio>
          {(props) => (
            <Select<PlanoEscola | ''>
              {...props}
              name="plano"
              opcoes={OPCOES_PLANO}
              valor={campos.plano}
              onChange={(plano) => alterar('plano', plano)}
            />
          )}
        </Campo>
        <div className={styles.field}>
          <span className={styles.label} id={`${statusId}`}>
            Status inicial
          </span>
          <Switch
            name="ativo"
            aria-labelledby={`${statusId}`}
            checked={campos.ativo}
            onChange={(ativo) => alterar('ativo', ativo)}
            rotuloLigado="Ativo"
            rotuloDesligado="Inativo"
          />
        </div>
      </Secao>

      <Secao titulo="Gestor da escola">
        <p className={styles.ajuda}>
          Recebe o primeiro acesso como MASTER. O e-mail vira o login e a senha provisória é o CPF; o
          convite vai por e-mail assim que a escola for salva.
        </p>
        <Campo label="Nome completo" erro={erros.masterNome} obrigatorio>
          {(props) => (
            <input
              {...props}
              name="masterNome"
              maxLength={MAX_NOME}
              autoComplete="off"
              placeholder="Ex.: Maria Souza"
              value={campos.masterNome}
              onChange={(e) => alterar('masterNome', e.target.value)}
            />
          )}
        </Campo>
        <Campo label="CPF" erro={erros.masterCpf} obrigatorio>
          {(props) => (
            <input
              {...props}
              name="masterCpf"
              inputMode="numeric"
              autoComplete="off"
              placeholder="000.000.000-00"
              value={campos.masterCpf}
              onChange={(e) => alterar('masterCpf', mascararCpf(e.target.value))}
            />
          )}
        </Campo>
        <Campo label="E-mail de acesso" erro={erros.masterEmail} obrigatorio>
          {(props) => (
            <input
              {...props}
              name="masterEmail"
              type="email"
              maxLength={MAX_EMAIL}
              autoComplete="off"
              placeholder="gestor@escola.com.br"
              value={campos.masterEmail}
              onChange={(e) => alterar('masterEmail', e.target.value)}
            />
          )}
        </Campo>
      </Secao>

      {erroEnvio && <ModalAlerta>{erroEnvio}</ModalAlerta>}
    </Modal>
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
