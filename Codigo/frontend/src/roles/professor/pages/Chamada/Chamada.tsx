import { useCallback, useId, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { CampoData } from '../../../../components/formulario/CampoData/CampoData'
import { paraData, paraIso } from '../../../../components/formulario/CampoData/datas'
import { ConfirmarExclusao } from '../../../../components/modal/ConfirmarExclusao/ConfirmarExclusao'
import { Modal } from '../../../../components/modal/Modal/Modal'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import {
  atualizarAula,
  buscarAula,
  excluirAula,
  obterChamadaDoDia,
  registrarAula,
} from '../../../../services/portal/portalProfessorService'
import type { Aula, AulaResumo, ChamadaDoDia, DadosAula } from '../../../../types/academico/aula'
import { DIAS_SEMANA, diaDaSemanaJs, ordemDoDia, type DiaSemana, type HorarioDaDisciplina } from '../../../../types/academico/horario'
import { hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { EstadoTurmas } from '../../components/EstadoTurmas/EstadoTurmas'
import { FormularioChamada } from '../../components/FormularioChamada/FormularioChamada'
import { HistoricoDeAulas } from '../../components/HistoricoDeAulas/HistoricoDeAulas'
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

const DIA_VAZIO: ChamadaDoDia = { data: '', diaDeAula: false, alunos: [], aulas: [] }
const SEM_AULA: Aula | null = null

/** A data da URL, se for válida para uma aula (não futura); senão, null. */
function diaDaUrl(valor: string | null, hoje: string): string | null {
  return valor && /^\d{4}-\d{2}-\d{2}$/.test(valor) && valor <= hoje ? valor : null
}

/** Os dias de aula da disciplina na grade da turma, na ordem da semana. */
function diasDaGrade(horarios: HorarioDaDisciplina[]): DiaSemana[] {
  return DIAS_SEMANA.map((d) => d.dia).filter((d) => horarios.some((h) => h.diaSemana === d))
}

/** O último dia de aula da grade até hoje (hoje, se for); null sem grade. */
function ultimoDiaDeAula(horarios: HorarioDaDisciplina[], hoje: string): string | null {
  const diasJs = diasDaGrade(horarios).map(diaDaSemanaJs)
  if (diasJs.length === 0) return null
  const dia = paraData(hoje) as Date
  while (!diasJs.includes(dia.getDay())) dia.setDate(dia.getDate() - 1)
  return paraIso(dia)
}

/** "Seg, Qua e Sex", na ordem da semana. */
function listarDias(dias: string[]): string {
  return dias.length > 1 ? `${dias.slice(0, -1).join(', ')} e ${dias[dias.length - 1]}` : (dias[0] ?? '')
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
  const hoje = hojeIso()
  const ultimaAula = ultimoDiaDeAula(selecionada?.disciplina.horarios ?? [], hoje)
  // Sem dia na URL, abre no último dia de aula da grade (hoje, se for); sem grade, hoje.
  const data = diaDaUrl(params.get('data'), hoje) ?? ultimaAula ?? hoje
  const aulaPedida = params.get('aula')

  const saida = useConfirmarSaida()
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [excluindo, setExcluindo] = useState<AulaResumo | null>(null)
  // Muda depois de salvar ou excluir, para o histórico ler de novo.
  const [versaoHistorico, setVersaoHistorico] = useState(0)
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

  if (!selecionada) {
    return (
      <ProfessorLayout titulo={TITULO} subtitulo={SUBTITULO}>
        <EstadoTurmas carregando={carregandoTurmas} erro={erroTurmas} onTentarDeNovo={recarregarTurmas} />
      </ProfessorLayout>
    )
  }

  const turmaDisciplina = { turma: String(selecionada.turma.id), disciplina: String(selecionada.disciplina.id) }

  // No calendário do dia da aula, só os dias de aula da grade podem ser escolhidos.
  const diasDeAula = diasDaGrade(selecionada.disciplina.horarios)
  const nomesDosDias = listarDias(diasDeAula.map((d) => DIAS_SEMANA[ordemDoDia(d)].curto))
  const destaqueDoCalendario = {
    diasDaSemana: diasDeAula.map(diaDaSemanaJs),
    somenteEles: true,
    legenda:
      diasDeAula.length > 0
        ? `Dias de aula de ${selecionada.disciplina.nome} no ${selecionada.turma.nome}: ${nomesDosDias}.`
        : `A grade do ${selecionada.turma.nome} ainda não tem horário de ${selecionada.disciplina.nome}.`,
  }

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
    setVersaoHistorico((v) => v + 1)
  }

  const carregandoAula = dia.carregando || detalhe.carregando
  const erroAula = dia.erro ?? detalhe.erro
  // Aula nova só em dia de aula da grade; as já registradas abrem em qualquer dia.
  const semAulaNoDia = !aulaAberta && !dia.dados.diaDeAula
  const semAlunos = !aulaAberta && !semAulaNoDia && dia.dados.alunos.length === 0

  return (
    <ProfessorLayout titulo={TITULO} subtitulo={SUBTITULO}>
      <div className={styles.toolbar}>
        <SeletorTurmaDisciplina
          opcoes={opcoes}
          selecionada={selecionada}
          onChange={(chave) =>
            saida.trocar(() => {
              setSucesso(null)
              // Outra turma volta para a aula de hoje (e o histórico, para a primeira página).
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
            max={hoje}
            destaque={destaqueDoCalendario}
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

      {!carregandoAula && !erroAula && semAulaNoDia && (
        <div className={styles.mensagem}>
          {diasDeAula.length > 0 ? (
            <>
              <strong>
                Não há aula de {selecionada.disciplina.nome} no {selecionada.turma.nome} em {formatarData(data)}.
              </strong>
              <span>
                A chamada é feita nos dias de aula da grade: {nomesDosDias}.
              </span>
            </>
          ) : (
            <>
              <strong>
                A grade do {selecionada.turma.nome} ainda não tem horário de {selecionada.disciplina.nome}.
              </strong>
              <span>A chamada fica disponível nos dias de aula, depois que a secretaria montar o horário.</span>
            </>
          )}
          {dia.dados.aulas.length > 0 ? (
            <Button size="sm" onClick={() => abrir(data)}>
              Voltar para a aula registrada
            </Button>
          ) : (
            ultimaAula &&
            ultimaAula !== data && (
              <Button size="sm" onClick={() => abrir(ultimaAula)}>
                Ir para a última aula ({formatarData(ultimaAula)})
              </Button>
            )
          )}
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

      {!carregandoAula && !erroAula && !semAulaNoDia && !semAlunos && (
        <FormularioChamada
          key={aulaAberta?.id ?? `nova-${turmaDisciplina.turma}-${turmaDisciplina.disciplina}-${data}`}
          data={data}
          turma={selecionada.turma.nome}
          disciplina={selecionada.disciplina.nome}
          alunos={dia.dados.alunos}
          aula={aulaAberta ? detalhe.dados : null}
          onPendente={saida.setPendente}
          onSalvar={salvar}
          onCancelar={
            !aulaAberta && dia.dados.aulas.length > 0 ? () => saida.trocar(() => abrir(data)) : undefined
          }
          onOutraAula={
            aulaAberta && dia.dados.diaDeAula
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

      <HistoricoDeAulas
        turmaId={selecionada.turma.id}
        disciplinaId={selecionada.disciplina.id}
        aulaAbertaId={aulaAbertaId}
        versao={versaoHistorico}
        onAbrir={(aula) =>
          saida.trocar(() => {
            setSucesso(null)
            abrir(aula.data, aula.id)
            window.scrollTo({ top: 0, behavior: 'smooth' })
          })
        }
      />

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
            dia.recarregar()
            setVersaoHistorico((v) => v + 1)
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
