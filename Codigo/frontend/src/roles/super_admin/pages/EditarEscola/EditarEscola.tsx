import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../components/formulario/CampoData/CampoData'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { Select } from '../../../../components/formulario/Select/Select'
import { Switch } from '../../../../components/ui/Switch/Switch'
import { paths } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import { atualizarEscola, buscarEscola } from '../../../../services/escola/escolaService'
import {
  MODULOS_EM_BREVE,
  MODULOS_ESCOLA,
  OPCOES_PLANO,
  ROTULOS_MODULO,
  type Escola,
  type EscolaUpdateRequest,
  type ModuloEscola,
  type PlanoEscola,
} from '../../../../types/escola/escola'
import {
  cnpjValido,
  mascararCnpj,
  mascararInteiro,
  mascararTelefone,
  somenteDigitos,
} from '../../../../utils/formatters'
import { CamposEndereco } from '../../components/escolas/CamposEndereco/CamposEndereco'
import {
  enderecoDosValores,
  validarEndereco,
  valoresDoEndereco,
  type ValoresEndereco,
} from '../../components/escolas/CamposEndereco/endereco'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import styles from './EditarEscola.module.css'

/** Estado de navegação lido pelo PainelEscolas ao voltar desta tela. */
export type EstadoPainelEscolas = { sucesso?: string }

/**
 * Edição de escola (IU03), exclusiva do SUPER_ADMIN. Carrega a escola pelo id
 * da rota e só monta o formulário depois, para ele já nascer preenchido.
 */
export function EditarEscola() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [escola, setEscola] = useState<Escola | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    buscarEscola(id, controller.signal)
      .then(setEscola)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        if (e instanceof ApiError && (e.status === 404 || e.status === 400)) {
          setErro('Escola não encontrada.')
        } else {
          setErro(e instanceof ApiError ? e.message : 'Erro inesperado ao carregar a escola.')
        }
      })

    return () => controller.abort()
  }, [id, tentativa])

  function voltarAoPainel(estado?: EstadoPainelEscolas) {
    navigate(paths.superAdmin.escolas, { state: estado })
  }

  function tentarNovamente() {
    setErro(null)
    setTentativa((t) => t + 1)
  }

  return (
    <SuperAdminLayout
      titulo="Editar escola"
      subtitulo={escola?.nome}
      voltar={
        <Link to={paths.superAdmin.escolas} className={styles.voltar}>
          <Icon name="arrowLeft" size={16} />
          Voltar para o painel de escolas
        </Link>
      }
    >

      {erro && (
        <div className={styles.alerta} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={tentarNovamente}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!erro && !escola && <p className={styles.carregando} role="status">Carregando escola…</p>}

      {escola && (
        <FormularioEdicao
          escola={escola}
          onCancelar={() => voltarAoPainel()}
          onSalva={(salva) => voltarAoPainel({ sucesso: `"${salva.nome}" foi atualizada com sucesso.` })}
        />
      )}
    </SuperAdminLayout>
  )
}

type Campos = ValoresEndereco & {
  nome: string
  cnpj: string
  email: string
  telefone: string
  plano: PlanoEscola | ''
  ativo: boolean
  /** AAAA-MM-DD, como o input date entrega. Só leitura: definido no cadastro. */
  inicioVigencia: string
  fimVigencia: string
  /** Com separador de milhar, ex.: "1.500". */
  limiteUsuarios: string
  modulos: ModuloEscola[]
}

type Erros = Partial<Record<keyof Campos, string>>

