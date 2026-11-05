import { useCallback, useId, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { Modal } from '../../../../components/Modal/Modal'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paginacaoDoBackend, POR_PAGINA, usePaginaDoBackend, type PaginaDoBackend } from '../../../../hooks/usePaginacao'
import {
  atualizarAula,
  buscarAula,
  excluirAula,
  listarAulas,
  obterChamadaDoDia,
  registrarAula,
} from '../../../../services/portalProfessorService'
import type { Aula, AulaResumo, ChamadaDoDia, DadosAula } from '../../../../types/aula'
import { hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { EstadoTurmas } from '../../components/EstadoTurmas/EstadoTurmas'
import { FormularioChamada } from '../../components/FormularioChamada/FormularioChamada'
import { SeletorTurmaDisciplina } from '../../components/SeletorTurmaDisciplina/SeletorTurmaDisciplina'
import { useConfirmarSaida } from '../../hooks/useConfirmarSaida'
import { useTurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import styles from './Chamada.module.css'

const TITULO = 'Chamada'
const SUBTITULO = 'Registre a presença dos alunos e o conteúdo de cada aula.'

/** Valor de ?aula= para uma chamada nova num dia que já tem aula registrada. */
const NOVA_AULA = 'nova'
const MENSAGEM_404 = 'Você não está mais alocado nesta turma/disciplina. Recarregue a página.'

const DIA_VAZIO: ChamadaDoDia = { data: '', alunos: [], aulas: [] }
const SEM_AULA: Aula | null = null
const HISTORICO_VAZIO: PaginaDoBackend<AulaResumo> = {
  conteudo: [],
  pagina: 0,
  tamanho: POR_PAGINA,
  totalElementos: 0,
  totalPaginas: 0,
}

/** A data da URL, se for válida para uma aula; senão, hoje. */
function diaDaUrl(valor: string | null): string {
  const hoje = hojeIso()
  return valor && /^\d{4}-\d{2}-\d{2}$/.test(valor) && valor <= hoje ? valor : hoje
}

function resumoDaChamada(aula: { presentes: number; faltas: number }): string {
  return `${aula.presentes} ${plural(aula.presentes, 'presente', 'presentes')} e ${aula.faltas} ${plural(aula.faltas, 'falta', 'faltas')}`
}

/**
 * Chamada do professor (IU25): a aula do dia da turma/disciplina escolhida, com
 * o conteúdo e presente/falta de cada aluno, e o histórico de aulas embaixo.
 * Turma, disciplina, dia e aula ficam na URL.
 */
export function Chamada() {
  const [params, setParams] = useSearchParams()
  const { opcoes, selecionada, selecionar, carregando: carregandoTurmas, erro: erroTurmas, recarregar: recarregarTurmas } =
    useTurmaDisciplina()
  const turmaId = selecionada?.turma.id
  const disciplinaId = selecionada?.disciplina.id
  const data = diaDaUrl(params.get('data'))
  const aulaPedida = params.get('aula')

  const saida = useConfirmarSaida()
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [excluindo, setExcluindo] = useState<AulaResumo | null>(null)
  const campoDataId = useId()

  const buscarDia = useCallback(
    (signal: AbortSignal) => obterChamadaDoDia(turmaId as number, disciplinaId as number, data, signal),
    [turmaId, disciplinaId, data],
  )
  const dia = useCarregamento(selecionada ? buscarDia : null, DIA_VAZIO, MENSAGEM_404)

  const aulaAberta =
    aulaPedida === NOVA_AULA ? null : (dia.dados.aulas.find((a) => a.id === aulaPedida) ?? dia.dados.aulas[0] ?? null)
  const aulaAbertaId = aulaAberta?.id

  const buscarDetalhe = useCallback((signal: AbortSignal) => buscarAula(aulaAbertaId as string, signal), [aulaAbertaId])
  const detalhe = useCarregamento(aulaAbertaId ? buscarDetalhe : null, SEM_AULA, 'Esta aula não existe mais. Recarregue a página.')

  const { pagina, setPagina, porPagina, irPara, mudarPorPagina, topo } = usePaginaDoBackend<HTMLElement>()
  const buscarHistorico = useCallback(
    (signal: AbortSignal) => listarAulas(turmaId as number, disciplinaId as number, pagina, porPagina, signal),
    [turmaId, disciplinaId, pagina, porPagina],
  )
  const historico = useCarregamento(selecionada ? buscarHistorico : null, HISTORICO_VAZIO, MENSAGEM_404)
  const paginacao = paginacaoDoBackend(historico.dados, { irPara, mudarPorPagina })

  if (!selecionada) {
    return (
      <ProfessorLayout titulo={TITULO} subtitulo={SUBTITULO}>
        <EstadoTurmas carregando={carregandoTurmas} erro={erroTurmas} onTentarDeNovo={recarregarTurmas} />
      </ProfessorLayout>
    )
  }

  const turmaDisciplina = { turma: String(selecionada.turma.id), disciplina: String(selecionada.disciplina.id) }

  function abrir(novaData: string, aula?: string) {
    setParams({ ...turmaDisciplina, data: novaData, ...(aula ? { aula } : {}) }, { replace: true })
  }

  async function salvar(dados: DadosAula) {
    const salva = aulaAberta
      ? await atualizarAula(aulaAberta.id, dados)
      : await registrarAula({
          ...dados,
          turmaId: selecionada?.turma.id as number,
          disciplinaId: selecionada?.disciplina.id as number,
          data,
        })
    setSucesso(`Chamada de ${formatarData(salva.data)} salva: ${resumoDaChamada(salva)}.`)
    // Recarregar desmonta o formulário, que volta com o que o backend gravou.
    abrir(salva.data, salva.id)
    dia.recarregar()
    if (aulaAberta) detalhe.recarregar()
    historico.recarregar()
  }

  const carregandoAula = dia.carregando || detalhe.carregando
  const erroAula = dia.erro ?? detalhe.erro
  const semAlunos = !aulaAberta && dia.dados.alunos.length === 0

  return (
    <ProfessorLayout titulo={TITULO} subtitulo={SUBTITULO}>
      <div className={styles.toolbar}>
        <SeletorTurmaDisciplina
          opcoes={opcoes}
          selecionada={selecionada}
          onChange={(chave) =>
            saida.trocar(() => {
              setSucesso(null)
              setPagina(0)
              // Outra turma volta para a aula de hoje.
              selecionar(chave)
            })
          }
        />
        <div className={styles.dia}>
          <label htmlFor={campoDataId} className={styles.rotuloDia}>
            Dia da aula
          </label>
          <CampoData
            id={campoDataId}
            className={styles.campoData}
            valor={data}
            max={hojeIso()}
            onChange={(nova) =>
              saida.trocar(() => {
                setSucesso(null)
                abrir(nova)
              })
            }
          />
        </div>
      </div>

      {sucesso && (
        <p className={styles.sucesso} role="status">
          {sucesso}
        </p>
      )}

      {carregandoAula && (
        <div className={styles.mensagem} role="status">
          Carregando a chamada…
        </div>
      )}

      {!carregandoAula && erroAula && (
        <div className={styles.mensagem} role="alert">
          <span>{erroAula}</span>
          <Button
            size="sm"
            onClick={() => {
              dia.recarregar()
              detalhe.recarregar()
            }}
          >
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregandoAula && !erroAula && semAlunos && (
        <div className={styles.mensagem}>
          <strong>Nenhum aluno para a chamada de {formatarData(data)}.</strong>
          <span>Entram na chamada os alunos com matrícula ativa na turma, feita até o dia da aula.</span>
          {dia.dados.aulas.length > 0 && (
            <Button size="sm" onClick={() => abrir(data)}>
              Voltar para a aula registrada
            </Button>
          )}
        </div>
      )}

      {!carregandoAula && !erroAula && !semAlunos && (
        <FormularioChamada
          key={aulaAberta?.id ?? `nova-${turmaDisciplina.turma}-${turmaDisciplina.disciplina}-${data}`}
          data={data}
          alunos={dia.dados.alunos}
          aula={aulaAberta ? detalhe.dados : null}
          onPendente={saida.setPendente}
          onSalvar={salvar}
          onCancelar={
            !aulaAberta && dia.dados.aulas.length > 0 ? () => saida.trocar(() => abrir(data)) : undefined
          }
          onOutraAula={
            aulaAberta
              ? () =>
                  saida.trocar(() => {
                    setSucesso(null)
                    abrir(data, NOVA_AULA)
                  })
              : undefined
          }
          onExcluir={
            aulaAberta
              ? () => {
                  setSucesso(null)
                  setExcluindo(aulaAberta)
                }
              : undefined
          }
        />
      )}

      <section ref={topo} className={styles.historico} aria-labelledby="titulo-historico-aulas">
        <h2 id="titulo-historico-aulas" className={styles.tituloHistorico}>
          Histórico de aulas
        </h2>

        {historico.carregando && <p role="status">Carregando o histórico…</p>}

        {!historico.carregando && historico.erro && (
          <div className={styles.erroHistorico} role="alert">
            <span>{historico.erro}</span>
            <Button size="sm" onClick={historico.recarregar}>
              Tentar novamente
            </Button>
          </div>
        )}

        {!historico.carregando && !historico.erro && historico.dados.conteudo.length === 0 && (
          <p className={styles.semAulas}>Nenhuma aula registrada nesta turma ainda.</p>
        )}

        {!historico.carregando && !historico.erro && historico.dados.conteudo.length > 0 && (
          <>
            <div className={styles.rolagem}>
              <table className={styles.tabela}>
                <thead>
                  <tr>
                    <th scope="col">Data</th>
                    <th scope="col">Conteúdo</th>
                    <th scope="col" className={styles.numero}>
                      Presentes
                    </th>
                    <th scope="col" className={styles.numero}>
                      Faltas
                    </th>
                    <th scope="col" className={styles.acao}>
                      <span className={styles.somenteLeitor}>Ações</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {historico.dados.conteudo.map((aula) => (
                    <tr
                      key={aula.id}
                      className={aula.id === aulaAbertaId ? styles.aberta : undefined}
                      aria-current={aula.id === aulaAbertaId || undefined}
                    >
                      <td data-rotulo="Data" className={styles.data}>{formatarData(aula.data)}</td>
                      <td className={styles.conteudo}>{aula.conteudo}</td>
                      <td data-rotulo="Presentes" className={styles.numero}>{aula.presentes}</td>
                      <td data-rotulo="Faltas" className={styles.numero}>{aula.faltas}</td>
                      <td className={styles.acao}>
                        <Button
                          size="sm"
                          icon="eye"
                          aria-label={`Abrir a aula de ${formatarData(aula.data)}`}
                          disabled={aula.id === aulaAbertaId}
                          onClick={() =>
                            saida.trocar(() => {
                              setSucesso(null)
                              abrir(aula.data, aula.id)
                              window.scrollTo({ top: 0, behavior: 'smooth' })
                            })
                          }
                        >
                          Abrir
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacao paginacao={paginacao} rotulo="Páginas do histórico de aulas" />
          </>
        )}
      </section>

      {excluindo && (
        <ConfirmarExclusao
          titulo="Excluir aula"
          nome={`a aula de ${formatarData(excluindo.data)}`}
          onFechar={() => setExcluindo(null)}
          onConfirmar={async () => {
            await excluirAula(excluindo.id)
            setExcluindo(null)
            saida.setPendente(false)
            setSucesso(`Aula de ${formatarData(excluindo.data)} excluída. As faltas dela deixaram de contar no boletim.`)
            abrir(excluindo.data)
            // A página atual do histórico pode ter acabado com esta exclusão.
            setPagina(0)
            dia.recarregar()
            historico.recarregar()
          }}
        />
      )}

      {saida.confirmando && (
        <Modal
          titulo="Sair sem salvar?"
          tamanho="pequeno"
          onFechar={saida.continuarEditando}
          rodape={
            <>
              <Button onClick={saida.continuarEditando}>Continuar editando</Button>
              <Button variant="danger" onClick={saida.descartar}>
                Sair sem salvar
              </Button>
            </>
          }
        >
          <p className={styles.avisoSaida}>A chamada tem alterações que ainda não foram salvas e elas serão perdidas.</p>
        </Modal>
      )}
    </ProfessorLayout>
  )
}
