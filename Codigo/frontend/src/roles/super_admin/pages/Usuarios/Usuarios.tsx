import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Dica } from '../../../../components/ui/Dica/Dica'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { Paginacao } from '../../../../components/ui/Paginacao/Paginacao'
import { Select, type OpcaoSelect } from '../../../../components/formulario/Select/Select'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { useListaPaginada, type PaginaDoBackend } from '../../../../hooks/usePaginacao'
import { useTermoAdiado } from '../../../../hooks/useTermoAdiado'
import { ApiError } from '../../../../services/api'
import type { PerfilUsuario } from '../../../../services/acesso/authService'
import { listarEscolas } from '../../../../services/escola/escolaService'
import {
  ativarUsuarioDaPlataforma,
  inativarUsuarioDaPlataforma,
  listarUsuariosDaPlataforma,
  reenviarAcessoDaPlataforma,
} from '../../../../services/acesso/usuarioPlataformaService'
import {
  ROTULOS_PERFIL_PLATAFORMA,
  type FiltrosUsuariosPlataforma,
  type UsuarioPlataforma,
} from '../../../../types/acesso/usuarioPlataforma'
import type { Escola } from '../../../../types/escola/escola'
import { plural } from '../../../../utils/formatters'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import styles from './Usuarios.module.css'

const SEM_ESCOLAS: Escola[] = []

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

const OPCOES_PERFIL: OpcaoSelect<PerfilUsuario | ''>[] = [
  { valor: '', rotulo: 'Todos os perfis' },
  ...(Object.keys(ROTULOS_PERFIL_PLATAFORMA) as PerfilUsuario[]).map((valor) => ({
    valor,
    rotulo: ROTULOS_PERFIL_PLATAFORMA[valor],
  })),
]

const OPCOES_SITUACAO: OpcaoSelect<FiltrosUsuariosPlataforma['ativo']>[] = [
  { valor: '', rotulo: 'Ativos e inativos' },
  { valor: 'true', rotulo: 'Só ativos' },
  { valor: 'false', rotulo: 'Só inativos' },
]

type FiltrosSemBusca = Omit<FiltrosUsuariosPlataforma, 'busca'>

const SEM_FILTROS: FiltrosSemBusca = { escolaId: '', perfil: '', ativo: '' }

function opcoesDeEscola(escolas: Escola[]): OpcaoSelect<string>[] {
  return [
    { valor: '', rotulo: 'Todas as escolas' },
    ...[...escolas]
      .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
      .map((escola) => ({ valor: escola.id, rotulo: escola.nome })),
  ]
}

function mensagemDeErro(e: unknown, falha: string, reenvio: boolean): string {
  if (e instanceof ApiError && e.codigo === 'LIMITE_USUARIOS_ATINGIDO') return e.message
  // No reenvio, o 409 é de quem já trocou a senha provisória.
  if (reenvio && e instanceof ApiError && e.status === 409) {
    return `${falha} Ele já definiu a própria senha e entra normalmente; se esqueceu, use "Esqueceu sua senha?" no login.`
  }
  return `${falha}${e instanceof ApiError ? ` ${e.message}` : ''}`
}

/**
 * Usuários da plataforma (S5-14): os logins de todas as escolas, para o Super
 * Admin achar um acesso e ver se ele está ativo e quando entrou. Ele gere o
 * do gestor (MASTER), que é quem ele cria: inativar, reativar e reenviar o
 * primeiro acesso. Equipe, professores e alunos são geridos pela escola.
 */
