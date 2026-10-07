import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { StatCard } from '../../../../components/StatCard/StatCard'
import { paths } from '../../../../routes/paths'
import { cadastrarAvaliacao, atualizarAvaliacao, excluirAvaliacao, listarAvaliacoes } from '../../../../services/portalProfessorService'
import type { AvaliacaoDoProfessor } from '../../../../types/avaliacaoProfessor'
import { ROTULOS_TIPO_AVALIACAO } from '../../../../types/portalAluno'
import { formatarPontos } from '../../../aluno/formatacao'
import { ConfirmarExclusao } from '../../../secretaria/components/PainelSecretaria/ConfirmarExclusao'
import { EstadoTurmas } from '../../components/EstadoTurmas/EstadoTurmas'
import { FormularioAvaliacao } from '../../components/FormularioAvaliacao/FormularioAvaliacao'
import { SeletorTurmaDisciplina } from '../../components/SeletorTurmaDisciplina/SeletorTurmaDisciplina'
import { useCarregamento } from '../../hooks/useCarregamento'
import { useTurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import { mensagemErroAvaliacao } from '../../notas'
import styles from './Avaliacoes.module.css'

const NENHUMA: AvaliacaoDoProfessor[] = []
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** Mesma ordem do backend: por data. */
function porData(a: AvaliacaoDoProfessor, b: AvaliacaoDoProfessor): number {
  return a.data.localeCompare(b.data)
}

/** Formulário aberto: cadastro (sem avaliação) ou edição. */
type Formulario = { aberto: false } | { aberto: true; avaliacao?: AvaliacaoDoProfessor }

export function Avaliacoes() {
  const navigate = useNavigate()
  const { opcoes, selecionada, selecionar, carregando: carregandoTurmas, erro: erroTurmas, recarregar: recarregarTurmas } =
    useTurmaDisciplina()
  const turmaId = selecionada?.turma.id
  const disciplinaId = selecionada?.disciplina.id

  const buscar = useCallback(
    (signal: AbortSignal) => listarAvaliacoes(turmaId as number, disciplinaId as number, signal),
    [turmaId, disciplinaId],
  )
  const { dados: avaliacoes, carregando, erro, recarregar, atualizar } = useCarregamento(
    selecionada ? buscar : null,
    NENHUMA,
    'Você não está mais alocado nesta turma/disciplina. Recarregue a página.',
  )

  const [formulario, setFormulario] = useState<Formulario>({ aberto: false })
  const [excluindo, setExcluindo] = useState<AvaliacaoDoProfessor | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)

  if (!selecionada) {
    return (
      <ProfessorLayout titulo="Avaliações" subtitulo="Provas, trabalhos e tarefas das suas turmas.">
        <EstadoTurmas carregando={carregandoTurmas} erro={erroTurmas} onTentarDeNovo={recarregarTurmas} />
      </ProfessorLayout>
    )
  }

  const contexto = `${selecionada.turma.nome} · ${selecionada.disciplina.nome}`
  const editando = formulario.aberto ? formulario.avaliacao : undefined
  const pontosDistribuidos = avaliacoes.reduce((soma, a) => soma + a.valor, 0)
  const comNotas = avaliacoes.filter((a) => a.notasLancadas > 0).length

  function trocar(chave: string) {
    setSucesso(null)
    selecionar(chave)
  }

  function abrir(avaliacao?: AvaliacaoDoProfessor) {
    setSucesso(null)
    setFormulario({ aberto: true, avaliacao })
  }

  function lancarNotas(avaliacao: AvaliacaoDoProfessor) {
    const params = new URLSearchParams({
      turma: String(avaliacao.turmaId),
      disciplina: String(avaliacao.disciplinaId),
      avaliacao: avaliacao.id,
    })
    navigate(`${paths.professor.notas}?${params}`)
  }

  return (
    <ProfessorLayout titulo="Avaliações" subtitulo="Provas, trabalhos e tarefas das suas turmas.">
      <div className={styles.toolbar}>
        <SeletorTurmaDisciplina opcoes={opcoes} selecionada={selecionada} onChange={trocar} />
        <Button variant="accent" icon="plus" onClick={() => abrir()}>
          Nova avaliação
        </Button>
      </div>

      {sucesso && (
        <p className={styles.sucesso} role="status">
          {sucesso}
        </p>
      )}

      {carregando && <div className={styles.mensagem}>Carregando as avaliações…</div>}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && avaliacoes.length === 0 && (
        <div className={styles.mensagem}>
          <strong>Nenhuma avaliação em {contexto}.</strong>
          <span>Cadastre a primeira prova, trabalho ou tarefa para lançar as notas da turma.</span>
          <Button size="sm" icon="plus" onClick={() => abrir()}>
            Nova avaliação
          </Button>
        </div>
      )}

      {!carregando && !erro && avaliacoes.length > 0 && (
        <>
          <section className={styles.stats} aria-label="Resumo das avaliações">
            <StatCard label="Avaliações" value={avaliacoes.length} descricao={contexto} destaque />
            <StatCard
              label="Pontos distribuídos"
              value={formatarPontos(pontosDistribuidos)}
              descricao="Somando o valor de todas"
            />
            <StatCard
              label="Com notas"
              value={`${comNotas} de ${avaliacoes.length}`}
              descricao={comNotas === avaliacoes.length ? 'Todas já têm nota' : 'Já têm alguma nota lançada'}
            />
          </section>

          <ul className={styles.lista} aria-label={`Avaliações de ${contexto}`}>
            {avaliacoes.map((avaliacao) => (
              <ItemAvaliacao
                key={avaliacao.id}
                avaliacao={avaliacao}
                onLancarNotas={() => lancarNotas(avaliacao)}
                onEditar={() => abrir(avaliacao)}
                onExcluir={() => {
                  setSucesso(null)
                  setExcluindo(avaliacao)
                }}
              />
            ))}
          </ul>
        </>
      )}

      {formulario.aberto && (
        <FormularioAvaliacao
          key={editando?.id ?? 'nova'}
          contexto={contexto}
          avaliacao={editando}
          onFechar={() => setFormulario({ aberto: false })}
          onSalvar={async (dados) => {
            const salva = editando
              ? await atualizarAvaliacao(editando.id, dados)
              : await cadastrarAvaliacao({ ...dados, turmaId: selecionada.turma.id, disciplinaId: selecionada.disciplina.id })
            atualizar((lista) => [...lista.filter((a) => a.id !== salva.id), salva].sort(porData))
            setFormulario({ aberto: false })
            setSucesso(editando ? `Avaliação "${salva.titulo}" atualizada.` : `Avaliação "${salva.titulo}" cadastrada.`)
          }}
        />
      )}

      {excluindo && (
        <ConfirmarExclusao
          titulo="Excluir avaliação"
          nome={excluindo.titulo}
          traduzirErro={mensagemErroAvaliacao}
          onFechar={() => setExcluindo(null)}
          onConfirmar={async () => {
            await excluirAvaliacao(excluindo.id)
            atualizar((lista) => lista.filter((a) => a.id !== excluindo.id))
            setExcluindo(null)
            setSucesso(`Avaliação "${excluindo.titulo}" excluída.`)
          }}
        />
      )}
    </ProfessorLayout>
  )
}

