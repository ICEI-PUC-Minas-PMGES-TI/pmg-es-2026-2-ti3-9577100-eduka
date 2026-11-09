import { useEffect, useState, type KeyboardEvent } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import {
  atualizarResponsavel,
  atualizarVinculo,
  buscarResponsavel,
  desvincularResponsavel,
  incluirResponsavel,
  listarResponsaveisDoAluno,
} from '../../../../services/responsavelService'
import type { Endereco, Responsavel } from '../../../../types/aluno'
import { mascararCpf, mascararTelefone, somenteDigitos } from '../../../../utils/formatters'
import { CamposDoResponsavel } from './CamposDoResponsavel'
import {
  RESPONSAVEL_VAZIO,
  responsavelParaRequest,
  validarResponsavel,
  type CamposResponsavel,
  type ErrosResponsavel,
} from './camposResponsavel'
import styles from './ResponsaveisDoAluno.module.css'

const PREFIXO = 'responsavel-edicao'

function mensagemDe(erro: unknown, padrao: string): string {
  return erro instanceof ApiError ? erro.message : padrao
}

type ResponsaveisDoAlunoProps = {
  alunoId: string
}

/**
 * Responsáveis na edição do aluno. Incluir, editar e desvincular são gravados
 * na hora, sem depender do "Salvar alterações" do aluno. O responsável
 * financeiro é o pagador das cobranças no Asaas; sem ele, paga o próprio aluno.
 */