export function Usuarios() {
  const [filtros, setFiltros] = useState<FiltrosSemBusca>(SEM_FILTROS)
  const [texto, setTexto] = useState('')
  const busca = useTermoAdiado(texto)
  const [ocupadoId, setOcupadoId] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  // O filtro por escola não pode travar a tela: sem a lista, ele só fica com "Todas as escolas".
  const { dados: escolas } = useCarregamento(listarEscolas, SEM_ESCOLAS, '')
  const opcoesEscola = useMemo(() => opcoesDeEscola(escolas), [escolas])

  // Filtro ou busca nova é outra consulta: o useListaPaginada volta sozinho para a primeira página.
  const buscar = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarUsuariosDaPlataforma({ ...filtros, busca }, pagina, tamanho, signal),
    [filtros, busca],
  )
  const { dados, paginacao, carregando, atualizando, erro, recarregar, topo } = useListaPaginada<
    PaginaDoBackend<UsuarioPlataforma>,
    HTMLElement
  >(buscar, 'Não foi possível carregar os usuários.')

  const filtrando = Object.values(filtros).some(Boolean) || busca !== ''

  function filtrar(mudanca: Partial<FiltrosSemBusca>) {
    setFiltros((atuais) => ({ ...atuais, ...mudanca }))
  }

  async function acao(
    usuario: UsuarioPlataforma,
    executar: () => Promise<unknown>,
    ok: string,
    falha: string,
    reenvio = false,
  ) {
    setSucesso(null)
    setErroAcao(null)
    setOcupadoId(usuario.id)
    try {
      await executar()
      setSucesso(ok)
      recarregar()
    } catch (e) {
      setErroAcao(mensagemDeErro(e, falha, reenvio))
      recarregar()
    } finally {
      setOcupadoId(null)
    }
  }

  function alternar(usuario: UsuarioPlataforma) {
    return acao(
      usuario,
      () => (usuario.ativo ? inativarUsuarioDaPlataforma(usuario.id) : ativarUsuarioDaPlataforma(usuario.id)),
      usuario.ativo
        ? `O acesso de ${usuario.login} foi inativado e não entra mais no Eduka.`
        : `O acesso de ${usuario.login} foi reativado.`,
      `Não foi possível ${usuario.ativo ? 'inativar' : 'reativar'} o acesso de ${usuario.login}.`,
    )
  }

  function reenviar(usuario: UsuarioPlataforma) {
    return acao(
      usuario,
      () => reenviarAcessoDaPlataforma(usuario.id),
      `O primeiro acesso foi reenviado para ${usuario.login}, com uma nova senha provisória.`,
      `Não foi possível reenviar o acesso de ${usuario.login}.`,
      true,
    )
  }

  return (
    <SuperAdminLayout
      titulo="Usuários"
      subtitulo={
        dados && !erro
          ? `${dados.totalElementos} ${plural(dados.totalElementos, 'login', 'logins')}${filtrando ? ' para os filtros' : ' na plataforma'}. Você gere o acesso do gestor de cada escola; os demais são geridos pela escola.`
          : 'Os logins de todas as escolas. Você gere o acesso do gestor de cada escola; os demais são geridos pela escola.'
      }
    >
      {sucesso && (
        <p role="status" className={styles.sucesso}>
          {sucesso}
        </p>
      )}
      {erroAcao && (
        <p role="alert" className={styles.alerta}>
          {erroAcao}
        </p>
      )}

      <section className={styles.filtros} aria-label="Filtros dos usuários">
        <label className={styles.busca}>
          <Icon name="search" size={17} />
          <input
            type="search"
            placeholder="Buscar pelo login (e-mail)"
            aria-label="Buscar pelo login (e-mail)"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
        </label>
        <Select<string>
          aria-label="Filtrar por escola"
          opcoes={opcoesEscola}
          valor={filtros.escolaId}
          onChange={(escolaId) => filtrar({ escolaId })}
          className={`${styles.filtro} ${filtros.escolaId ? styles.filtroAtivo : ''}`}
        />
        <Select<PerfilUsuario | ''>
          aria-label="Filtrar por perfil"
          opcoes={OPCOES_PERFIL}
          valor={filtros.perfil}
          onChange={(perfil) => filtrar({ perfil })}
          className={`${styles.filtro} ${filtros.perfil ? styles.filtroAtivo : ''}`}
        />
        <Select<FiltrosUsuariosPlataforma['ativo']>
          aria-label="Filtrar por situação"
          opcoes={OPCOES_SITUACAO}
          valor={filtros.ativo}
          onChange={(ativo) => filtrar({ ativo })}
          className={`${styles.filtro} ${filtros.ativo ? styles.filtroAtivo : ''}`}
        />
        {filtrando && (
          <Button
            size="sm"
            onClick={() => {
              setFiltros(SEM_FILTROS)
              setTexto('')
            }}
          >
            Limpar filtros
          </Button>
        )}
      </section>

      <section ref={topo} className={styles.card} aria-label="Usuários" aria-busy={carregando || atualizando}>
        {dados && dados.conteudo.length > 0 && (
          <div className={styles.rolagem}>
            <table className={styles.tabela}>
              <caption className={styles.somenteLeitor}>Logins da plataforma, em ordem de login</caption>
              <thead>
                <tr>
                  <th scope="col">Login</th>
                  <th scope="col">Escola</th>
                  <th scope="col">Último acesso</th>
                  <th scope="col">Situação</th>
                  <th scope="col" className={styles.colAcoes}>
                    Ações
                  </th>
                </tr>
              </thead>
              <tbody>
                {dados.conteudo.map((usuario) => (
                  <tr key={usuario.id}>
                    <td data-rotulo="Login" className={styles.colPrincipal}>
                      <span className={styles.quem}>
                        <strong>{usuario.login}</strong>
                        <span className={styles.secundario}>{ROTULOS_PERFIL_PLATAFORMA[usuario.perfil]}</span>
                      </span>
                    </td>
                    <td data-rotulo="Escola">{usuario.escolaNome ?? 'Plataforma'}</td>
                    <td data-rotulo="Último acesso" className={styles.quando}>
                      {usuario.ultimoAcessoEm ? dataEHora(usuario.ultimoAcessoEm) : 'Nunca entrou'}
                    </td>
                    <td data-rotulo="Situação">
                      <Situacao usuario={usuario} />
                    </td>
                    <td className={styles.colAcoes}>
                      {usuario.gerenciavel ? (
                        <div className={styles.acoes}>
                          {usuario.ativo && usuario.aguardandoPrimeiroAcesso && (
                            <Button
                              size="sm"
                              icon="mail"
                              disabled={ocupadoId === usuario.id}
                              onClick={() => reenviar(usuario)}
                            >
                              Reenviar acesso
                            </Button>
                          )}
                          <Dica texto={usuario.ativo ? 'O login para de funcionar; o cadastro continua' : null}>
                            <Button
                              size="sm"
                              className={styles.alternar}
                              icon={usuario.ativo ? 'pause' : 'check'}
                              disabled={ocupadoId === usuario.id}
                              onClick={() => alternar(usuario)}
                            >
                              {usuario.ativo ? 'Inativar' : 'Reativar'}
                            </Button>
                          </Dica>
                        </div>
                      ) : (
                        <span className={styles.secundario}>
                          {usuario.perfil === 'SUPER_ADMIN' ? '—' : 'Gerido pela escola'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {carregando && <Mensagem papel="status">Carregando os usuários…</Mensagem>}
        {erro && (
          <Mensagem papel="alert">
            <span>{erro}</span>
            <Button size="sm" onClick={recarregar}>
              Tentar novamente
            </Button>
          </Mensagem>
        )}
        {!erro && dados && dados.conteudo.length === 0 && (
          <Mensagem>{filtrando ? 'Nenhum login encontrado para os filtros.' : 'Nenhum login cadastrado ainda.'}</Mensagem>
        )}
      </section>

      {paginacao && !erro && <Paginacao paginacao={paginacao} rotulo="Páginas dos usuários" carregando={atualizando} />}
    </SuperAdminLayout>
  )
}

function Situacao({ usuario }: { usuario: UsuarioPlataforma }) {
  if (!usuario.ativo) return <Badge tone="neutral">Inativo</Badge>
  if (usuario.aguardandoPrimeiroAcesso) return <Badge tone="warning">Aguardando acesso</Badge>
  return (
    <Badge tone="success" icon="check">
      Ativo
    </Badge>
  )
}

function Mensagem({ children, papel }: { children: ReactNode; papel?: 'status' | 'alert' }) {
  return (
    <div className={styles.mensagem} role={papel}>
      {children}
    </div>
  )
}
