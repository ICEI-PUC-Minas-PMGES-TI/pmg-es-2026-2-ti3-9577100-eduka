import { useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon, type IconName } from '../../../../components/ui/Icon/Icon'
import { buscarMeusDados } from '../../../../services/pessoas/alunoService'
import { obterUsuarioLogado } from '../../../../services/acesso/authService'
import { listarMinhasTurmas } from '../../../../services/portal/portalAlunoService'
import { buscarMinhaCorrecaoAberta } from '../../../../services/pessoas/solicitacaoCorrecaoService'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import type { Aluno, Endereco } from '../../../../types/pessoas/aluno'
import { ROTULOS_STATUS_MATRICULA } from '../../../../types/academico/matricula'
import type { TurmaDoAluno } from '../../../../types/portal/portalAluno'
import type { SolicitacaoCorrecao } from '../../../../types/pessoas/solicitacaoCorrecao'
import { formatarCnpjCpf, iniciais, mascararCep, mascararTelefone } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { CarrosselTurmas } from '../../components/CarrosselTurmas/CarrosselTurmas'
import { ModalCorrecaoDados } from '../../components/ModalCorrecaoDados/ModalCorrecaoDados'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './MeusDados.module.css'

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

/**
 * Meus dados do ALUNO: o resumo com a turma atual no topo, os dados pessoais
 * com os responsáveis e o acesso ao lado e, embaixo, o carrossel com todas as
 * turmas. Tudo é só leitura; a correção é pedida à secretaria (IU15).
 */
