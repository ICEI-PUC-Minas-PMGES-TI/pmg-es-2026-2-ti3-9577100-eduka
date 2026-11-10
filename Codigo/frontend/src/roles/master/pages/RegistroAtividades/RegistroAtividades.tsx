import { useCallback, useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { CampoPeriodo, type Periodo } from '../../../../components/CampoPeriodo/CampoPeriodo'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { Select, type OpcaoSelect } from '../../../../components/Select/Select'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { useListaPaginada, type PaginaDoBackend } from '../../../../hooks/usePaginacao'
import { listarAtividades, listarAutoresDeAtividades } from '../../../../services/auditoriaService'
import {
  descreverAcao,
  descreverAutor,
  ROTULOS_ENTIDADE,
  ROTULOS_OPERACAO,
  ROTULOS_PERFIL,
  type Atividade,
  type AutorAtividade,
  type EntidadeAuditada,
  type FiltrosAtividades,
  type OperacaoAuditoria,
} from '../../../../types/auditoria'
import { hojeIso, plural } from '../../../../utils/formatters'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './RegistroAtividades.module.css'

const SEM_FILTROS: FiltrosAtividades = { usuarioId: '', de: '', ate: '', entidade: '', operacao: '' }
const SEM_AUTORES: AutorAtividade[] = []

// Os instantes do backend no fuso da escola: "09/11/2026 às 14:32".
const DATA_E_HORA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

function dataEHora(instante: string): string {
  return DATA_E_HORA.format(new Date(instante)).replace(', ', ' às ')
}

/** Opções de um enum por ordem alfabética do rótulo, com "todos" na frente. */
function opcoesDe<T extends string>(rotulos: Record<T, string>, todos: string): OpcaoSelect<T | ''>[] {
  const opcoes = (Object.keys(rotulos) as T[])
    .map((valor) => ({ valor, rotulo: rotulos[valor] }))
    .sort((a, b) => a.rotulo.localeCompare(b.rotulo, 'pt-BR'))
  return [{ valor: '', rotulo: todos }, ...opcoes]
}

const OPCOES_ENTIDADE = opcoesDe<EntidadeAuditada>(ROTULOS_ENTIDADE, 'Todas as entidades')
const OPCOES_OPERACAO = opcoesDe<OperacaoAuditoria>(ROTULOS_OPERACAO, 'Todas as operações')

function opcoesDePessoa(autores: AutorAtividade[]): OpcaoSelect<string>[] {
  return [
    { valor: '', rotulo: 'Todas as pessoas' },
    ...autores.map((a) => ({
      valor: a.usuarioId,
      rotulo: `${a.nome ?? 'Ex-integrante da escola'} · ${ROTULOS_PERFIL[a.perfil]}`,
    })),
  ]
}

/**
 * Registro de atividades (IU28, MASTER): quem fez o quê na escola e quando,
 * da mais recente para a mais antiga. Filtra por pessoa, período, entidade e
 * operação; a página vem cortada do backend.
 */
export function RegistroAtividades() {
  const [filtros, setFiltros] = useState<FiltrosAtividades>(SEM_FILTROS)
  // O filtro por pessoa não pode travar a tela: sem a lista, ele só fica com "Todas as pessoas".
  const { dados: autores } = useCarregamento(listarAutoresDeAtividades, SEM_AUTORES, '')
  // Filtro novo é outra busca: o useListaPaginada volta sozinho para a primeira página.
  const buscar = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) => listarAtividades(filtros, pagina, tamanho, signal),
    [filtros],
  )
  const { dados, paginacao, carregando, atualizando, erro, recarregar, topo } = useListaPaginada<
    PaginaDoBackend<Atividade>,
    HTMLElement
  >(buscar, 'Não foi possível carregar as atividades.')

  const filtrando = Object.values(filtros).some(Boolean)
  const periodo: Periodo | null = filtros.de && filtros.ate ? { de: filtros.de, ate: filtros.ate } : null

  function filtrar(mudanca: Partial<FiltrosAtividades>) {
    setFiltros((atuais) => ({ ...atuais, ...mudanca }))
  }

  return (
    <MasterLayout
      titulo="Registro de atividades"
      subtitulo={
        dados && !erro
          ? `${dados.totalElementos} ${plural(dados.totalElementos, 'atividade', 'atividades')}${filtrando ? ' para os filtros' : ''}. Quem fez o quê na escola, e quando.`
          : 'Quem fez o quê na escola, e quando.'
      }
    >
      <section className={styles.filtros} aria-label="Filtros das atividades">
        <Select<string>
          aria-label="Filtrar por pessoa"
          opcoes={opcoesDePessoa(autores)}
          valor={filtros.usuarioId}
          onChange={(usuarioId) => filtrar({ usuarioId })}
          className={`${styles.filtro} ${filtros.usuarioId ? styles.filtroAtivo : ''}`}
        />
        <CampoPeriodo
          valor={periodo}
          onChange={({ de, ate }) => filtrar({ de, ate })}
          placeholder="Qualquer período"
          max={hojeIso()}
          aria-pressed={periodo !== null}
          className={`${styles.filtro} ${periodo ? styles.filtroAtivo : ''}`}
        />
        <Select<EntidadeAuditada | ''>
          aria-label="Filtrar por entidade"
          opcoes={OPCOES_ENTIDADE}
          valor={filtros.entidade}
          onChange={(entidade) => filtrar({ entidade })}
          className={`${styles.filtro} ${filtros.entidade ? styles.filtroAtivo : ''}`}
        />
        <Select<OperacaoAuditoria | ''>
          aria-label="Filtrar por operação"
          opcoes={OPCOES_OPERACAO}
          valor={filtros.operacao}
          onChange={(operacao) => filtrar({ operacao })}
          className={`${styles.filtro} ${filtros.operacao ? styles.filtroAtivo : ''}`}
        />
        {filtrando && (
          <Button size="sm" onClick={() => filtrar(SEM_FILTROS)}>
            Limpar filtros
          </Button>
        )}
      </section>

      <section ref={topo} className={styles.card} aria-label="Atividades" aria-busy={carregando || atualizando}>
        {dados && dados.conteudo.length > 0 && (
          <div className={styles.rolagem}>
            <table className={styles.tabela}>
              <caption className={styles.somenteLeitor}>
                Atividades da escola, da mais recente para a mais antiga
              </caption>
              <thead>
                <tr>
                  <th scope="col">Quando</th>
                  <th scope="col">Quem</th>
                  <th scope="col">O que fez</th>
                  <th scope="col">Entidade</th>
                </tr>
              </thead>
              <tbody>
                {dados.conteudo.map((atividade) => (
                  <tr key={atividade.id}>
                    <td data-rotulo="Quando" className={styles.quando}>
                      {dataEHora(atividade.dataHora)}
                    </td>
                    <td data-rotulo="Quem" className={styles.colPrincipal}>
                      <span className={styles.quem}>
                        <strong>{descreverAutor(atividade.origem, atividade.usuarioNome)}</strong>
                        {atividade.usuarioPerfil && (
                          <span className={styles.secundario}>{ROTULOS_PERFIL[atividade.usuarioPerfil]}</span>
                        )}
                      </span>
                    </td>
                    <td data-rotulo="O que fez">{descreverAcao(atividade.operacao, atividade.entidade)}</td>
                    <td data-rotulo="Entidade">
                      <Badge tone="neutral">{ROTULOS_ENTIDADE[atividade.entidade]}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {carregando && <Mensagem papel="status">Carregando as atividades…</Mensagem>}
        {erro && (
          <Mensagem papel="alert">
            <span>{erro}</span>
            <Button size="sm" onClick={recarregar}>
              Tentar novamente
            </Button>
          </Mensagem>
        )}
        {!erro && dados && dados.conteudo.length === 0 && (
          <Mensagem>
            {filtrando
              ? 'Nenhuma atividade encontrada para os filtros.'
              : 'Nenhuma atividade registrada ainda. Cadastros, matrículas, notas e cobranças aparecem aqui.'}
          </Mensagem>
        )}
      </section>

      {paginacao && !erro && (
        <Paginacao paginacao={paginacao} rotulo="Páginas das atividades" carregando={atualizando} />
      )}
    </MasterLayout>
  )
}

function Mensagem({ children, papel }: { children: ReactNode; papel?: 'status' | 'alert' }) {
  return (
    <div className={styles.mensagem} role={papel}>
      {children}
    </div>
  )
}