// Limites das colunas de ESCOLA no banco.
const MAX_NOME = 150
const MAX_EMAIL = 150
// Cabe folgado no INTEGER da coluna LIMITE_USUARIOS.
const MAX_DIGITOS_LIMITE = 7

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function camposDaEscola(escola: Escola): Campos {
  return {
    ...valoresDoEndereco(escola.endereco),
    nome: escola.nome,
    cnpj: mascararCnpj(escola.cnpjCpf ?? ''),
    email: escola.email ?? '',
    telefone: mascararTelefone(escola.telefone ?? ''),
    plano: escola.plano ?? '',
    ativo: escola.ativo,
    inicioVigencia: escola.inicioVigencia ?? '',
    fimVigencia: escola.fimVigencia ?? '',
    limiteUsuarios: mascararInteiro(String(escola.limiteUsuarios ?? ''), MAX_DIGITOS_LIMITE),
    modulos: escola.modulos,
  }
}

function validar(campos: Campos, escola: Escola): Erros {
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

  // A API mantém o valor atual quando o campo vai vazio, então apagar um valor
  // já salvo não teria efeito: melhor avisar do que fingir que limpou.
  if (!campos.fimVigencia && escola.fimVigencia) {
    erros.fimVigencia = 'Informe o término da vigência.'
  } else if (campos.inicioVigencia && campos.fimVigencia && campos.fimVigencia < campos.inicioVigencia) {
    erros.fimVigencia = 'O término deve ser igual ou posterior ao início.'
  }

  if (!campos.limiteUsuarios && escola.limiteUsuarios !== null) {
    erros.limiteUsuarios = 'Informe o limite de usuários.'
  }

  return erros
}

function paraRequest(campos: Campos): EscolaUpdateRequest {
  const limite = somenteDigitos(campos.limiteUsuarios)
  return {
    nome: campos.nome.trim(),
    cnpjCpf: somenteDigitos(campos.cnpj),
    email: campos.email.trim(),
    telefone: campos.telefone,
    endereco: enderecoDosValores(campos),
    // validar() já garante que um plano foi escolhido.
    plano: campos.plano as PlanoEscola,
    ativo: campos.ativo,
    // Sem inicioVigencia: o início é fixado no cadastro e a API não o altera.
    fimVigencia: campos.fimVigencia || undefined,
    limiteUsuarios: limite ? Number(limite) : undefined,
    modulos: MODULOS_ESCOLA.filter((m) => campos.modulos.includes(m)),
  }
}

type FormularioEdicaoProps = {
  escola: Escola
  onCancelar: () => void
  onSalva: (escola: Escola) => void
}

