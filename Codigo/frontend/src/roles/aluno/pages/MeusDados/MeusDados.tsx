import { useState } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { buscarMeusDados } from '../../../../services/pessoas/alunoService'
import { obterUsuarioLogado } from '../../../../services/acesso/authService'
import { listarMinhasTurmas } from '../../../../services/portal/portalAlunoService'
import { buscarMinhaCorrecaoAberta } from '../../../../services/pessoas/solicitacaoCorrecaoService'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import type { Aluno, Endereco } from '../../../../types/pessoas/aluno'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS } from '../../../../types/academico/matricula'
import type { TurmaDoAluno } from '../../../../types/portal/portalAluno'
import type { SolicitacaoCorrecao } from '../../../../types/pessoas/solicitacaoCorrecao'
import { formatarCnpjCpf, iniciais, mascararCep, mascararTelefone } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { ModalCorrecaoDados } from '../../components/ModalCorrecaoDados/ModalCorrecaoDados'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './MeusDados.module.css'

type AbaPerfil = 'pessoais' | 'responsaveis' | 'turmas' | 'acesso'

const SEM_DADO = '—'
const SEM_TURMAS: TurmaDoAluno[] = []

function enderecoEmTexto(endereco: Endereco | null): { rua: string; cidade: string } {
  if (!endereco) return { rua: SEM_DADO, cidade: SEM_DADO }
  const rua = [endereco.logradouro, endereco.numero].filter(Boolean).join(', ')
  const linha1 = [rua, endereco.bairro].filter(Boolean).join(' · ')
  const cidade = [endereco.cidade, endereco.estado].filter(Boolean).join(' · ')
  const cep = endereco.cep ? `CEP ${mascararCep(endereco.cep)}` : ''
  return {
    rua: linha1 || SEM_DADO,
    cidade: [cidade, cep].filter(Boolean).join(' · ') || SEM_DADO,
  }
}

function buscarAlunoOuNulo(signal: AbortSignal): Promise<Aluno | null> {
  return buscarMeusDados(signal)
}

