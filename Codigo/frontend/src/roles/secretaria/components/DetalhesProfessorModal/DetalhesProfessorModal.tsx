import { Badge } from '../../../../components/Badge/Badge'
import { ModalDetalhes } from '../../../../components/ModalDetalhes/ModalDetalhes'
import { useDetalhe } from '../../../../hooks/useDetalhe'
import { listarAlocacoesDoProfessor } from '../../../../services/alocacaoService'
import { buscarProfessor } from '../../../../services/professorService'
import type { Alocacao } from '../../../../types/alocacao'
import { ROTULOS_VINCULO, type Professor } from '../../../../types/professor'
import { formatarCnpjCpf, mascararTelefone } from '../../../../utils/formatters'
import { formatarData } from '../PainelSecretaria/formatacao'

type DetalhesProfessorModalProps = {
  /** Da listagem: mostra nome e situação enquanto o detalhe carrega. */
  professor: Professor
  onFechar: () => void
}

/** "1º Ano A (2026/2): Matemática, Física" por turma. */
function turmasEDisciplinas(alocacoes: Alocacao[]): string {
  const porTurma = new Map<string, string[]>()
  for (const a of alocacoes) {
    const turma = `${a.turmaNome} (${a.periodoLetivoNome})`
    porTurma.set(turma, [...(porTurma.get(turma) ?? []), a.disciplinaNome])
  }
  return [...porTurma].map(([turma, disciplinas]) => `${turma}: ${disciplinas.join(', ')}`).join(' · ')
}

/** Dados completos do professor (GET /professores/{id}) e onde ele dá aula. */
export function DetalhesProfessorModal({ professor, onFechar }: DetalhesProfessorModalProps) {
  const { dados, carregando, erro } = useDetalhe(buscarProfessor, professor.id)
  const { dados: alocacoes, erro: erroAlocacoes } = useDetalhe(listarAlocacoesDoProfessor, professor.id)
  const atual = dados ?? professor

  return (
    <ModalDetalhes
      titulo={atual.nome}
      subtitulo={atual.ativo ? <Badge tone="success">Ativo</Badge> : <Badge tone="neutral">Inativo</Badge>}
      carregando={carregando}
      erro={erro}
      onFechar={onFechar}
      secoes={[
        {
          titulo: 'Dados pessoais',
          itens: [
            { rotulo: 'CPF', valor: atual.cpf && formatarCnpjCpf(atual.cpf) },
            { rotulo: 'E-mail (login)', valor: atual.email },
            { rotulo: 'Telefone', valor: atual.telefone && mascararTelefone(atual.telefone) },
          ],
        },
        {
          titulo: 'Vínculo com a escola',
          itens: [
            { rotulo: 'Vínculo', valor: atual.vinculo && ROTULOS_VINCULO[atual.vinculo] },
            { rotulo: 'Data de admissão', valor: atual.dataAdmissao && formatarData(atual.dataAdmissao) },
            { rotulo: 'Formação', valor: atual.formacao, largo: true },
            {
              rotulo: 'Turmas e disciplinas',
              valor: erroAlocacoes
                ? 'Não foi possível carregar'
                : alocacoes === null
                  ? 'Carregando…'
                  : alocacoes.length === 0
                    ? 'Sem alocação'
                    : turmasEDisciplinas(alocacoes),
              largo: true,
            },
          ],
        },
      ]}
    />
  )
}
