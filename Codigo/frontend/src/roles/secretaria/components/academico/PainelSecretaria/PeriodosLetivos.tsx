import { useState } from 'react'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { Campo } from '../../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../../components/formulario/CampoData/CampoData'
import { ModalDetalhes } from '../../../../../components/modal/ModalDetalhes/ModalDetalhes'
import type { PeriodoLetivo, PeriodoLetivoRequest } from '../../../../../types/academico/academico'
import { hojeIso } from '../../../../../utils/formatters'
import { AcoesLinha, CabecalhoAba, Tabela } from './comum'
import { ConfirmarExclusao } from '../../../../../components/modal/ConfirmarExclusao/ConfirmarExclusao'
import { mensagemErroAcademico } from './erros'
import { formatarData } from '../../../../../utils/formatacao'
import { ModalCadastro } from '../../../../../components/modal/ModalCadastro/ModalCadastro'
import styles from './PainelSecretaria.module.css'
import { situacao } from './situacaoPeriodo'

// Limite da coluna NOME (V14).
const MAX_NOME = 100


/** Dias corridos entre as datas ISO, contando o primeiro e o último. */
function duracaoEmDias(inicio: string, fim: string): number {
  const dia = 24 * 60 * 60 * 1000
  return Math.round((Date.parse(`${fim}T00:00:00Z`) - Date.parse(`${inicio}T00:00:00Z`)) / dia) + 1
}

type PeriodosLetivosProps = {
  periodos: PeriodoLetivo[]
  onCadastrar: (dados: PeriodoLetivoRequest) => Promise<PeriodoLetivo>
  onAtualizar: (id: number, dados: PeriodoLetivoRequest) => Promise<PeriodoLetivo>
  onExcluir: (id: number) => Promise<void>
  onEncerrar: (id: number) => Promise<PeriodoLetivo>
  onAbrirMatriculas: (id: number) => Promise<PeriodoLetivo>
}

/** Formulário aberto: cadastro (sem período) ou edição. */
type Formulario = { aberto: false } | { aberto: true; periodo?: PeriodoLetivo }

export function PeriodosLetivos({
  periodos,
  onCadastrar,
  onAtualizar,
  onExcluir,
  onEncerrar,
  onAbrirMatriculas,
}: PeriodosLetivosProps) {
  const [formulario, setFormulario] = useState<Formulario>({ aberto: false })
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [vendo, setVendo] = useState<PeriodoLetivo | null>(null)
  const [excluindo, setExcluindo] = useState<PeriodoLetivo | null>(null)
  const [encerrando, setEncerrando] = useState<PeriodoLetivo | null>(null)
  const [abrindo, setAbrindo] = useState<number | null>(null)
  const [erroAbrir, setErroAbrir] = useState<string | null>(null)

  async function abrirMatriculas(periodo: PeriodoLetivo) {
    setSucesso(null)
    setErroAbrir(null)
    setAbrindo(periodo.id)
    try {
      await onAbrirMatriculas(periodo.id)
      setSucesso(`Matrículas do período "${periodo.nome}" abertas.`)
    } catch (e) {
      setErroAbrir(mensagemErroAcademico(e))
    } finally {
      setAbrindo(null)
    }
  }
  const hoje = hojeIso()

  function abrir(periodo?: PeriodoLetivo) {
    setSucesso(null)
    setFormulario({ aberto: true, periodo })
  }

  const editando = formulario.aberto ? formulario.periodo : undefined

  return (
    <>
      <CabecalhoAba titulo="Períodos letivos" rotuloNovo="Novo período" onNovo={() => abrir()} sucesso={sucesso} />

      <Tabela
        colunas={['Nome', 'Início', 'Término', 'Situação', '']}
        vazio="Nenhum período letivo cadastrado ainda."
        rotuloPaginacao="Páginas dos períodos letivos"
        linhas={periodos.map((p) => {
          const { rotulo, tom, fase } = situacao(p, hoje)
          return (
            <tr key={p.id}>
              <td className={styles.destaque}>{p.nome}</td>
              <td>{formatarData(p.dataInicio)}</td>
              <td>{formatarData(p.dataFim)}</td>
              <td>
                <Badge tone={tom}>{rotulo}</Badge>
                {fase === 'planejado' && (
                  <span className={styles.situacaoMatriculas}>
                    {p.matriculasAbertas ? 'Matrículas abertas' : 'Matrículas fechadas'}
                  </span>
                )}
              </td>
              <td className={styles.acoes}>
                {fase === 'andamento' && (
                  <Button
                    size="sm"
                    icon="lock"
                    onClick={() => {
                      setSucesso(null)
                      setEncerrando(p)
                    }}
                  >
                    Encerrar
                  </Button>
                )}
                {fase === 'planejado' && !p.matriculasAbertas && (
                  <Button size="sm" variant="primary" disabled={abrindo === p.id} onClick={() => abrirMatriculas(p)}>
                    {abrindo === p.id ? 'Abrindo…' : 'Abrir matrículas'}
                  </Button>
                )}
                <AcoesLinha
                  nome={p.nome}
                  onVer={() => setVendo(p)}
                  onEditar={() => abrir(p)}
                  onExcluir={() => {
                    setSucesso(null)
                    setExcluindo(p)
                  }}
                />
              </td>
            </tr>
          )
        })}
      />

      {erroAbrir && (
        <p className={styles.erroPeriodo} role="alert">
          {erroAbrir}
        </p>
      )}

      {encerrando && (
        <ConfirmarExclusao
          traduzirErro={mensagemErroAcademico}
          titulo="Encerrar período letivo"
          nome={encerrando.nome}
          rotuloConfirmar="Encerrar período"
          iconeConfirmar="lock"
          onFechar={() => setEncerrando(null)}
          onConfirmar={async () => {
            await onEncerrar(encerrando.id)
            setEncerrando(null)
            setSucesso(`Período "${encerrando.nome}" encerrado.`)
          }}
        >
          Encerrar o período <strong>{encerrando.nome}</strong> agora? Ele deixa de receber matrícula nova; as matrículas
          e notas já feitas continuam.
        </ConfirmarExclusao>
      )}

      {vendo && (
        <ModalDetalhes
          titulo={vendo.nome}
          subtitulo={<Badge tone={situacao(vendo, hoje).tom}>{situacao(vendo, hoje).rotulo}</Badge>}
          onFechar={() => setVendo(null)}
          secoes={[
            {
              itens: [
                { rotulo: 'Início', valor: formatarData(vendo.dataInicio) },
                { rotulo: 'Término', valor: formatarData(vendo.dataFim) },
                { rotulo: 'Duração', valor: `${duracaoEmDias(vendo.dataInicio, vendo.dataFim)} dias` },
                { rotulo: 'Cadastro', valor: vendo.ativo ? 'Ativo' : 'Inativo' },
                ...(vendo.encerradoEm ? [{ rotulo: 'Encerrado em', valor: formatarData(vendo.encerradoEm) }] : []),
                ...(situacao(vendo, hoje).fase === 'planejado'
                  ? [{ rotulo: 'Matrículas', valor: vendo.matriculasAbertas ? 'Abertas' : 'Fechadas' }]
                  : []),
              ],
            },
          ]}
        />
      )}

      {formulario.aberto && (
        <FormularioPeriodo
          key={editando?.id ?? 'novo'}
          periodo={editando}
          onFechar={() => setFormulario({ aberto: false })}
          onSalvar={async (dados) => {
            const salvo = editando ? await onAtualizar(editando.id, dados) : await onCadastrar(dados)
            setFormulario({ aberto: false })
            setSucesso(editando ? `Período "${salvo.nome}" atualizado.` : `Período "${salvo.nome}" cadastrado.`)
          }}
        />
      )}

      {excluindo && (
        <ConfirmarExclusao
          traduzirErro={mensagemErroAcademico}
          titulo="Excluir período letivo"
          nome={excluindo.nome}
          onFechar={() => setExcluindo(null)}
          onConfirmar={async () => {
            await onExcluir(excluindo.id)
            setExcluindo(null)
            setSucesso(`Período "${excluindo.nome}" excluído.`)
          }}
        />
      )}
    </>
  )
}