export function MeusDados() {
  const { dados: aluno, carregando, erro, recarregar } = useDadosDoAluno(buscarAlunoOuNulo, null)
  const {
    dados: turmas,
    carregando: carregandoTurmas,
    erro: erroTurmas,
    recarregar: recarregarTurmas,
  } = useDadosDoAluno(listarMinhasTurmas, SEM_TURMAS)
  const [abaAtiva, setAbaAtiva] = useState<AbaPerfil>('pessoais')
  // IU15: um pedido de correção aberto por vez; o enviado agora vale até a próxima leitura.
  const { dados: correcaoAberta } = useDadosDoAluno<SolicitacaoCorrecao | null>(buscarMinhaCorrecaoAberta, null)
  const [correcaoEnviada, setCorrecaoEnviada] = useState<SolicitacaoCorrecao | null>(null)
  const [pedindoCorrecao, setPedindoCorrecao] = useState(false)
  const pedido = correcaoEnviada ?? correcaoAberta

  // A turma do resumo é a que o aluno está cursando; sem ela, a mais recente.
  const turma = turmas.find((t) => t.situacaoMatricula === 'ATIVA') ?? turmas[0]
  const login = obterUsuarioLogado()?.login || aluno?.email || SEM_DADO

  return (
    <AlunoLayout titulo="Meus dados" subtitulo="Confira suas informações. Para corrigir algo, fale com a secretaria.">
      {carregando && <div className={styles.mensagem} role="status">Carregando seus dados…</div>}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && aluno && (
        <div className={styles.container}>
          <aside className={styles.perfilCard} aria-label="Resumo do perfil">
            <div className={styles.avatar} aria-hidden="true">
              {iniciais(aluno.nome)}
            </div>

            <h2 className={styles.nomeAluno}>{aluno.nome}</h2>
            <span className={styles.matricula}>Matrícula {aluno.matricula}</span>

            {turma ? (
              <Badge tone={turma.situacaoMatricula === 'ATIVA' ? 'success' : 'neutral'}>
                {turma.situacaoMatricula === 'ATIVA'
                  ? 'Matrícula ativa'
                  : `Matrícula ${ROTULOS_STATUS_MATRICULA[turma.situacaoMatricula].toLowerCase()}`}
              </Badge>
            ) : (
              <Badge tone="neutral">Sem turma</Badge>
            )}

            {turma && (
              <div className={styles.detalhesAcademicos}>
                <LinhaInfo rotulo="Turma" valor={turma.turmaNome} />
                <LinhaInfo rotulo="Curso" valor={turma.cursoNome} />
                <LinhaInfo rotulo="Período" valor={turma.periodoLetivoNome} />
                <LinhaInfo rotulo="Turno" valor={ROTULOS_TURNO[turma.turno]} />
                <Button size="sm" icon="book" onClick={() => setAbaAtiva('turmas')}>
                  {turmas.length > 1 ? `Ver as ${turmas.length} turmas e disciplinas` : 'Ver disciplinas'}
                </Button>
              </div>
            )}

            {pedido ? (
              <p className={styles.pedidoCorrecao} role="status">
                <Icon name="clock" size={14} />
                Pedido de correção enviado em {formatarData(pedido.criadoEm.slice(0, 10))}. Aguardando a secretaria.
              </p>
            ) : (
              <Button size="sm" icon="edit" onClick={() => setPedindoCorrecao(true)}>
                Pedir correção de dados
              </Button>
            )}
          </aside>

          <section className={styles.painelDireito} aria-label="Detalhes cadastrais">
            <div className={styles.tabsContainer} role="tablist" aria-label="Detalhes cadastrais">
              <Aba id="pessoais" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Dados pessoais
              </Aba>
              <Aba id="responsaveis" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Responsáveis
              </Aba>
              <Aba id="turmas" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Turmas
              </Aba>
              <Aba id="acesso" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Acesso
              </Aba>
            </div>

            <div
              id="painel-meus-dados"
              role="tabpanel"
              aria-labelledby={`aba-${abaAtiva}`}
              className={styles.conteudoCard}
            >
              {abaAtiva === 'pessoais' && <DadosPessoais aluno={aluno} />}
              {abaAtiva === 'responsaveis' && <Responsaveis aluno={aluno} />}
              {abaAtiva === 'turmas' && (
                <Turmas
                  turmas={turmas}
                  carregando={carregandoTurmas}
                  erro={erroTurmas}
                  onTentarNovamente={recarregarTurmas}
                />
              )}
              {abaAtiva === 'acesso' && (
                <div>
                  <h3 className={styles.tituloSecao}>Acesso ao Eduka</h3>
                  <div className={styles.gridCampos}>
                    <Campo rotulo="Login" valor={login} />
                    <Campo rotulo="Perfil no sistema" valor="Aluno" />
                  </div>
                  <Aviso>Esqueceu a senha? Peça à secretaria para reenviar o seu acesso.</Aviso>
                </div>
              )}
            </div>
          </section>
        </div>
      )}
      {pedindoCorrecao && (
        <ModalCorrecaoDados
          onFechar={() => setPedindoCorrecao(false)}
          onEnviado={(enviado) => {
            setCorrecaoEnviada(enviado)
            setPedindoCorrecao(false)
          }}
        />
      )}
    </AlunoLayout>
  )
}

function DadosPessoais({ aluno }: { aluno: Aluno }) {
  const endereco = enderecoEmTexto(aluno.endereco)
  return (
    <div>
      <h3 className={styles.tituloSecao}>Dados pessoais</h3>
      <div className={styles.gridCampos}>
        <Campo rotulo="Nome completo" valor={aluno.nome} />
        <Campo
          rotulo="Data de nascimento"
          valor={aluno.dataNascimento ? formatarData(aluno.dataNascimento) : SEM_DADO}
        />
        <Campo rotulo="CPF" valor={aluno.cpf ? formatarCnpjCpf(aluno.cpf) : SEM_DADO} />
        <Campo rotulo="Gênero" valor={aluno.genero ?? SEM_DADO} />
        <Campo rotulo="E-mail" valor={aluno.email ?? SEM_DADO} />
        <Campo rotulo="Endereço" valor={endereco.rua} />
        <Campo rotulo="Cidade" valor={endereco.cidade} />
      </div>
      <Aviso>Só a secretaria pode alterar estes dados.</Aviso>
    </div>
  )
}

