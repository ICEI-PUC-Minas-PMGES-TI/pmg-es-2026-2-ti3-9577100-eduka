import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { Modal, ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { Select } from '../../../../components/Select/Select'
import { buscarAluno } from '../../../../services/alunoService'
import { ApiError } from '../../../../services/api'
import {
  OPCOES_GENERO,
  type Aluno,
  type AlunoRequest,
  type Endereco,
  type Responsavel,
} from '../../../../types/aluno'
import {
  cpfValido,
  mascararCep,
  mascararCpf,
  mascararTelefone,
  somenteDigitos,
} from '../../../../utils/formatters'
import styles from './AlunoModal.module.css'

type Campos = {
  nome: string
  matricula: string
  cpf: string
  email: string
  /** ISO (AAAA-MM-DD), ou '' quando vazia. */
  dataNascimento: string
  genero: string
  cep: string
  logradouro: string
  numero: string
  bairro: string
  cidade: string
  estado: string
}

type CamposResponsavel = {
  nome: string
  parentesco: string
  cpf: string
  email: string
  telefone: string
}

type Erros = Partial<Record<keyof Campos, string>>
type ErrosResponsavel = Partial<Record<keyof CamposResponsavel, string>>

const CAMPOS_INICIAIS: Campos = {
  nome: '',
  matricula: '',
  cpf: '',
  email: '',
  dataNascimento: '',
  genero: '',
  cep: '',
  logradouro: '',
  numero: '',
  bairro: '',
  cidade: '',
  estado: '',
}

const RESPONSAVEL_VAZIO: CamposResponsavel = { nome: '', parentesco: '', cpf: '', email: '', telefone: '' }

const MAX_NOME = 150
const MAX_EMAIL = 150
// Limite da coluna numero_matricula (V5).
const MAX_MATRICULA = 50

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function camposDe(aluno: Aluno): Campos {
  const endereco = aluno.endereco
  return {
    nome: aluno.nome,
    matricula: aluno.matricula,
    cpf: mascararCpf(aluno.cpf ?? ''),
    email: aluno.email ?? '',
    dataNascimento: aluno.dataNascimento ?? '',
    genero: aluno.genero ?? '',
    cep: mascararCep(endereco?.cep ?? ''),
    logradouro: endereco?.logradouro ?? '',
    numero: endereco?.numero ?? '',
    bairro: endereco?.bairro ?? '',
    cidade: endereco?.cidade ?? '',
    estado: endereco?.estado ?? '',
  }
}

function validar(campos: Campos): Erros {
  // Na ordem da tela, para o foco ir ao primeiro campo inválido.
  const erros: Erros = {}

  if (!campos.nome.trim()) erros.nome = 'Informe o nome do aluno.'
  if (!campos.matricula.trim()) erros.matricula = 'Informe o número de matrícula.'

  // O CPF é a senha provisória e o e-mail é o login do aluno.
  if (!campos.cpf) erros.cpf = 'Informe o CPF.'
  else if (!cpfValido(campos.cpf)) erros.cpf = 'CPF inválido.'

  if (!campos.email.trim()) erros.email = 'Informe o e-mail do aluno.'
  else if (!EMAIL_REGEX.test(campos.email.trim())) erros.email = 'E-mail inválido.'

  // Endereço é opcional: só valida o que foi digitado.
  const cep = somenteDigitos(campos.cep)
  if (cep && cep.length !== 8) erros.cep = 'CEP incompleto.'
  if (campos.estado.trim() && campos.estado.trim().length !== 2) erros.estado = 'Use a sigla (ex.: MG).'

  return erros
}

function validarResponsavel(responsavel: CamposResponsavel): ErrosResponsavel {
  const erros: ErrosResponsavel = {}
  if (!responsavel.nome.trim()) erros.nome = 'Informe o nome.'
  if (responsavel.cpf && !cpfValido(responsavel.cpf)) erros.cpf = 'CPF inválido.'
  if (responsavel.email.trim() && !EMAIL_REGEX.test(responsavel.email.trim())) erros.email = 'E-mail inválido.'
  const telefone = somenteDigitos(responsavel.telefone)
  if (telefone && telefone.length < 10) erros.telefone = 'Telefone incompleto.'
  return erros
}

/** Campo opcional vazio vai como null, para o backend não gravar string vazia. */
function opcional(valor: string): string | null {
  const limpo = valor.trim()
  return limpo ? limpo : null
}

function enderecoDe(campos: Campos): Endereco | null {
  const endereco: Endereco = {
    logradouro: opcional(campos.logradouro),
    numero: opcional(campos.numero),
    bairro: opcional(campos.bairro),
    cidade: opcional(campos.cidade),
    estado: opcional(campos.estado.toUpperCase()),
    cep: opcional(somenteDigitos(campos.cep)),
  }
  return Object.values(endereco).some((valor) => valor !== null) ? endereco : null
}

function paraRequest(campos: Campos, responsaveis: CamposResponsavel[]): AlunoRequest {
  return {
    nome: campos.nome.trim(),
    matricula: campos.matricula.trim(),
    cpf: somenteDigitos(campos.cpf),
    email: campos.email.trim(),
    dataNascimento: campos.dataNascimento || null,
    genero: opcional(campos.genero),
    endereco: enderecoDe(campos),
    responsaveis: responsaveis.map((r) => ({
      nome: r.nome.trim(),
      parentesco: opcional(r.parentesco),
      cpf: opcional(somenteDigitos(r.cpf)),
      email: opcional(r.email),
      telefone: opcional(somenteDigitos(r.telefone)),
    })),
  }
}

type AlunoModalProps = {
  /** Sem id é cadastro; com id, carrega o aluno e edita. */
  alunoId?: string
  onFechar: () => void
  /** Envia os dados; deve lançar ApiError em caso de falha. */
  onSalvar: (dados: AlunoRequest) => Promise<Aluno>
  onSalvo: (aluno: Aluno) => void
}

/**
 * Cadastro e edição de aluno pela secretaria. A escola não aparece: o backend
 * usa a do token. No cadastro o backend cria o login do aluno (o e-mail) e
 * envia a senha provisória; os responsáveis só entram no cadastro. Montado só
 * enquanto está aberto, então cada abertura começa limpa.
 */
export function AlunoModal({ alunoId, onFechar, onSalvar, onSalvo }: AlunoModalProps) {
  const edicao = alunoId !== undefined
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [campos, setCampos] = useState<Campos>(CAMPOS_INICIAIS)
  const [erros, setErros] = useState<Erros>({})
  const [responsaveis, setResponsaveis] = useState<CamposResponsavel[]>([])
  const [errosResponsaveis, setErrosResponsaveis] = useState<ErrosResponsavel[]>([])
  const [responsaveisAtuais, setResponsaveisAtuais] = useState<Responsavel[]>([])
  const [carregando, setCarregando] = useState(edicao)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  useEffect(() => {
    if (!alunoId) return
    const controller = new AbortController()
    buscarAluno(alunoId, controller.signal)
      .then((aluno) => {
        setCampos(camposDe(aluno))
        setResponsaveisAtuais(aluno.responsaveis)
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setErroCarga(e instanceof ApiError ? e.message : 'Erro inesperado ao carregar o aluno.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [alunoId])

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function alterarResponsavel(indice: number, campo: keyof CamposResponsavel, valor: string) {
    setResponsaveis((atuais) => atuais.map((r, i) => (i === indice ? { ...r, [campo]: valor } : r)))
    setErrosResponsaveis((atuais) => atuais.map((e, i) => (i === indice ? { ...e, [campo]: undefined } : e)))
  }

  function adicionarResponsavel() {
    setResponsaveis((atuais) => [...atuais, RESPONSAVEL_VAZIO])
    setErrosResponsaveis((atuais) => [...atuais, {}])
  }

  function removerResponsavel(indice: number) {
    setResponsaveis((atuais) => atuais.filter((_, i) => i !== indice))
    setErrosResponsaveis((atuais) => atuais.filter((_, i) => i !== indice))
  }

  function focar(nome: string) {
    dialogRef.current?.querySelector<HTMLElement>(`[name="${nome}"]`)?.focus()
  }

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErroEnvio(null)

    const novosErros = validar(campos)
    const novosErrosResponsaveis = responsaveis.map(validarResponsavel)
    setErros(novosErros)
    setErrosResponsaveis(novosErrosResponsaveis)
    const primeiroInvalido = Object.keys(novosErros)[0]
    if (primeiroInvalido) {
      focar(primeiroInvalido)
      return
    }
    const responsavelInvalido = novosErrosResponsaveis.findIndex((e) => Object.keys(e).length > 0)
    if (responsavelInvalido >= 0) {
      focar(`responsavel-${responsavelInvalido}-${Object.keys(novosErrosResponsaveis[responsavelInvalido])[0]}`)
      return
    }

    setSalvando(true)
    try {
      const aluno = await onSalvar(paraRequest(campos, edicao ? [] : responsaveis))
      onSalvo(aluno)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        // O 409 vem da matrícula repetida na escola ou do e-mail já usado como login.
        const campo = e.detalhe?.toLowerCase().includes('matricula') ? 'matricula' : 'email'
        setErros(
          campo === 'matricula'
            ? { matricula: 'Já existe um aluno com este número de matrícula.' }
            : { email: 'Este e-mail já é usado por outro usuário.' },
        )
        focar(campo)
      } else if (e instanceof ApiError && e.status === 400) {
        setErroEnvio('Os dados foram recusados pelo servidor. Revise os campos e tente novamente.')
      } else {
        setErroEnvio(e instanceof ApiError ? e.message : 'Erro inesperado ao salvar o aluno.')
      }
      setSalvando(false)
    }
  }

  const bloqueado = carregando || erroCarga !== null

  return (
    <Modal
      titulo={edicao ? 'Editar aluno' : 'Cadastrar novo aluno'}
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
            {salvando ? 'Salvando…' : edicao ? 'Salvar alterações' : 'Salvar aluno'}
          </Button>
        </>
      }
    >
      {carregando && <ModalAviso>Carregando dados do aluno…</ModalAviso>}
      {erroCarga && <ModalAlerta>{erroCarga}</ModalAlerta>}

      {!bloqueado && (
        <>
          <fieldset className={styles.secao}>
            <legend className={styles.legenda}>Dados do aluno</legend>
            <div className={styles.grid}>
              <Campo label="Nome completo" erro={erros.nome} obrigatorio className={styles.span4}>
                {(props) => (
                  <input
                    {...props}
                    name="nome"
                    maxLength={MAX_NOME}
                    autoComplete="off"
                    placeholder="Nome do aluno"
                    value={campos.nome}
                    onChange={(e) => alterar('nome', e.target.value)}
                  />
                )}
              </Campo>
              <Campo label="Matrícula" erro={erros.matricula} obrigatorio className={styles.span2}>
                {(props) => (
                  <input
                    {...props}
                    name="matricula"
                    maxLength={MAX_MATRICULA}
                    placeholder="Ex.: 2026-001"
                    value={campos.matricula}
                    onChange={(e) => alterar('matricula', e.target.value)}
                  />
                )}
              </Campo>

              <Campo
                label="CPF"
                erro={erros.cpf}
                obrigatorio
                dica={edicao ? undefined : 'É a senha do primeiro acesso.'}
                className={styles.span2}
              >
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
                dica={edicao ? 'Trocar o e-mail troca o login do aluno.' : 'O acesso ao Eduka é enviado para ele.'}
                className={styles.span4}
              >
                {(props) => (
                  <input
                    {...props}
                    name="email"
                    type="email"
                    maxLength={MAX_EMAIL}
                    autoComplete="off"
                    placeholder="aluno@email.com"
                    value={campos.email}
                    onChange={(e) => alterar('email', e.target.value)}
                  />
                )}
              </Campo>

              <Campo label="Data de nascimento" erro={erros.dataNascimento} className={styles.span3}>
                {(props) => (
                  <CampoData
                    {...props}
                    name="dataNascimento"
                    valor={campos.dataNascimento}
                    onChange={(data) => alterar('dataNascimento', data)}
                  />
                )}
              </Campo>
              <Campo label="Gênero" erro={erros.genero} className={styles.span3}>
                {(props) => (
                  <Select<string>
                    {...props}
                    name="genero"
                    opcoes={OPCOES_GENERO}
                    valor={campos.genero}
                    placeholder="Selecione"
                    onChange={(genero) => alterar('genero', genero)}
                  />
                )}
              </Campo>
            </div>
          </fieldset>

          <fieldset className={styles.secao}>
            <legend className={styles.legenda}>Endereço</legend>
            <div className={styles.grid}>
              <Campo label="CEP" erro={erros.cep} className={styles.span2}>
                {(props) => (
                  <input
                    {...props}
                    name="cep"
                    inputMode="numeric"
                    placeholder="00000-000"
                    value={campos.cep}
                    onChange={(e) => alterar('cep', mascararCep(e.target.value))}
                  />
                )}
              </Campo>
              <Campo label="Logradouro" erro={erros.logradouro} className={styles.span4}>
                {(props) => (
                  <input
                    {...props}
                    name="logradouro"
                    placeholder="Rua, avenida…"
                    value={campos.logradouro}
                    onChange={(e) => alterar('logradouro', e.target.value)}
                  />
                )}
              </Campo>
              <Campo label="Número" erro={erros.numero} className={styles.span2}>
                {(props) => (
                  <input
                    {...props}
                    name="numero"
                    value={campos.numero}
                    onChange={(e) => alterar('numero', e.target.value)}
                  />
                )}
              </Campo>
              <Campo label="Bairro" erro={erros.bairro} className={styles.span4}>
                {(props) => (
                  <input
                    {...props}
                    name="bairro"
                    value={campos.bairro}
                    onChange={(e) => alterar('bairro', e.target.value)}
                  />
                )}
              </Campo>
              <Campo label="Cidade" erro={erros.cidade} className={styles.span4}>
                {(props) => (
                  <input
                    {...props}
                    name="cidade"
                    value={campos.cidade}
                    onChange={(e) => alterar('cidade', e.target.value)}
                  />
                )}
              </Campo>
              <Campo label="UF" erro={erros.estado} className={styles.span2}>
                {(props) => (
                  <input
                    {...props}
                    name="estado"
                    maxLength={2}
                    placeholder="MG"
                    value={campos.estado}
                    onChange={(e) => alterar('estado', e.target.value.toUpperCase())}
                  />
                )}
              </Campo>
            </div>
          </fieldset>

          <fieldset className={styles.secao}>
            <legend className={styles.legenda}>Responsáveis</legend>
            {edicao ? (
              <>
                {responsaveisAtuais.length === 0 ? (
                  <ModalAviso>Nenhum responsável cadastrado.</ModalAviso>
                ) : (
                  <ul className={styles.responsaveis}>
                    {responsaveisAtuais.map((r) => (
                      <li key={r.pessoaId}>
                        <strong>{r.nome}</strong>
                        {r.parentesco && <span> · {r.parentesco}</span>}
                        {(r.email || r.telefone) && (
                          <span className={styles.contato}>
                            {[r.email, r.telefone && mascararTelefone(r.telefone)].filter(Boolean).join(' · ')}
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
                <ModalAviso>Os responsáveis ainda não podem ser alterados por aqui.</ModalAviso>
              </>
            ) : (
              <>
                {responsaveis.map((responsavel, indice) => {
                  const errosR = errosResponsaveis[indice] ?? {}
                  const nome = (campo: keyof CamposResponsavel) => `responsavel-${indice}-${campo}`
                  return (
                    <div key={indice} className={styles.responsavel}>
                      <div className={styles.responsavelTopo}>
                        <span>Responsável {indice + 1}</span>
                        <Button
                          size="sm"
                          icon="trash"
                          aria-label={`Remover responsável ${indice + 1}`}
                          onClick={() => removerResponsavel(indice)}
                        />
                      </div>
                      <div className={styles.grid}>
                        <Campo label="Nome" erro={errosR.nome} obrigatorio className={styles.span4}>
                          {(props) => (
                            <input
                              {...props}
                              name={nome('nome')}
                              maxLength={MAX_NOME}
                              value={responsavel.nome}
                              onChange={(e) => alterarResponsavel(indice, 'nome', e.target.value)}
                            />
                          )}
                        </Campo>
                        <Campo label="Parentesco" erro={errosR.parentesco} className={styles.span2}>
                          {(props) => (
                            <input
                              {...props}
                              name={nome('parentesco')}
                              placeholder="Ex.: Mãe"
                              value={responsavel.parentesco}
                              onChange={(e) => alterarResponsavel(indice, 'parentesco', e.target.value)}
                            />
                          )}
                        </Campo>
                        <Campo label="CPF" erro={errosR.cpf} className={styles.span2}>
                          {(props) => (
                            <input
                              {...props}
                              name={nome('cpf')}
                              inputMode="numeric"
                              placeholder="000.000.000-00"
                              value={responsavel.cpf}
                              onChange={(e) => alterarResponsavel(indice, 'cpf', mascararCpf(e.target.value))}
                            />
                          )}
                        </Campo>
                        <Campo label="E-mail" erro={errosR.email} className={styles.span2}>
                          {(props) => (
                            <input
                              {...props}
                              name={nome('email')}
                              type="email"
                              maxLength={MAX_EMAIL}
                              value={responsavel.email}
                              onChange={(e) => alterarResponsavel(indice, 'email', e.target.value)}
                            />
                          )}
                        </Campo>
                        <Campo label="Telefone" erro={errosR.telefone} className={styles.span2}>
                          {(props) => (
                            <input
                              {...props}
                              name={nome('telefone')}
                              type="tel"
                              placeholder="(00) 00000-0000"
                              value={responsavel.telefone}
                              onChange={(e) =>
                                alterarResponsavel(indice, 'telefone', mascararTelefone(e.target.value))
                              }
                            />
                          )}
                        </Campo>
                      </div>
                    </div>
                  )
                })}
                <div>
                  <Button size="sm" icon="plus" onClick={adicionarResponsavel}>
                    Adicionar responsável
                  </Button>
                </div>
              </>
            )}
          </fieldset>
        </>
      )}

      {erroEnvio && <ModalAlerta>{erroEnvio}</ModalAlerta>}
    </Modal>
  )
}
