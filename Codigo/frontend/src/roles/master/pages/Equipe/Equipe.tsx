import { useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Dica } from '../../../../components/Dica/Dica'
import { Icon } from '../../../../components/Icon/Icon'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { Pilulas } from '../../../../components/PainelFinanceiro/PainelFinanceiro'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import { ApiError } from '../../../../services/api'
import { reenviarAcessoEquipe } from '../../../../services/equipeService'
import { ROTULOS_PERFIL_EQUIPE, type MembroEquipe, type MembroEquipeRequest, type PerfilEquipe } from '../../../../types/equipe'
import { iniciais, plural } from '../../../../utils/formatters'
import { CadastroEquipeModal } from '../../components/CadastroEquipeModal/CadastroEquipeModal'
import { useEquipe } from '../../hooks/useEquipe'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './Equipe.module.css'

type Filtro = 'TODOS' | PerfilEquipe

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'TODOS', rotulo: 'Todos' },
  { valor: 'SECRETARIA', rotulo: 'Secretaria' },
  { valor: 'FINANCEIRO', rotulo: 'Financeiro' },
]

function correspondeBusca(membro: MembroEquipe, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return membro.nome.toLowerCase().includes(termo) || (membro.email ?? '').toLowerCase().includes(termo)
}

/** A edição manda os mesmos campos, menos o perfil, que não muda. */
function semPerfil({ perfil: _perfil, ...dados }: MembroEquipeRequest) {
  return dados
}

/**
 * Equipe da escola (MASTER): quem trabalha como secretaria e financeiro.
 * Cadastrar manda o primeiro acesso por e-mail; inativar tira o login.
 */