export function ResponsaveisDoAluno({ alunoId }: ResponsaveisDoAlunoProps) {
  const [responsaveis, setResponsaveis] = useState<Responsavel[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  /** Formulário aberto: 'novo', o responsável em edição, ou nenhum. */
  const [formulario, setFormulario] = useState<'novo' | Responsavel | null>(null)
  const [valores, setValores] = useState<CamposResponsavel>(RESPONSAVEL_VAZIO)
  const [erros, setErros] = useState<ErrosResponsavel>({})
  /** O PUT de /responsaveis substitui tudo: o endereço, que a tela não mostra, volta como veio. */
  const [endereco, setEndereco] = useState<Endereco | null>(null)
  const [carregandoDetalhe, setCarregandoDetalhe] = useState(false)

  /** Responsável com o desvínculo esperando confirmação. */
  const [confirmandoId, setConfirmandoId] = useState<string | null>(null)
  const [gravando, setGravando] = useState(false)
  const [erroAcao, setErroAcao] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listarResponsaveisDoAluno(alunoId, controller.signal)
      .then((lista) => {
        setResponsaveis(lista)
        setErroCarga(null)
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setErroCarga(mensagemDe(e, 'Erro inesperado ao carregar os responsáveis.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [alunoId, versao])

  // A lista não traz o CPF: na edição, os campos vêm do detalhe.
  const responsavelIdEmEdicao = formulario && formulario !== 'novo' ? formulario.responsavelId : null
  useEffect(() => {
    if (!responsavelIdEmEdicao) return
    const controller = new AbortController()
    buscarResponsavel(responsavelIdEmEdicao, controller.signal)
      .then((detalhe) => {
        setValores((atuais) => ({
          ...atuais,
          cpf: mascararCpf(detalhe.cpf ?? ''),
        }))
        setEndereco(detalhe.endereco)
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setFormulario(null)
        setErroAcao(mensagemDe(e, 'Erro inesperado ao carregar o responsável.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregandoDetalhe(false)
      })
    return () => controller.abort()
  }, [responsavelIdEmEdicao])

  function tentarCarregarDeNovo() {
    setCarregando(true)
    setErroCarga(null)
    setVersao((v) => v + 1)
  }

  function abrirInclusao() {
    setErroAcao(null)
    setConfirmandoId(null)
    setErros({})
    setValores(RESPONSAVEL_VAZIO)
    setFormulario('novo')
  }

  function abrirEdicao(responsavel: Responsavel) {
    setErroAcao(null)
    setConfirmandoId(null)
    setErros({})
    setValores({
      nome: responsavel.nome,
      parentesco: responsavel.parentesco ?? '',
      cpf: '',
      email: responsavel.email ?? '',
      telefone: mascararTelefone(responsavel.telefone ?? ''),
      financeiro: responsavel.financeiro,
    })
    setEndereco(null)
    setCarregandoDetalhe(true)
    setFormulario(responsavel)
  }

  function alterar<K extends keyof CamposResponsavel>(campo: K, valor: CamposResponsavel[K]) {
    setValores((atuais) => ({ ...atuais, [campo]: valor }))
    setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  async function salvar() {
    if (!formulario) return
    const edicao = formulario !== 'novo'
    setErroAcao(null)
    const novosErros = validarResponsavel(valores, edicao)
    setErros(novosErros)
    if (Object.keys(novosErros).length > 0) return

    const dados = responsavelParaRequest(valores)
    setGravando(true)
    try {
      if (formulario === 'novo') {
        await incluirResponsavel(alunoId, dados)
      } else {
        const responsavelId = formulario.responsavelId as string
        await atualizarResponsavel(responsavelId, {
          nome: dados.nome,
          cpf: somenteDigitos(valores.cpf),
          email: dados.email,
          telefone: dados.telefone,
          endereco,
        })
        await atualizarVinculo(alunoId, responsavelId, {
          parentesco: dados.parentesco,
          financeiro: dados.financeiro ?? false,
        })
      }
      setFormulario(null)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        if (edicao) setErros({ cpf: 'Já existe outro responsável com este CPF.' })
        else setErroAcao('Este responsável já está vinculado a este aluno.')
      } else {
        setErroAcao(mensagemDe(e, 'Erro inesperado ao salvar o responsável.'))
      }
    } finally {
      setGravando(false)
      // Marcar um financeiro tira a marca do anterior: a lista vem de novo do backend.
      setVersao((v) => v + 1)
    }
  }

  async function desvincular(responsavel: Responsavel) {
    setErroAcao(null)
    setGravando(true)
    try {
      await desvincularResponsavel(alunoId, responsavel.responsavelId as string)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setErroAcao(
          `${responsavel.nome} tem cobranças em aberto deste aluno. Troque o responsável delas antes de desvincular.`,
        )
      } else if (!(e instanceof ApiError && e.status === 404)) {
        // 404: já não estava vinculado; a lista recarregada mostra isso.
        setErroAcao(mensagemDe(e, 'Erro inesperado ao desvincular o responsável.'))
      }
    } finally {
      setGravando(false)
      setConfirmandoId(null)
      setVersao((v) => v + 1)
    }
  }

  /** Estes campos ficam dentro do formulário do aluno: Enter aqui não pode salvar o aluno. */
  function aoTeclar(evento: KeyboardEvent<HTMLDivElement>) {
    if (evento.key === 'Enter' && evento.target instanceof HTMLInputElement) {
      evento.preventDefault()
      void salvar()
    }
  }

  const edicao = formulario !== null && formulario !== 'novo'
  const temFinanceiro = responsaveis.some((r) => r.financeiro)

  if (carregando) return <ModalAviso>Carregando os responsáveis…</ModalAviso>

  if (erroCarga) {
    return (
      <div className={styles.erroCarga}>
        <ModalAlerta>{erroCarga}</ModalAlerta>
        <Button size="sm" onClick={tentarCarregarDeNovo}>
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <>
      <p className={styles.dica}>
        Alterações nos responsáveis são gravadas na hora, sem precisar de "Salvar alterações". PIX ou boleto já gerado
        continua em nome de quem era o pagador.
      </p>

      {responsaveis.length === 0 ? (
        <p className={styles.dica}>Nenhum responsável cadastrado.</p>
      ) : (
        <ul className={styles.lista}>
          {responsaveis.map((r) => (
            <li key={r.pessoaId} className={styles.item}>
              <span className={styles.itemTexto}>
                <span className={styles.itemNome}>
                  <strong>{r.nome}</strong>
                  {r.parentesco && <span> · {r.parentesco}</span>}
                  {r.financeiro && <Badge tone="success">Responsável financeiro</Badge>}
                </span>
                {(r.email || r.telefone) && (
                  <span className={styles.contato}>
                    {[r.email, r.telefone && mascararTelefone(r.telefone)].filter(Boolean).join(' · ')}
                  </span>
                )}
                {confirmandoId === r.pessoaId && (
                  <span className={styles.aviso} role="alert">
                    {r.financeiro
                      ? 'Sem responsável financeiro, as próximas cobranças saem em nome do aluno. Desvincular?'
                      : 'O cadastro do responsável continua na escola. Desvincular?'}
                  </span>
                )}
              </span>
              {r.responsavelId && (
                <span className={styles.itemAcoes}>
                  {confirmandoId === r.pessoaId ? (
                    <>
                      <Button size="sm" disabled={gravando} onClick={() => setConfirmandoId(null)}>
                        Manter
                      </Button>
                      <Button size="sm" variant="danger" disabled={gravando} onClick={() => void desvincular(r)}>
                        {gravando ? 'Desvinculando…' : 'Confirmar'}
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button
                        size="sm"
                        disabled={gravando}
                        aria-label={`Editar ${r.nome}`}
                        onClick={() => abrirEdicao(r)}
                      >
                        Editar
                      </Button>
                      <Button
                        size="sm"
                        variant="dangerOutline"
                        disabled={gravando}
                        aria-label={`Desvincular ${r.nome}`}
                        onClick={() => setConfirmandoId(r.pessoaId)}
                      >
                        Desvincular
                      </Button>
                    </>
                  )}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      {!temFinanceiro && <p className={styles.dica}>Sem responsável financeiro, as cobranças saem em nome do aluno.</p>}

      {formulario === null ? (
        <div>
          <Button size="sm" icon="plus" onClick={abrirInclusao}>
            Adicionar responsável
          </Button>
        </div>
      ) : (
        <div className={styles.formulario} onKeyDown={aoTeclar}>
          <strong className={styles.formularioTitulo}>{edicao ? `Editar ${valores.nome}` : 'Novo responsável'}</strong>
          {carregandoDetalhe ? (
            <ModalAviso>Carregando os dados do responsável…</ModalAviso>
          ) : (
            <>
              <CamposDoResponsavel
                valores={valores}
                erros={erros}
                prefixo={PREFIXO}
                dicaCpf={edicao ? undefined : 'Se já estiver cadastrado na escola, o aluno é ligado a esse responsável.'}
                onAlterar={alterar}
              />
              <div className={styles.formularioAcoes}>
                <Button size="sm" disabled={gravando} onClick={() => setFormulario(null)}>
                  Cancelar
                </Button>
                <Button size="sm" variant="accent" disabled={gravando} onClick={() => void salvar()}>
                  {gravando ? 'Salvando…' : edicao ? 'Salvar responsável' : 'Incluir responsável'}
                </Button>
              </div>
            </>
          )}
        </div>
      )}

      {erroAcao && <ModalAlerta>{erroAcao}</ModalAlerta>}
    </>
  )
}
