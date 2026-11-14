import { Badge } from '../../../../../components/ui/Badge/Badge'
import { ModalDetalhes } from '../../../../../components/modal/ModalDetalhes/ModalDetalhes'
import { ROTULOS_TURNO } from '../../../../../types/academico/alocacao'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS, type Matricula } from '../../../../../types/academico/matricula'
import { formatarData } from '../../../../../utils/formatacao'

/** "2026-10-06T14:03:00Z" -> "06/10/2026 às 11:03", no fuso do navegador. */
function formatarDataHora(iso: string): string {
  const data = new Date(iso)
  return `${data.toLocaleDateString('pt-BR')} às ${data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`
}

type DetalhesMatriculaModalProps = {
  matricula: Matricula
  onFechar: () => void
}

/** Tudo o que a listagem de matrículas já traz, em um só lugar. */
export function DetalhesMatriculaModal({ matricula: m, onFechar }: DetalhesMatriculaModalProps) {
  return (
    <ModalDetalhes
      titulo={m.alunoNome ?? 'Aluno'}
      subtitulo={
        <>
          <span>Nº {m.alunoNumeroMatricula}</span>
          <Badge tone={TOM_DO_STATUS[m.status]}>{ROTULOS_STATUS_MATRICULA[m.status]}</Badge>
        </>
      }
      onFechar={onFechar}
      secoes={[
        {
          titulo: 'Turma',
          itens: [
            { rotulo: 'Turma', valor: m.turmaNome },
            { rotulo: 'Curso', valor: m.cursoNome },
            { rotulo: 'Período letivo', valor: m.periodoLetivoNome },
            { rotulo: 'Turno', valor: ROTULOS_TURNO[m.turno] },
          ],
        },
        {
          titulo: 'Matrícula',
          itens: [
            { rotulo: 'Matriculado em', valor: formatarData(m.dataMatricula) },
            { rotulo: 'Situação', valor: ROTULOS_STATUS_MATRICULA[m.status] },
            { rotulo: 'Registrada no sistema', valor: formatarDataHora(m.criadoEm) },
            { rotulo: 'Última alteração', valor: m.atualizadoEm ? formatarDataHora(m.atualizadoEm) : 'Nenhuma' },
            { rotulo: 'Observação', valor: m.observacao, largo: true },
          ],
        },
      ]}
    />
  )
}