function FormularioEdicao({ escola, onCancelar, onSalva }: FormularioEdicaoProps) {
  const formRef = useRef<HTMLFormElement>(null)
  const idBase = useId()
  const [campos, setCampos] = useState<Campos>(() => camposDaEscola(escola))
  const [erros, setErros] = useState<Erros>({})
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function alternarModulo(modulo: ModuloEscola, habilitado: boolean) {
    alterar(
      'modulos',
      habilitado ? [...campos.modulos, modulo] : campos.modulos.filter((m) => m !== modulo),
    )
  }

  function focar(campo: string) {
    formRef.current?.querySelector<HTMLElement>(`[name="${campo}"]`)?.focus()
  }

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErroEnvio(null)

    const novosErros = validar(campos, escola)
    setErros(novosErros)
    const primeiroInvalido = Object.keys(novosErros)[0]
    if (primeiroInvalido) {
      focar(primeiroInvalido)
      return
    }

    setSalvando(true)
    try {
      onSalva(await atualizarEscola(escola.id, paraRequest(campos)))
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setErros({ cnpj: 'Já existe outra escola cadastrada com este CNPJ.' })
        focar('cnpj')
      } else if (e instanceof ApiError && e.status === 400) {
        setErroEnvio('Os dados foram recusados pelo servidor. Revise os campos e tente novamente.')
      } else {
        setErroEnvio(e instanceof ApiError ? e.message : 'Erro inesperado ao salvar a escola.')
      }
      setSalvando(false)
    }
  }

  return (
    <form ref={formRef} className={styles.form} noValidate onSubmit={handleSubmit}>
      <Cartao titulo="Dados básicos" className={styles.dados}>
        <div className={styles.grid}>
          <Campo label="Razão social" erro={erros.nome} obrigatorio>
            {(props) => (
              <input
                {...props}
                name="nome"
                maxLength={MAX_NOME}
                autoComplete="organization"
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
          <CamposEndereco valores={campos} erros={erros} onAlterar={alterar} />
        </div>
      </Cartao>

      <div className={styles.lateral}>
        <Cartao titulo="Assinatura">
          <div className={styles.grid}>
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
            <div className={styles.status}>
              <span className={styles.label} id={`${idBase}-status`}>
                Status da assinatura
              </span>
              <Switch
                name="ativo"
                aria-labelledby={`${idBase}-status`}
                checked={campos.ativo}
                onChange={(ativo) => alterar('ativo', ativo)}
                rotuloLigado="Ativa"
                rotuloDesligado="Suspensa"
              />
            </div>
            <Campo label="Início da vigência" dica="Definido no cadastro. Não pode ser alterado.">
              {(props) => (
                <CampoData {...props} name="inicioVigencia" somenteLeitura valor={campos.inicioVigencia} />
              )}
            </Campo>
            <Campo label="Término da vigência" erro={erros.fimVigencia}>
              {(props) => (
                <CampoData
                  {...props}
                  name="fimVigencia"
                  min={campos.inicioVigencia || undefined}
                  valor={campos.fimVigencia}
                  onChange={(data) => alterar('fimVigencia', data)}
                />
              )}
            </Campo>
          </div>
        </Cartao>

        <Cartao titulo="Configurações de acesso">
          <div className={styles.acesso}>
            <Campo label="Limite de usuários" erro={erros.limiteUsuarios}>
              {(props) => (
                <input
                  {...props}
                  name="limiteUsuarios"
                  inputMode="numeric"
                  placeholder="Ex.: 500"
                  value={campos.limiteUsuarios}
                  onChange={(e) =>
                    alterar('limiteUsuarios', mascararInteiro(e.target.value, MAX_DIGITOS_LIMITE))
                  }
                />
              )}
            </Campo>
            <fieldset className={styles.modulos}>
              <legend className={styles.label}>Módulos habilitados</legend>
              <div className={styles.modulosGrid}>
                {MODULOS_ESCOLA.map((modulo) => {
                  // Sem funcionalidade ainda: não se marca nem aparece marcado.
                  const emBreve = MODULOS_EM_BREVE.includes(modulo)
                  return (
                    <label key={modulo} className={`${styles.modulo} ${emBreve ? styles.moduloEmBreve : ''}`}>
                      <input
                        type="checkbox"
                        name="modulos"
                        value={modulo}
                        checked={!emBreve && campos.modulos.includes(modulo)}
                        disabled={emBreve}
                        onChange={(e) => alternarModulo(modulo, e.target.checked)}
                      />
                      <span className={styles.caixa} aria-hidden="true">
                        <Icon name="check" size={12} strokeWidth={3} />
                      </span>
                      {ROTULOS_MODULO[modulo]}
                      {emBreve && <span className={styles.emBreve}>Em breve</span>}
                    </label>
                  )
                })}
              </div>
            </fieldset>
          </div>
        </Cartao>
      </div>

      <div className={styles.acoes}>
        {erroEnvio && (
          <p className={styles.alertaEnvio} role="alert">
            {erroEnvio}
          </p>
        )}
        <div className={styles.botoes}>
          <Button disabled={salvando} onClick={onCancelar}>
            Cancelar
          </Button>
          <Button type="submit" variant="accent" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar alterações'}
          </Button>
        </div>
      </div>
    </form>
  )
}

function Cartao({ titulo, className, children }: { titulo: string; className?: string; children: ReactNode }) {
  const tituloId = useId()
  return (
    <section className={[styles.card, className].filter(Boolean).join(' ')} aria-labelledby={tituloId}>
      <h2 id={tituloId} className={styles.cardTitle}>
        {titulo}
      </h2>
      {children}
    </section>
  )
}
