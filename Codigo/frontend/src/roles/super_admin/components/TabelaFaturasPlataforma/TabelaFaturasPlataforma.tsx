import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import estilos from '../../../../components/TabelaFaturas/TabelaFaturas.module.css'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import type { FaturaPlataforma, StatusFatura } from '../../../../types/faturaPlataforma'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'

const SITUACAO: Record<StatusFatura, { rotulo: string; tom: 'success' | 'danger' | 'neutral' | 'warning' }> = {
  PENDENTE: { rotulo: 'A pagar', tom: 'warning' },
  EM_ATRASO: { rotulo: 'Em atraso', tom: 'danger' },
  PAGA: { rotulo: 'Paga', tom: 'success' },
  CANCELADA: { rotulo: 'Cancelada', tom: 'neutral' },
}

/** A referência já traz o mês quando lançada por aqui; nas antigas, ele vem da competência. */
function referencia(fatura: FaturaPlataforma): string {
  const [ano, mes] = fatura.competencia.split('-')
  return fatura.descricao.includes(ano) ? fatura.descricao : `${fatura.descricao} · ${mes}/${ano}`
}

type TabelaFaturasPlataformaProps = {
  faturas: FaturaPlataforma[]
  /** Volta à primeira página quando muda (o filtro da tela). */
  reiniciarCom: string
  selecionadaId: string | null
  onSelecionar: (id: string) => void
  onVerComprovante: (fatura: FaturaPlataforma) => void
}

/** Lista da IU24. Só a fatura que o backend libera para cancelar pode ser selecionada. */
export function TabelaFaturasPlataforma({
  faturas,
  reiniciarCom,
  selecionadaId,
  onSelecionar,
  onVerComprovante,
}: TabelaFaturasPlataformaProps) {
  const { paginacao, topo } = usePaginacao<FaturaPlataforma, HTMLElement>(faturas, { reiniciarCom })
  return (
    <>
      <section ref={topo} className={estilos.cartao} aria-label="Faturas da plataforma">
        {faturas.length === 0 ? (
          <p className={estilos.vazio}>Nenhuma fatura nesta situação.</p>
        ) : (
          <div className={estilos.rolagem}>
            <table className={estilos.tabela}>
              <thead>
                <tr>
                  <th scope="col">
                    <span className={estilos.somenteLeitor}>Selecionar</span>
                  </th>
                  <th scope="col">Escola</th>
                  <th scope="col">Vence</th>
                  <th scope="col">Valor</th>
                  <th scope="col">Situação</th>
                  <th scope="col">
                    <span className={estilos.somenteLeitor}>Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {paginacao.itensDaPagina.map((f) => (
                  <tr key={f.id}>
                    <td>
                      <input
                        type="radio"
                        name="fatura-selecionada"
                        aria-label={`Selecionar fatura de ${f.escolaNome}, ${f.competencia}`}
                        disabled={!f.acoes.includes('CANCELAR')}
                        checked={selecionadaId === f.id}
                        onChange={() => onSelecionar(f.id)}
                      />
                    </td>
                    <td>
                      <span className={estilos.descricao}>{f.escolaNome}</span>
                      <span className={estilos.secundario}>{referencia(f)}</span>
                    </td>
                    <td className={estilos.nowrap}>{formatarData(f.dataVencimento)}</td>
                    <td className={estilos.nowrap}>
                      <strong>{formatarMoeda(f.pagamento?.valorPago ?? f.valor)}</strong>
                    </td>
                    <td>
                      <Badge tone={SITUACAO[f.status].tom}>{SITUACAO[f.status].rotulo}</Badge>
                    </td>
                    <td className={estilos.acao}>
                      {f.acoes.includes('VER_COMPROVANTE') && (
                        <Button size="sm" variant="outline" icon="eye" onClick={() => onVerComprovante(f)}>
                          Comprovante
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <Paginacao paginacao={paginacao} rotulo="Páginas das faturas" />
    </>
  )
}