type ItemAvaliacaoProps = {
  avaliacao: AvaliacaoDoProfessor
  onLancarNotas: () => void
  onEditar: () => void
  onExcluir: () => void
}

function ItemAvaliacao({ avaliacao, onLancarNotas, onEditar, onExcluir }: ItemAvaliacaoProps) {
  // "AAAA-MM-DD" lido direto, sem Date, para o fuso não mudar o dia.
  const [, mes, dia] = avaliacao.data.split('-')
  const temNotas = avaliacao.notasLancadas > 0

  return (
    <li className={styles.item}>
      <div className={styles.data} aria-label={`Dia ${dia}/${mes}`}>
        <strong>{dia}</strong>
        <span>{MESES[Number(mes) - 1]}</span>
      </div>
      <div className={styles.corpo}>
        <span className={styles.tipo}>{ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]}</span>
        <strong className={styles.titulo}>{avaliacao.titulo}</strong>
        {avaliacao.descricao && <p className={styles.descricao}>{avaliacao.descricao}</p>}
      </div>
      <div className={styles.pontos}>
        <strong>{formatarPontos(avaliacao.valor)} pts</strong>
        <span>
          {temNotas
            ? `${avaliacao.notasLancadas} ${avaliacao.notasLancadas === 1 ? 'nota lançada' : 'notas lançadas'}`
            : 'Sem notas'}
        </span>
      </div>
      <div className={styles.acoes}>
        <Button size="sm" variant="primary" icon="check" onClick={onLancarNotas}>
          {temNotas ? 'Notas' : 'Lançar notas'}
        </Button>
        <Button size="sm" icon="edit" aria-label={`Editar ${avaliacao.titulo}`} title="Editar" onClick={onEditar} />
        <Button
          size="sm"
          icon="trash"
          className={styles.botaoExcluir}
          aria-label={`Excluir ${avaliacao.titulo}`}
          title={temNotas ? 'Tem notas lançadas: apague as notas antes de excluir' : 'Excluir'}
          disabled={temNotas}
          onClick={onExcluir}
        />
      </div>
    </li>
  )
}
