import { OPCOES_POR_PAGINA, type EstadoPaginacao } from '../../hooks/usePaginacao'
import { Button } from '../Button/Button'
import { Icon } from '../Icon/Icon'
import { Select } from '../Select/Select'
import styles from './Paginacao.module.css'

type PaginacaoProps = {
  paginacao: EstadoPaginacao<unknown>
  /** Rótulo acessível da navegação, ex.: "Páginas das cobranças". */
  rotulo: string
  /** Outra página está a caminho do backend: os botões esperam a resposta. */
  carregando?: boolean
}

const OPCOES = OPCOES_POR_PAGINA.map((n) => ({ valor: String(n), rotulo: String(n) }))

/**
 * Páginas com número: a primeira, a última e as vizinhas da atual. Entre
 * elas, `null` vira reticências; se só falta uma página no meio, ela entra.
 */
function paginasNumeradas(atual: number, total: number): (number | null)[] {
  const escolhidas = [...new Set([1, atual - 1, atual, atual + 1, total])]
    .filter((p) => p >= 1 && p <= total)
    .sort((a, b) => a - b)
  const resultado: (number | null)[] = []
  let anterior = 0
  for (const p of escolhidas) {
    if (p - anterior === 2) resultado.push(anterior + 1)
    else if (p - anterior > 2) resultado.push(null)
    resultado.push(p)
    anterior = p
  }
  return resultado
}

/**
 * "1–10 de 42", quantos por página, os números das páginas e Anterior e
 * Próxima. Some quando tudo cabe na menor página.
 */
export function Paginacao({ paginacao, rotulo, carregando = false }: PaginacaoProps) {
  const { pagina, totalPaginas, inicio, total, itensDaPagina, irPara, porPagina, mudarPorPagina } = paginacao
  // Até a menor opção, a lista cabe numa página com qualquer escolha: não há o que escolher.
  const podeEscolher = mudarPorPagina !== undefined && total > OPCOES_POR_PAGINA[0]
  if (totalPaginas <= 1 && !podeEscolher) return null

  return (
    <nav className={styles.paginacao} aria-label={rotulo} aria-busy={carregando}>
      <div className={styles.info}>
        <span className={styles.resumo}>
          {inicio + 1}–{inicio + itensDaPagina.length} de {total}
        </span>
        {podeEscolher && (
          <span className={styles.porPagina}>
            <span aria-hidden="true">Por página</span>
            <Select<string>
              aria-label="Itens por página"
              className={styles.seletor}
              opcoes={OPCOES}
              valor={String(porPagina)}
              onChange={(valor) => mudarPorPagina(Number(valor))}
            />
          </span>
        )}
      </div>
      {totalPaginas > 1 && (
        <div className={styles.botoes}>
          <Button
            variant="outline"
            size="sm"
            icon="chevronLeft"
            onClick={() => irPara(pagina - 1)}
            disabled={carregando || pagina === 1}
          >
            Anterior
          </Button>
          <span className={styles.numeros}>
            {paginasNumeradas(pagina, totalPaginas).map((p, i) =>
              p === null ? (
                <span key={`reticencias-${i}`} className={styles.reticencias} aria-hidden="true">
                  …
                </span>
              ) : (
                <Button
                  key={p}
                  variant={p === pagina ? 'primary' : 'outline'}
                  size="sm"
                  className={styles.numero}
                  aria-label={`Página ${p}`}
                  aria-current={p === pagina ? 'page' : undefined}
                  onClick={() => p !== pagina && irPara(p)}
                  disabled={carregando && p !== pagina}
                >
                  {p}
                </Button>
              ),
            )}
          </span>
          {/* No celular os números somem e fica só o texto. */}
          <span className={styles.atual}>
            Página {pagina} de {totalPaginas}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => irPara(pagina + 1)}
            disabled={carregando || pagina === totalPaginas}
          >
            Próxima
            <Icon name="chevronRight" size={16} />
          </Button>
        </div>
      )}
    </nav>
  )
}
