import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '../../../../components/ui/Button/Button'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { Modal, ModalAlerta, ModalAviso } from '../../../../components/modal/Modal/Modal'
import { ApiError, ehLimiteDeUsuarios } from '../../../../services/api'
import { buscarMembroEquipe } from '../../../../services/escola/equipeService'
import {
  DESCRICOES_PERFIL_EQUIPE,
  ROTULOS_PERFIL_EQUIPE,
  type MembroEquipe,
  type MembroEquipeRequest,
  type PerfilEquipe,
} from '../../../../types/escola/equipe'
import { cpfValido, mascararCpf, mascararTelefone, somenteDigitos } from '../../../../utils/formatters'
import styles from './CadastroEquipeModal.module.css'

type Campos = {
  perfil: PerfilEquipe
  nome: string
  cpf: string
  email: string
  telefone: string
}

type Erros = Partial<Record<keyof Campos, string>>

const PERFIS: PerfilEquipe[] = ['SECRETARIA', 'FINANCEIRO']

// Limites do MembroEquipeRequest no backend.
const MAX_NOME = 150
const MAX_EMAIL = 150

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function camposIniciais(perfil: PerfilEquipe): Campos {
  return { perfil, nome: '', cpf: '', email: '', telefone: '' }
}

function camposDe(membro: MembroEquipe): Campos {
  return {
    perfil: membro.perfil,
    nome: membro.nome,
    cpf: mascararCpf(membro.cpf ?? ''),
    email: membro.email ?? '',
    telefone: mascararTelefone(membro.telefone ?? ''),
  }
}

function validar(campos: Campos): Erros {
  // Na ordem da tela, para o foco ir ao primeiro campo inválido.
  const erros: Erros = {}
  if (!campos.nome.trim()) erros.nome = 'Informe o nome.'
  if (!campos.cpf) erros.cpf = 'Informe o CPF.'
  else if (!cpfValido(campos.cpf)) erros.cpf = 'CPF inválido.'
  // O e-mail vira o login.
  if (!campos.email.trim()) erros.email = 'Informe o e-mail.'
  else if (!EMAIL_REGEX.test(campos.email.trim())) erros.email = 'E-mail inválido.'
  const telefone = somenteDigitos(campos.telefone)
  if (telefone && telefone.length < 10) erros.telefone = 'Telefone incompleto.'
  return erros
}

function paraRequest(campos: Campos): MembroEquipeRequest {
  const telefone = campos.telefone.trim()
  return {
    perfil: campos.perfil,
    nome: campos.nome.trim(),
    cpf: somenteDigitos(campos.cpf),
    email: campos.email.trim(),
    telefone: telefone ? telefone : null,
  }
}

type CadastroEquipeModalProps = {
  /** Sem id é cadastro; com id, carrega a pessoa e edita (o perfil não muda). */
  membroId?: string
  /** Perfil já marcado ao abrir o cadastro. */
  perfilInicial?: PerfilEquipe
  onFechar: () => void
  /** Envia os dados; deve lançar ApiError em caso de falha. */
  onSalvar: (dados: MembroEquipeRequest) => Promise<MembroEquipe>
  onSalvo: (membro: MembroEquipe) => void
}

/**
 * Cadastro e edição de alguém da equipe pelo MASTER. No cadastro, o backend
 * cria o login (o e-mail) e envia o primeiro acesso, com o CPF de senha
 * provisória; na edição, trocar o e-mail troca o login. Montado só enquanto
 * está aberto, então cada abertura começa limpa.
 */
