import { Badge } from '../../../../../components/ui/Badge/Badge'
import { ModalDetalhes } from '../../../../../components/modal/ModalDetalhes/ModalDetalhes'
import { ROTULOS_MODULO, ROTULOS_PLANO, modulosDisponiveis, type Escola } from '../../../../../types/escola/escola'
import { formatarCnpjCpf, formatarEndereco, mascararTelefone } from '../../../../../utils/formatters'

/** "AAAA-MM-DD" -> "dd/mm/aaaa", sem passar por Date (que mudaria o dia pelo fuso). */
function formatarData(iso: string | null): string | null {
  if (!iso) return null
  const [ano, mes, dia] = iso.slice(0, 10).split('-')
  return `${dia}/${mes}/${ano}`
}

type DetalhesEscolaModalProps = {
  escola: Escola
  onFechar: () => void
}

/** Cadastro, contato e assinatura da escola, como a listagem já traz. */
export function DetalhesEscolaModal({ escola, onFechar }: DetalhesEscolaModalProps) {
  return (
    <ModalDetalhes
      titulo={escola.nome}
      subtitulo={
        escola.ativo ? (
          <Badge tone="success" icon="check">
            Ativa
          </Badge>
        ) : (
          <Badge tone="danger">Suspensa</Badge>
        )
      }
      onFechar={onFechar}
      secoes={[
        {
          titulo: 'Cadastro',
          itens: [
            { rotulo: 'CNPJ/CPF', valor: escola.cnpjCpf && formatarCnpjCpf(escola.cnpjCpf) },
            { rotulo: 'Cadastrada em', valor: formatarData(escola.criadoEm) },
            { rotulo: 'E-mail', valor: escola.email },
            { rotulo: 'Telefone', valor: escola.telefone && mascararTelefone(escola.telefone) },
            { rotulo: 'Endereço', valor: formatarEndereco(escola.endereco), largo: true },
          ],
        },
        {
          titulo: 'Assinatura',
          itens: [
            { rotulo: 'Plano', valor: escola.plano && ROTULOS_PLANO[escola.plano] },
            { rotulo: 'Limite de usuários', valor: escola.limiteUsuarios ?? 'Sem limite' },
            { rotulo: 'Início da vigência', valor: formatarData(escola.inicioVigencia) },
            { rotulo: 'Fim da vigência', valor: formatarData(escola.fimVigencia) ?? 'Sem prazo' },
            {
              rotulo: 'Módulos',
              valor:
                modulosDisponiveis(escola.modulos).length === 0
                  ? 'Nenhum'
                  : modulosDisponiveis(escola.modulos)
                      .map((m) => ROTULOS_MODULO[m])
                      .join(', '),
              largo: true,
            },
          ],
        },
      ]}
    />
  )
}