type Campos = { nome: string; dataInicio: string; dataFim: string }
type Erros = Partial<Record<keyof Campos, string>>

function FormularioPeriodo({
  periodo,
  onFechar,
  onSalvar,
}: {
  /** Sem período é cadastro; com período, edição com os campos preenchidos. */
  periodo?: PeriodoLetivo
  onFechar: () => void
  onSalvar: (dados: PeriodoLetivoRequest) => Promise<void>
}) {
  const [campos, setCampos] = useState<Campos>({
    nome: periodo?.nome ?? '',
    dataInicio: periodo?.dataInicio ?? '',
    dataFim: periodo?.dataFim ?? '',
  })
  const [erros, setErros] = useState<Erros>({})

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.nome.trim()) novos.nome = 'Informe o nome do período.'
    if (!campos.dataInicio) novos.dataInicio = 'Informe a data de início.'
    if (!campos.dataFim) novos.dataFim = 'Informe a data de término.'
    else if (campos.dataInicio && campos.dataFim < campos.dataInicio) {
      novos.dataFim = 'O término não pode ser antes do início.'
    }
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  return (
    <ModalCadastro
      titulo={periodo ? 'Editar período letivo' : 'Novo período letivo'}
      rotuloSalvar={periodo ? 'Salvar alterações' : 'Salvar período'}
      onFechar={onFechar}
      validar={validar}
      traduzirErro={mensagemErroAcademico}
      onEnviar={() => onSalvar({ nome: campos.nome.trim(), dataInicio: campos.dataInicio, dataFim: campos.dataFim })}
    >
      <Campo label="Nome" erro={erros.nome} obrigatorio largo>
        {(props) => (
          <input
            {...props}
            name="nome"
            maxLength={MAX_NOME}
            placeholder="Ex.: 2026/1"
            value={campos.nome}
            onChange={(e) => alterar('nome', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Data de início" erro={erros.dataInicio} obrigatorio>
        {(props) => (
          <CampoData
            {...props}
            name="dataInicio"
            valor={campos.dataInicio}
            onChange={(data) => alterar('dataInicio', data)}
          />
        )}
      </Campo>
      <Campo label="Data de término" erro={erros.dataFim} obrigatorio>
        {(props) => (
          <CampoData
            {...props}
            name="dataFim"
            valor={campos.dataFim}
            min={campos.dataInicio || undefined}
            onChange={(data) => alterar('dataFim', data)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
