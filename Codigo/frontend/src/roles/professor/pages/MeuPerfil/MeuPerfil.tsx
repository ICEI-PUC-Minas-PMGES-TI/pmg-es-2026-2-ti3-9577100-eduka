import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { IndicadorAjuda } from '../../../../components/ajuda/IndicadorAjuda/IndicadorAjuda'
import { CartaoTurma } from '../../../../components/academico/CartaoTurma/CartaoTurma'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Carrossel } from '../../../../components/ui/Carrossel/Carrossel'
import { Icon, type IconName } from '../../../../components/ui/Icon/Icon'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths } from '../../../../routes/paths'
import { obterUsuarioLogado } from '../../../../services/acesso/authService'
import { useModulos } from '../../../../services/acesso/modulosSessao'
import { buscarMeuPerfil } from '../../../../services/portal/portalProfessorService'
import { descreverHorarios } from '../../../../types/academico/horario'
import { ROTULOS_TURNO, type TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { ROTULOS_VINCULO, type Professor } from '../../../../types/pessoas/professor'
import { formatarCnpjCpf, iniciais, mascararTelefone, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { useMinhasTurmas } from '../../hooks/useMinhasTurmas'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import styles from './MeuPerfil.module.css'

const SEM_DADO = '—'
const SEM_LOGIN_DE_PROFESSOR =
  'Seu login não está vinculado a um cadastro de professor. Fale com a secretaria da escola.'

/** Disciplinas diferentes que o professor leciona, somando todas as turmas. */
function contarDisciplinas(turmas: TurmaDoProfessor[]): number {
  return new Set(turmas.flatMap((t) => t.disciplinas.map((d) => d.id))).size
}

/** Aulas por semana: cada horário da grade é uma aula semanal. */
function contarAulas(turmas: TurmaDoProfessor[]): number {
  return turmas.reduce((soma, t) => soma + t.disciplinas.reduce((s, d) => s + d.horarios.length, 0), 0)
}

/**
 * Meu perfil do professor: o resumo com os números das turmas no topo, os
 * dados pessoais e profissionais lado a lado e, embaixo, o carrossel das
 * turmas com o horário de cada disciplina e atalhos para Avaliações e
 * Chamada. Tudo é só leitura; quem corrige é a secretaria. Professor e escola
 * saem do token (GET /portal/professor/me).
 */
export function MeuPerfil() {
  const navigate = useNavigate()
  const { dados: professor, carregando, erro, recarregar } = useCarregamento<Professor | null>(
    buscarMeuPerfil,
    null,
    SEM_LOGIN_DE_PROFESSOR,
  )
  const {
    turmas,
    carregando: carregandoTurmas,
    erro: erroTurmas,
    recarregar: recarregarTurmas,
  } = useMinhasTurmas()
  const login = obterUsuarioLogado()?.login || professor?.email || SEM_DADO
  // As turmas em andamento primeiro no carrossel; as encerradas ficam no fim, na ordem do backend.
  const turmasOrdenadas = [...turmas].sort((a, b) => Number(b.ativa) - Number(a.ativa))
  const ativas = turmas.filter((t) => t.ativa)

  // O resumo conta só o que está em andamento. Enquanto as turmas carregam
  // (ou se falharem), os números ficam em aberto.
  function numeroDasTurmas(calcular: (t: TurmaDoProfessor[]) => number): string {
    if (carregandoTurmas) return '…'
    if (erroTurmas) return SEM_DADO
    return String(calcular(ativas))
  }

  return (
    <ProfessorLayout titulo="Meu perfil" subtitulo="Confira seus dados. Só a secretaria pode alterá-los.">
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

      {!carregando && !erro && professor && (
        <div className={styles.pagina}>
          <section className={styles.perfil} aria-label="Resumo do perfil">
            <div className={styles.identidade}>
              <div className={styles.avatar} aria-hidden="true">
                {iniciais(professor.nome)}
              </div>
              <div className={styles.identidadeTexto}>
                <h2 className={styles.nome}>{professor.nome}</h2>
                <div className={styles.identidadeLinha}>
                  <span className={styles.papel}>
                    Professor{professor.vinculo ? ` · ${ROTULOS_VINCULO[professor.vinculo]}` : ''}
                  </span>
                  {professor.ativo ? <Badge tone="success">Ativo</Badge> : <Badge tone="neutral">Inativo</Badge>}
                </div>
                <span className={styles.login}>
                  <Icon name="lock" size={13} />
                  Login no Eduka: <strong>{login}</strong>
                </span>
              </div>
            </div>

            <div className={styles.acao}>
              <Button size="sm" icon="users" onClick={() => navigate(paths.professor.turmas)}>
                Ver minhas turmas
              </Button>
            </div>

            <dl className={styles.numeros} aria-label="Resumo das turmas">
              <Numero icone="users" rotulo="Turmas ativas" valor={numeroDasTurmas((t) => t.length)} />
              <Numero icone="book" rotulo="Disciplinas" valor={numeroDasTurmas(contarDisciplinas)} />
              <Numero icone="clock" rotulo="Aulas por semana" valor={numeroDasTurmas(contarAulas)} />
              <Numero
                icone="calendar"
                rotulo="Admissão"
                valor={professor.dataAdmissao ? formatarData(professor.dataAdmissao) : SEM_DADO}
              />
            </dl>
          </section>

          <div className={styles.grade}>
            <Cartao titulo="Dados pessoais" icone="user">
              <dl className={styles.campos}>
                <Campo rotulo="Nome completo" valor={professor.nome} largo />
                <Campo rotulo="CPF" valor={professor.cpf ? formatarCnpjCpf(professor.cpf) : SEM_DADO} />
                <Campo
                  rotulo="Telefone"
                  valor={professor.telefone ? mascararTelefone(professor.telefone) : SEM_DADO}
                />
                <Campo rotulo="E-mail" valor={professor.email ?? SEM_DADO} largo />
              </dl>
            </Cartao>

            <Cartao titulo="Dados profissionais" icone="graduationCap">
              <dl className={styles.campos}>
                <Campo rotulo="Formação" valor={professor.formacao ?? SEM_DADO} largo />
                <Campo
                  rotulo="Vínculo"
                  valor={professor.vinculo ? ROTULOS_VINCULO[professor.vinculo] : 'Não informado'}
                />
                <Campo rotulo="Situação" valor={professor.ativo ? 'Ativo' : 'Inativo'} />
                <Campo
                  rotulo="Data de admissão"
                  valor={professor.dataAdmissao ? formatarData(professor.dataAdmissao) : SEM_DADO}
                  largo
                />
              </dl>
            </Cartao>
          </div>

          <IndicadorAjuda>Algum dado seu está errado? Veja como falar com a secretaria</IndicadorAjuda>

          <TurmasDoProfessor
            turmas={turmasOrdenadas}
            carregando={carregandoTurmas}
            erro={erroTurmas}
            onTentarNovamente={recarregarTurmas}
          />
        </div>
      )}
    </ProfessorLayout>
  )
}

function TurmasDoProfessor({
  turmas,
  carregando,
  erro,
  onTentarNovamente,
}: {
  turmas: TurmaDoProfessor[]
  carregando: boolean
  erro: string | null
  onTentarNovamente: () => void
}) {
  const temModulo = useModulos()
  const comChamada = temModulo('CONTROLE_FREQUENCIA')
  const total = turmas.length
  const ativas = turmas.filter((t) => t.ativa).length
  const contagem =
    ativas === total
      ? `${total} ${plural(total, 'turma', 'turmas')}`
      : `${ativas} ${plural(ativas, 'turma ativa', 'turmas ativas')} e ${total - ativas} ${plural(total - ativas, 'encerrada', 'encerradas')}`

  return (
    <Carrossel
      titulo="Minhas turmas"
      resumo={`${contagem}. Cada disciplina tem atalhos para as avaliações${comChamada ? ' e a chamada' : ''}.`}
      carregando={carregando}
      erro={erro}
      onTentarNovamente={onTentarNovamente}
      textoCarregando="Carregando suas turmas…"
      vazio={
        <>
          <Icon name="users" size={22} />
          <span>Você ainda não está alocado em nenhuma turma. A secretaria faz a alocação.</span>
        </>
      }
      rotulos={{
        lista: 'Turmas',
        anterior: 'Turma anterior',
        proxima: 'Próxima turma',
        irPara: (nome) => `Ir para a turma ${nome}`,
      }}
      itens={turmas.map((t, i) => {
        const aulas = t.disciplinas.reduce((s, d) => s + d.horarios.length, 0)
        const qtd = t.disciplinas.length
        return {
          chave: String(t.id),
          rotulo: t.nome,
          conteudo: (
            <CartaoTurma
              nome={t.nome}
              codigo={t.codigo}
              posicao={total > 1 ? `${i + 1} de ${total}` : null}
              destaque={t.ativa}
              largo={total === 1}
              situacao={t.ativa ? <Badge tone="success">Ativa</Badge> : <Badge tone="neutral">Encerrada</Badge>}
              etiquetas={[
                { icone: 'graduationCap', texto: t.cursoNome },
                { icone: 'calendar', texto: `Período ${t.periodoLetivoNome}` },
                { icone: 'clock', texto: ROTULOS_TURNO[t.turno] },
              ]}
              resumo={`${qtd} ${plural(qtd, 'disciplina sua', 'disciplinas suas')} · ${aulas} ${plural(aulas, 'aula', 'aulas')} por semana`}
              semDisciplinas="Você ainda não tem disciplina nesta turma."
              disciplinas={t.disciplinas.map((d) => {
                const query = new URLSearchParams({ turma: String(t.id), disciplina: String(d.id) }).toString()
                return {
                  id: d.id,
                  nome: d.nome,
                  codigo: d.codigo,
                  detalhe: (
                    <>
                      {descreverHorarios(d.horarios) ?? 'Horário a definir'}{' '}
                      <span className={styles.semQuebra}>· {d.cargaHoraria} h</span>
                    </>
                  ),
                  acoes: (
                    <>
                      <Link className={styles.atalho} to={`${paths.professor.avaliacoes}?${query}`}>
                        <Icon name="list" size={13} />
                        Avaliações
                      </Link>
                      {comChamada && (
                        <Link className={styles.atalho} to={`${paths.professor.chamada}?${query}`}>
                          <Icon name="calendar" size={13} />
                          Chamada
                        </Link>
                      )}
                    </>
                  ),
                }
              })}
            />
          ),
        }
      })}
    />
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

function Numero({ icone, rotulo, valor }: { icone: IconName; rotulo: string; valor: string }) {
  return (
    <div className={styles.numero}>
      <span className={styles.numeroIcone} aria-hidden="true">
        <Icon name={icone} size={16} />
      </span>
      <div>
        <dt className={styles.campoRotulo}>{rotulo}</dt>
        <dd className={styles.numeroValor}>{valor}</dd>
      </div>
    </div>
  )
}

function Campo({ rotulo, valor, largo = false }: { rotulo: string; valor: string; largo?: boolean }) {
  return (
    <div className={`${styles.campo} ${largo ? styles.campoLargo : ''}`}>
      <dt className={styles.campoRotulo}>{rotulo}</dt>
      <dd className={styles.campoValor}>{valor}</dd>
    </div>
  )
}