export function Equipe() {
  const { equipe, carregando, erro, recarregar, cadastrar, atualizar, alternarSituacao } = useEquipe()
  const [busca, setBusca] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('TODOS')
  const [cadastroAberto, setCadastroAberto] = useState(false)
  const [editando, setEditando] = useState<MembroEquipe | null>(null)
  const [ocupadoId, setOcupadoId] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)

  const visiveis = equipe.filter((m) => (filtro === 'TODOS' || m.perfil === filtro) && correspondeBusca(m, busca))
  const ativos = (perfil: PerfilEquipe) => equipe.filter((m) => m.ativo && m.perfil === perfil).length
  const aguardando = equipe.filter((m) => m.ativo && m.aguardandoPrimeiroAcesso).length
  const { paginacao, topo } = usePaginacao<MembroEquipe, HTMLElement>(visiveis, { reiniciarCom: `${busca}|${filtro}` })

  function limparAvisos() {
    setSucesso(null)
    setErroAcao(null)
  }

  async function acao(membro: MembroEquipe, executar: () => Promise<unknown>, ok: string, falha: string) {
    limparAvisos()
    setOcupadoId(membro.id)
    try {
      await executar()
      setSucesso(ok)
    } catch (e) {
      setErroAcao(`${falha}${e instanceof ApiError ? ` ${e.message}` : ''}`)
    } finally {
      setOcupadoId(null)
    }
  }

  function alternar(membro: MembroEquipe) {
    return acao(
      membro,
      () => alternarSituacao(membro),
      membro.ativo
        ? `"${membro.nome}" foi inativado e não entra mais no Eduka.`
        : `"${membro.nome}" foi reativado e volta a entrar no Eduka.`,
      `Não foi possível ${membro.ativo ? 'inativar' : 'reativar'} "${membro.nome}".`,
    )
  }

  function reenviar(membro: MembroEquipe) {
    return acao(
      membro,
      () => reenviarAcessoEquipe(membro.id),
      `O acesso foi reenviado para ${membro.email ?? membro.login}.`,
      `Não foi possível reenviar o acesso de "${membro.nome}".`,
    )
  }

  function handleCadastrado(membro: MembroEquipe) {
    setCadastroAberto(false)
    setSucesso(`"${membro.nome}" foi cadastrado. O acesso foi enviado para ${membro.email ?? membro.login}.`)
  }

  function handleEditado(membro: MembroEquipe) {
    setEditando(null)
    setSucesso(`Os dados de "${membro.nome}" foram atualizados.`)
  }

  return (
    <MasterLayout titulo="Equipe" subtitulo="Quem trabalha na secretaria e no financeiro da sua escola.">
      {!carregando && !erro && (
        <StatCards rotulo="Resumo da equipe">
          <StatCard
            label="Secretaria"
            value={ativos('SECRETARIA')}
            descricao={plural(ativos('SECRETARIA'), 'Pessoa ativa', 'Pessoas ativas')}
            destaque
          />
          <StatCard
            label="Financeiro"
            value={ativos('FINANCEIRO')}
            descricao={plural(ativos('FINANCEIRO'), 'Pessoa ativa', 'Pessoas ativas')}
          />
          <StatCard
            label="Aguardando primeiro acesso"
            value={aguardando}
            descricao={aguardando > 0 ? 'Ainda não definiram a senha' : 'Todos já entraram'}
            atencao={aguardando > 0}
          />
        </StatCards>
      )}

      <div className={styles.toolbar}>
        <div className={styles.filtros}>
          <label className={styles.busca}>
            <Icon name="search" size={17} />
            <input
              type="search"
              placeholder="Procurar pelo nome ou e-mail"
              aria-label="Buscar na equipe pelo nome ou e-mail"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </label>
          <Pilulas<Filtro> rotulo="Filtrar por função" opcoes={FILTROS} valor={filtro} onChange={setFiltro} />
        </div>
        <Button
          variant="primary"
          icon="plus"
          onClick={() => {
            limparAvisos()
            setCadastroAberto(true)
          }}
        >
          Adicionar à equipe
        </Button>
      </div>

      {sucesso && (
        <p className={styles.sucesso} role="status">
          {sucesso}
        </p>
      )}
      {erroAcao && (
        <p className={styles.alerta} role="alert">
          {erroAcao}
        </p>
      )}

      <section ref={topo} className={styles.card} aria-label="Pessoas da equipe">
        <div className={styles.rolagem}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th scope="col">Pessoa</th>
                <th scope="col">Função</th>
                <th scope="col">Acesso</th>
                <th scope="col">Situação</th>
                <th scope="col" className={styles.colAcoes}>
                  Ações
                </th>
              </tr>
            </thead>
            <tbody>
              {!carregando &&
                !erro &&
                paginacao.itensDaPagina.map((membro) => (
                  <tr key={membro.id} className={membro.ativo ? '' : styles.inativo}>
                    <td className={styles.colPrincipal}>
                      <div className={styles.pessoa}>
                        <span className={styles.avatar} aria-hidden="true">
                          {iniciais(membro.nome)}
                        </span>
                        <div className={styles.info}>
                          <strong className={styles.nome}>{membro.nome}</strong>
                          <span className={styles.email}>{membro.email ?? membro.login}</span>
                        </div>
                      </div>
                    </td>
                    <td data-rotulo="Função">
                      <Badge tone={membro.perfil === 'FINANCEIRO' ? 'neutral' : 'primary'}>
                        {ROTULOS_PERFIL_EQUIPE[membro.perfil]}
                      </Badge>
                    </td>
                    <td data-rotulo="Acesso">
                      {membro.aguardandoPrimeiroAcesso ? (
                        <div className={styles.acesso}>
                          <Dica texto="Ainda não definiu a senha. O acesso vale por alguns dias; depois, reenvie.">
                            <Badge tone="warning">Aguardando acesso</Badge>
                          </Dica>
                          {membro.ativo && (
                            <Dica texto="Gera uma nova senha provisória e manda o e-mail de novo">
                              <Button
                                size="sm"
                                icon="mail"
                                disabled={ocupadoId === membro.id}
                                onClick={() => reenviar(membro)}
                              >
                                Reenviar acesso
                              </Button>
                            </Dica>
                          )}
                        </div>
                      ) : (
                        <span className={styles.secundario}>Senha definida</span>
                      )}
                    </td>
                    <td data-rotulo="Situação">
                      {membro.ativo ? <Badge tone="success">Ativo</Badge> : <Badge tone="neutral">Inativo</Badge>}
                    </td>
                    <td className={styles.colAcoes}>
                      <div className={styles.acoes}>
                        <Button
                          size="sm"
                          icon="edit"
                          onClick={() => {
                            limparAvisos()
                            setEditando(membro)
                          }}
                        >
                          Editar
                        </Button>
                        <Dica texto={membro.ativo ? 'O login para de funcionar; o cadastro continua' : null}>
                          <Button
                            size="sm"
                            className={styles.alternar}
                            icon={membro.ativo ? 'pause' : 'check'}
                            disabled={ocupadoId === membro.id}
                            onClick={() => alternar(membro)}
                          >
                            {membro.ativo ? 'Inativar' : 'Reativar'}
                          </Button>
                        </Dica>
                      </div>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>

        {carregando && <Mensagem>Carregando a equipe…</Mensagem>}
        {!carregando && erro && (
          <Mensagem>
            <span>{erro}</span>
            <Button size="sm" onClick={recarregar}>
              Tentar novamente
            </Button>
          </Mensagem>
        )}
        {!carregando && !erro && visiveis.length === 0 && (
          <Mensagem>
            {equipe.length === 0
              ? 'Ninguém na equipe ainda. Cadastre a secretaria e o financeiro para dividir o trabalho da escola.'
              : 'Ninguém encontrado para a busca.'}
          </Mensagem>
        )}
      </section>
      {!carregando && !erro && <Paginacao paginacao={paginacao} rotulo="Páginas da equipe" />}

      {cadastroAberto && (
        <CadastroEquipeModal
          perfilInicial={filtro === 'FINANCEIRO' ? 'FINANCEIRO' : 'SECRETARIA'}
          onFechar={() => setCadastroAberto(false)}
          onSalvar={cadastrar}
          onSalvo={handleCadastrado}
        />
      )}

      {editando && (
        <CadastroEquipeModal
          key={editando.id}
          membroId={editando.id}
          onFechar={() => setEditando(null)}
          onSalvar={(dados) => atualizar(editando.id, semPerfil(dados))}
          onSalvo={handleEditado}
        />
      )}
    </MasterLayout>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
