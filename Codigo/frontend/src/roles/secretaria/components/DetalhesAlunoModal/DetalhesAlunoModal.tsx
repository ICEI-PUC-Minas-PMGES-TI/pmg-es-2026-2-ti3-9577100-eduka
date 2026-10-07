import { ModalDetalhes } from '../../../../components/ModalDetalhes/ModalDetalhes'
import { useDetalhe } from '../../../../hooks/useDetalhe'
import { buscarAluno } from '../../../../services/alunoService'
import type { AlunoResumo } from '../../../../types/aluno'
import { formatarCnpjCpf, formatarEndereco, mascararTelefone } from '../../../../utils/formatters'
import { formatarData } from '../PainelSecretaria/formatacao'

type DetalhesAlunoModalProps = {
  /** Da listagem: mostra nome e matrícula enquanto o detalhe carrega. */
  aluno: AlunoResumo
  onFechar: () => void
}

/** Dados completos do aluno (GET /alunos/{id}), com os responsáveis. */
export function DetalhesAlunoModal({ aluno, onFechar }: DetalhesAlunoModalProps) {
  const { dados, carregando, erro } = useDetalhe(buscarAluno, aluno.id)

  return (
    <ModalDetalhes
      titulo={dados?.nome ?? aluno.nome}
      subtitulo={`Matrícula ${dados?.matricula ?? aluno.matricula}`}
      carregando={carregando}
      erro={erro}
      onFechar={onFechar}
      secoes={
        dados
          ? [
              {
                titulo: 'Dados do aluno',
                itens: [
                  { rotulo: 'CPF', valor: dados.cpf && formatarCnpjCpf(dados.cpf) },
                  { rotulo: 'Data de nascimento', valor: dados.dataNascimento && formatarData(dados.dataNascimento) },
                  { rotulo: 'E-mail (login)', valor: dados.email },
                  { rotulo: 'Gênero', valor: dados.genero },
                  { rotulo: 'Endereço', valor: formatarEndereco(dados.endereco), largo: true },
                ],
              },
              ...(dados.responsaveis.length === 0
                ? [{ titulo: 'Responsáveis', itens: [{ rotulo: 'Responsável', valor: 'Nenhum cadastrado', largo: true }] }]
                : dados.responsaveis.map((r, i) => ({
                    titulo: dados.responsaveis.length > 1 ? `Responsável ${i + 1}` : 'Responsável',
                    itens: [
                      { rotulo: 'Nome', valor: r.nome },
                      { rotulo: 'Parentesco', valor: r.parentesco },
                      { rotulo: 'E-mail', valor: r.email },
                      { rotulo: 'Telefone', valor: r.telefone && mascararTelefone(r.telefone) },
                    ],
                  }))),
            ]
          : []
      }
    />
  )
}