function Responsaveis({ aluno }: { aluno: Aluno }) {
  return (
    <div>
      <h3 className={styles.tituloSecao}>Responsáveis</h3>
      {aluno.responsaveis.length === 0 ? (
        <p className={styles.campoValor}>Nenhum responsável cadastrado.</p>
      ) : (
        aluno.responsaveis.map((r) => (
          <div key={r.pessoaId} className={styles.gridCampos}>
            <Campo rotulo="Nome" valor={r.nome} />
            <Campo rotulo="Parentesco" valor={r.parentesco ?? SEM_DADO} />
            <Campo rotulo="E-mail" valor={r.email ?? SEM_DADO} />
            <Campo rotulo="Telefone" valor={r.telefone ? mascararTelefone(r.telefone) : SEM_DADO} />
          </div>
        ))
      )}
      <Aviso>Só a secretaria pode alterar estes dados.</Aviso>
    </div>
  )
}

/** Todas as turmas do aluno (ativas primeiro, como o backend devolve), cada uma com a grade. */
function Turmas({
  turmas,
  carregando,
  erro,
  onTentarNovamente,
}: {
  turmas: TurmaDoAluno[]
  carregando: boolean
  erro: string | null
  onTentarNovamente: () => void
}) {
  return (
    <div>
      <h3 className={styles.tituloSecao}>Turmas e disciplinas</h3>
      {carregando && <p className={styles.campoValor} role="status">Carregando suas turmas…</p>}
      {!carregando && erro && (
        <div className={styles.erroTurmas} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={onTentarNovamente}>
            Tentar novamente
          </Button>
        </div>
      )}
      {!carregando && !erro && turmas.length === 0 && (
        <p className={styles.campoValor}>Você ainda não está matriculado em nenhuma turma.</p>
      )}
      {!carregando && !erro && turmas.length > 0 && (
        <ul className={styles.listaTurmas}>
          {turmas.map((t) => (
            <li key={t.matriculaId}>
              <article className={styles.turmaCard} aria-label={`Turma ${t.turmaNome}`}>
                <header className={styles.turmaCabecalho}>
                  <div>
                    <h4 className={styles.turmaNome}>
                      {t.turmaNome}
                      {t.turmaCodigo && <span className={styles.turmaCodigo}> · {t.turmaCodigo}</span>}
                    </h4>
                    <p className={styles.turmaMeta}>
                      {t.cursoNome} · {t.periodoLetivoNome} · {ROTULOS_TURNO[t.turno]}
                    </p>
                    <p className={styles.turmaMeta}>Matrícula em {formatarData(t.dataMatricula)}</p>
                  </div>
                  <Badge tone={TOM_DO_STATUS[t.situacaoMatricula]}>
                    {ROTULOS_STATUS_MATRICULA[t.situacaoMatricula]}
                  </Badge>
                </header>
                {t.disciplinas.length === 0 ? (
                  <p className={styles.turmaMeta}>A grade desta turma ainda não tem disciplinas.</p>
                ) : (
                  <ul className={styles.listaDisciplinas} aria-label={`Disciplinas de ${t.turmaNome}`}>
                    {t.disciplinas.map((d) => (
                      <li key={d.id} className={styles.disciplina}>
                        <span className={styles.disciplinaNome}>
                          {d.nome}
                          {d.codigo && <span className={styles.turmaCodigo}> · {d.codigo}</span>}
                        </span>
                        <span className={styles.disciplinaDetalhe}>
                          {d.professorNome ?? 'Professor a definir'}{' '}
                          <span className={styles.semQuebra}>· {d.cargaHoraria} h</span>
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Aba({
  id,
  ativa,
  onSelecionar,
  children,
}: {
  id: AbaPerfil
  ativa: AbaPerfil
  onSelecionar: (aba: AbaPerfil) => void
  children: string
}) {
  return (
    <button
      type="button"
      role="tab"
      id={`aba-${id}`}
      aria-selected={ativa === id}
      aria-controls="painel-meus-dados"
      className={`${styles.tabBotao} ${ativa === id ? styles.tabAtiva : ''}`}
      onClick={() => onSelecionar(id)}
    >
      {children}
    </button>
  )
}

function LinhaInfo({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className={styles.linhaInfo}>
      <span className={styles.rotuloInfo}>{rotulo}</span>
      <span className={styles.valorInfo}>{valor}</span>
    </div>
  )
}

function Campo({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className={styles.campoItem}>
      <span className={styles.campoRotulo}>{rotulo}</span>
      <span className={styles.campoValor}>{valor}</span>
    </div>
  )
}

function Aviso({ children }: { children: string }) {
  return (
    <div className={styles.avisoRodape}>
      <Icon name="info" size={16} />
      <span>{children}</span>
    </div>
  )
}