export function CadastroEquipeModal({
  membroId,
  perfilInicial = 'SECRETARIA',
  onFechar,
  onSalvar,
  onSalvo,
}: CadastroEquipeModalProps) {
  const edicao = membroId !== undefined
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [campos, setCampos] = useState<Campos>(() => camposIniciais(perfilInicial))
  const [erros, setErros] = useState<Erros>({})
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)
  // A listagem não traz o CPF: na edição, os campos vêm do detalhe.
  const [carregando, setCarregando] = useState(edicao)
  const [erroCarga, setErroCarga] = useState<string | null>(null)

  useEffect(() => {
    if (!membroId) return
    const controller = new AbortController()
    buscarMembroEquipe(membroId, controller.signal)
      .then((membro) => setCampos(camposDe(membro)))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setErroCarga(e instanceof ApiError ? e.message : 'Erro inesperado ao carregar os dados.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [membroId])

  const bloqueado = carregando || erroCarga !== null
  const rotuloPerfil = ROTULOS_PERFIL_EQUIPE[campos.perfil].toLowerCase()

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
      onSalvo(await onSalvar(paraRequest(campos)))
    } catch (e) {
      if (ehLimiteDeUsuarios(e)) {
        // Também 409, mas não é de um campo: a escola está no limite de usuários do plano.
        setErroEnvio((e as ApiError).message)
      } else if (e instanceof ApiError && e.status === 409) {
        // O 409 vem do CPF já na equipe ou do e-mail já usado como login.
        const campo = e.detalhe?.toLowerCase().includes('login') ? 'email' : 'cpf'
        setErros(
          campo === 'email'
            ? { email: 'Este e-mail já é usado por outro usuário.' }
            : { cpf: 'Já existe alguém da equipe com este CPF.' },
        )
        dialogRef.current?.querySelector<HTMLElement>(`[name="${campo}"]`)?.focus()
      } else if (e instanceof ApiError && e.status === 400) {
        setErroEnvio('Os dados foram recusados pelo servidor. Revise os campos e tente novamente.')
      } else {
        setErroEnvio(e instanceof ApiError ? e.message : 'Erro inesperado ao salvar.')
      }
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo={edicao ? `Editar ${rotuloPerfil}` : 'Adicionar à equipe'}
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
          <Button type="submit" variant="accent" disabled={salvando || bloqueado}>
            {salvando ? 'Salvando…' : edicao ? 'Salvar alterações' : 'Cadastrar e enviar acesso'}
          </Button>
        </>
      }
    >
      {carregando && <ModalAviso>Carregando os dados…</ModalAviso>}
      {erroCarga && <ModalAlerta>{erroCarga}</ModalAlerta>}

      {!bloqueado && (
        <>
          {/* O perfil só se escolhe no cadastro: quem muda de função é inativado e cadastrado de novo. */}
          {!edicao && (
            <fieldset className={styles.perfis}>
              <legend className={styles.legenda}>Função</legend>
              {PERFIS.map((perfil) => (
                <label key={perfil} className={styles.perfil}>
                  <input
                    type="radio"
                    name="perfil"
                    value={perfil}
                    checked={campos.perfil === perfil}
                    onChange={() => alterar('perfil', perfil)}
                  />
                  <span className={styles.perfilTexto}>
                    <strong>{ROTULOS_PERFIL_EQUIPE[perfil]}</strong>
                    <span>{DESCRICOES_PERFIL_EQUIPE[perfil]}</span>
                  </span>
                </label>
              ))}
            </fieldset>
          )}

          <div className={styles.grid}>
            <Campo label="Nome completo" erro={erros.nome} obrigatorio className={styles.span4}>
              {(props) => (
                <input
                  {...props}
                  name="nome"
                  maxLength={MAX_NOME}
                  autoComplete="name"
                  placeholder="Nome da pessoa"
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
              dica={
                edicao
                  ? 'Trocar o e-mail troca o login.'
                  : 'É o login. O acesso vai para ele, com o CPF como senha provisória.'
              }
              className={styles.span4}
            >
              {(props) => (
                <input
                  {...props}
                  name="email"
                  type="email"
                  maxLength={MAX_EMAIL}
                  autoComplete="email"
                  placeholder="nome@escola.com.br"
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
          </div>
        </>
      )}

      {erroEnvio && <ModalAlerta>{erroEnvio}</ModalAlerta>}
    </Modal>
  )
}