export function MeusDados() {
  const { dados: aluno, carregando, erro, recarregar } = useDadosDoAluno(buscarAlunoOuNulo, null)
  const {
    dados: turmas,
    carregando: carregandoTurmas,
    erro: erroTurmas,
    recarregar: recarregarTurmas,
  } = useDadosDoAluno(listarMinhasTurmas, SEM_TURMAS)
  // IU15: um pedido de correção aberto por vez; o enviado agora vale até a próxima leitura.
  const { dados: correcaoAberta } = useDadosDoAluno<SolicitacaoCorrecao | null>(buscarMinhaCorrecaoAberta, null)
  const [correcaoEnviada, setCorrecaoEnviada] = useState<SolicitacaoCorrecao | null>(null)
  const [pedindoCorrecao, setPedindoCorrecao] = useState(false)
  const pedido = correcaoEnviada ?? correcaoAberta

  // A turma do resumo é a que o aluno está cursando; sem ela, a mais recente.
  const turma = turmas.find((t) => t.situacaoMatricula === 'ATIVA') ?? turmas[0]
  const login = obterUsuarioLogado()?.login || aluno?.email || SEM_DADO

  return (
    <AlunoLayout titulo="Meus dados" subtitulo="Confira suas informações. Só a secretaria pode alterá-las.">
      {carregando && (
        <div className={styles.mensagem} role="status">
          Carregando seus dados…
        </div>
      )}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && aluno && (
        <div className={styles.pagina}>
          <section className={styles.perfil} aria-label="Resumo do perfil">
            <div className={styles.identidade}>
              <div className={styles.avatar} aria-hidden="true">
                {iniciais(aluno.nome)}
              </div>
              <div className={styles.identidadeTexto}>
                <h2 className={styles.nomeAluno}>{aluno.nome}</h2>
                <div className={styles.identidadeLinha}>
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
                </div>
                <span className={styles.login}>
                  <Icon name="lock" size={13} />
                  Login no Eduka: <strong>{login}</strong>
                </span>
              </div>
            </div>

            {turma && (
              <dl className={styles.resumoAcademico} aria-label="Turma atual">
                <InfoResumo icone="users" rotulo="Turma" valor={turma.turmaNome} />
                <InfoResumo icone="graduationCap" rotulo="Curso" valor={turma.cursoNome} />
                <InfoResumo icone="calendar" rotulo="Período" valor={turma.periodoLetivoNome} />
                <InfoResumo icone="clock" rotulo="Turno" valor={ROTULOS_TURNO[turma.turno]} />
              </dl>
            )}

            <div className={styles.acaoCorrecao}>
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
            </div>
          </section>

          <div className={styles.grade}>
            <Cartao titulo="Dados pessoais" icone="user">
              <DadosPessoais aluno={aluno} />
            </Cartao>

            <Cartao titulo="Responsáveis" icone="users">
              <Responsaveis aluno={aluno} />
              <p className={styles.aviso}>
                <Icon name="info" size={14} />
                Algum dado seu ou dos responsáveis mudou? Use “Pedir correção de dados” lá em cima.
              </p>
            </Cartao>
          </div>

          <CarrosselTurmas
            turmas={turmas}
            carregando={carregandoTurmas}
            erro={erroTurmas}
            onTentarNovamente={recarregarTurmas}
          />
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
    <dl className={styles.gridCampos}>
      <Campo rotulo="Nome completo" valor={aluno.nome} colunas={4} />
      <Campo rotulo="Gênero" valor={aluno.genero ?? SEM_DADO} colunas={2} />
      <Campo
        rotulo="Data de nascimento"
        valor={aluno.dataNascimento ? formatarData(aluno.dataNascimento) : SEM_DADO}
        colunas={3}
      />
      <Campo rotulo="CPF" valor={aluno.cpf ? formatarCnpjCpf(aluno.cpf) : SEM_DADO} colunas={3} />
      <Campo rotulo="E-mail" valor={aluno.email ?? SEM_DADO} colunas={6} />
      <Campo rotulo="Endereço" valor={endereco.rua} colunas={3} />
      <Campo rotulo="Cidade" valor={endereco.cidade} colunas={3} />
    </dl>
  )
}

function Responsaveis({ aluno }: { aluno: Aluno }) {
  if (aluno.responsaveis.length === 0) {
    return <p className={styles.semDado}>Nenhum responsável cadastrado.</p>
  }
  return (
    <ul className={styles.responsaveis}>
      {aluno.responsaveis.map((r) => (
        <li key={r.pessoaId} className={styles.responsavel}>
          <div className={styles.responsavelTopo}>
            <span className={styles.responsavelAvatar} aria-hidden="true">
              {iniciais(r.nome)}
            </span>
            <div className={styles.responsavelNome}>
              <strong>{r.nome}</strong>
              <span>{r.parentesco ?? 'Parentesco não informado'}</span>
            </div>
          </div>
          <dl className={styles.listaCampos}>
            <Campo rotulo="E-mail" valor={r.email ?? SEM_DADO} />
            <Campo rotulo="Telefone" valor={r.telefone ? mascararTelefone(r.telefone) : SEM_DADO} />
          </dl>
        </li>
      ))}
    </ul>
  )
}

function Cartao({ titulo, icone, children }: { titulo: string; icone: IconName; children: ReactNode }) {
  return (
    <section className={styles.cartao} aria-label={titulo}>
      <h3 className={styles.tituloCartao}>
        <span className={styles.tituloIcone} aria-hidden="true">
          <Icon name={icone} size={16} />
        </span>
        {titulo}
      </h3>
      {children}
    </section>
  )
}

function InfoResumo({ icone, rotulo, valor }: { icone: IconName; rotulo: string; valor: string }) {
  return (
    <div className={styles.infoResumo}>
      <span className={styles.infoIcone} aria-hidden="true">
        <Icon name={icone} size={16} />
      </span>
      <div>
        <dt className={styles.campoRotulo}>{rotulo}</dt>
        <dd className={styles.infoValor}>{valor}</dd>
      </div>
    </div>
  )
}

const SPAN = { 2: styles.span2, 3: styles.span3, 4: styles.span4, 6: styles.span6 } as const

/** `colunas` vale dentro da grade de seis colunas dos dados pessoais. */
function Campo({ rotulo, valor, colunas }: { rotulo: string; valor: string; colunas?: keyof typeof SPAN }) {
  return (
    <div className={`${styles.campo} ${colunas ? SPAN[colunas] : ''}`}>
      <dt className={styles.campoRotulo}>{rotulo}</dt>
      <dd className={styles.campoValor}>{valor}</dd>
    </div>
  )
}
