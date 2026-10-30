import type { EstadoPaginacao } from '../../hooks/usePaginacao'
import { Button } from '../Button/Button'
import { Icon } from '../Icon/Icon'
import styles from './Paginacao.module.css'

type PaginacaoProps = {
  paginacao: EstadoPaginacao<unknown>
  /** Rótulo acessível da navegação, ex.: "Páginas das cobranças". */
  rotulo: string
}

/** "1–10 de 42" e os botões Anterior e Próxima. Some quando tudo cabe numa página. */
export function Paginacao({ paginacao, rotulo }: PaginacaoProps) {
  const { pagina, totalPaginas, inicio, total, itensDaPagina, irPara } = paginacao
  if (totalPaginas <= 1) return null

  return (
    <nav className={styles.paginacao} aria-label={rotulo}>
      <span className={styles.resumo}>
        {inicio + 1}–{inicio + itensDaPagina.length} de {total}
      </span>
      <div className={styles.botoes}>
        <Button variant="outline" size="sm" icon="chevronLeft" onClick={() => irPara(pagina - 1)} disabled={pagina === 1}>
          Anterior
        </Button>
        <span className={styles.atual} aria-current="page">
          Página {pagina} de {totalPaginas}
        </span>
        <Button variant="outline" size="sm" onClick={() => irPara(pagina + 1)} disabled={pagina === totalPaginas}>
          Próxima
          <Icon name="chevronRight" size={16} />
        </Button>
      </div>
    </nav>
  )
}
