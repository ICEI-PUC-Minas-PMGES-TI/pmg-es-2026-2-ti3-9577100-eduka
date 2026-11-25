import { CartaoTurma } from '../../../../components/academico/CartaoTurma/CartaoTurma'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Carrossel } from '../../../../components/ui/Carrossel/Carrossel'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS } from '../../../../types/academico/matricula'
import type { TurmaDoAluno } from '../../../../types/portal/portalAluno'
import { formatarData } from '../../../../utils/formatacao'
import { plural } from '../../../../utils/formatters'
import styles from './CarrosselTurmas.module.css'

type CarrosselTurmasProps = {
  turmas: TurmaDoAluno[]
  carregando: boolean
  erro: string | null
  onTentarNovamente: () => void
}

/**
 * Turmas do aluno num carrossel (ativas primeiro, como o backend devolve): um
 * cartão por turma com a grade de disciplinas e os professores.
 */
export function CarrosselTurmas({ turmas, carregando, erro, onTentarNovamente }: CarrosselTurmasProps) {
  const total = turmas.length
  return (
    <Carrossel
      titulo="Minhas turmas"
      resumo={`${total} ${plural(total, 'turma', 'turmas')} com as disciplinas e os professores de cada uma.`}
      carregando={carregando}
      erro={erro}
      onTentarNovamente={onTentarNovamente}
      textoCarregando="Carregando suas turmas…"
      vazio={
        <>
          <Icon name="graduationCap" size={22} />
          <span>Você ainda não está matriculado em nenhuma turma.</span>
        </>
      }
      rotulos={{
        lista: 'Turmas',
        anterior: 'Turma anterior',
        proxima: 'Próxima turma',
        irPara: (nome) => `Ir para a turma ${nome}`,
      }}
      itens={turmas.map((t, i) => ({
        chave: t.matriculaId,
        rotulo: t.turmaNome,
        conteudo: <Cartao turma={t} posicao={total > 1 ? `${i + 1} de ${total}` : null} largo={total === 1} />,
      }))}
    />
  )
}

function Cartao({ turma: t, posicao, largo }: { turma: TurmaDoAluno; posicao: string | null; largo: boolean }) {
  const cargaTotal = t.disciplinas.reduce((soma, d) => soma + d.cargaHoraria, 0)
  const qtd = t.disciplinas.length

  return (
    <CartaoTurma
      nome={t.turmaNome}
      codigo={t.turmaCodigo}
      posicao={posicao}
      destaque={t.situacaoMatricula === 'ATIVA'}
      largo={largo}
      situacao={
        <Badge tone={TOM_DO_STATUS[t.situacaoMatricula]}>{ROTULOS_STATUS_MATRICULA[t.situacaoMatricula]}</Badge>
      }
      etiquetas={[
        { icone: 'graduationCap', texto: t.cursoNome },
        { icone: 'calendar', texto: `Período ${t.periodoLetivoNome}` },
        { icone: 'clock', texto: ROTULOS_TURNO[t.turno] },
        { icone: 'check', texto: `Matrícula em ${formatarData(t.dataMatricula)}` },
      ]}
      resumo={`${qtd} ${plural(qtd, 'disciplina', 'disciplinas')} · ${cargaTotal} h no total`}
      semDisciplinas="A grade desta turma ainda não tem disciplinas."
      disciplinas={t.disciplinas.map((d) => ({
        id: d.id,
        nome: d.nome,
        codigo: d.codigo,
        detalhe: (
          <>
            {d.professorNome ?? 'Professor a definir'} <span className={styles.semQuebra}>· {d.cargaHoraria} h</span>
          </>
        ),
      }))}
    />
  )
}
