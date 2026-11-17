import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths } from '../../../../routes/paths'
import { obterUsuarioLogado } from '../../../../services/acesso/authService'
import { buscarMeuPerfil } from '../../../../services/portal/portalProfessorService'
import { ROTULOS_VINCULO, type Professor } from '../../../../types/pessoas/professor'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { formatarCnpjCpf, iniciais, mascararTelefone } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { useMinhasTurmas } from '../../hooks/useMinhasTurmas'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import styles from './MeuPerfil.module.css'

type AbaPerfil = 'pessoais' | 'profissionais' | 'acesso'

const SEM_DADO = '—'
const SEM_LOGIN_DE_PROFESSOR =
  'Seu login não está vinculado a um cadastro de professor. Fale com a secretaria da escola.'

/** Disciplinas diferentes que o professor leciona, somando todas as turmas. */
function contarDisciplinas(turmas: TurmaDoProfessor[]): number {
  return new Set(turmas.flatMap((t) => t.disciplinas.map((d) => d.id))).size
}

/**
 * Meu perfil do professor: os dados que a secretaria cadastrou, só para
 * leitura. Professor e escola saem do token (GET /portal/professor/me).
 */
export function MeuPerfil() {
  const navigate = useNavigate()
  const { dados: professor, carregando, erro, recarregar } = useCarregamento<Professor | null>(
    buscarMeuPerfil,
    null,
    SEM_LOGIN_DE_PROFESSOR,
  )
  const { turmas, carregando: carregandoTurmas, erro: erroTurmas } = useMinhasTurmas()
  const [abaAtiva, setAbaAtiva] = useState<AbaPerfil>('pessoais')
  const login = obterUsuarioLogado()?.login || professor?.email || SEM_DADO

  return (
    <ProfessorLayout titulo="Meu perfil" subtitulo="Confira seus dados. Para corrigir algo, fale com a secretaria.">
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
        <div className={styles.container}>
          <aside className={styles.perfilCard} aria-label="Resumo do perfil">
            <div className={styles.avatar} aria-hidden="true">
              {iniciais(professor.nome)}
            </div>
            <h2 className={styles.nome}>{professor.nome}</h2>
            <span className={styles.subtitulo}>
              Professor{professor.vinculo ? ` · ${ROTULOS_VINCULO[professor.vinculo]}` : ''}
            </span>
            {professor.ativo ? <Badge tone="success">Ativo</Badge> : <Badge tone="neutral">Inativo</Badge>}

            <div className={styles.detalhes}>
              <LinhaInfo
                rotulo="Turmas"
                valor={carregandoTurmas ? '…' : erroTurmas ? SEM_DADO : String(turmas.length)}
              />
              <LinhaInfo
                rotulo="Disciplinas"
                valor={carregandoTurmas ? '…' : erroTurmas ? SEM_DADO : String(contarDisciplinas(turmas))}
              />
              <LinhaInfo
                rotulo="Admissão"
                valor={professor.dataAdmissao ? formatarData(professor.dataAdmissao) : SEM_DADO}
              />
            </div>

            <Button size="sm" icon="users" onClick={() => navigate(paths.professor.turmas)}>
              Ver minhas turmas
            </Button>
          </aside>

          <section className={styles.painel} aria-label="Detalhes cadastrais">
            <div className={styles.abas} role="tablist" aria-label="Detalhes cadastrais">
              <Aba id="pessoais" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Dados pessoais
              </Aba>
              <Aba id="profissionais" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Dados profissionais
              </Aba>
              <Aba id="acesso" ativa={abaAtiva} onSelecionar={setAbaAtiva}>
                Acesso
              </Aba>
            </div>

            <div id="painel-meu-perfil" role="tabpanel" aria-labelledby={`aba-${abaAtiva}`} className={styles.conteudo}>
              {abaAtiva === 'pessoais' && (
                <>
                  <h3 className={styles.tituloSecao}>Dados pessoais</h3>
                  <div className={styles.campos}>
                    <Campo rotulo="Nome completo" valor={professor.nome} />
                    <Campo rotulo="CPF" valor={professor.cpf ? formatarCnpjCpf(professor.cpf) : SEM_DADO} />
                    <Campo rotulo="E-mail" valor={professor.email ?? SEM_DADO} />
                    <Campo
                      rotulo="Telefone"
                      valor={professor.telefone ? mascararTelefone(professor.telefone) : SEM_DADO}
                    />
                  </div>
                  <Aviso>Só a secretaria pode alterar estes dados.</Aviso>
                </>
              )}
              {abaAtiva === 'profissionais' && (
                <>
                  <h3 className={styles.tituloSecao}>Dados profissionais</h3>
                  <div className={styles.campos}>
                    <Campo rotulo="Formação" valor={professor.formacao ?? SEM_DADO} />
                    <Campo
                      rotulo="Data de admissão"
                      valor={professor.dataAdmissao ? formatarData(professor.dataAdmissao) : SEM_DADO}
                    />
                    <Campo
                      rotulo="Vínculo"
                      valor={professor.vinculo ? ROTULOS_VINCULO[professor.vinculo] : 'Não informado'}
                    />
                    <Campo rotulo="Situação" valor={professor.ativo ? 'Ativo' : 'Inativo'} />
                  </div>
                  <Aviso>Só a secretaria pode alterar estes dados.</Aviso>
                </>
              )}
              {abaAtiva === 'acesso' && (
                <>
                  <h3 className={styles.tituloSecao}>Acesso ao Eduka</h3>
                  <div className={styles.campos}>
                    <Campo rotulo="Login" valor={login} />
                    <Campo rotulo="Perfil no sistema" valor="Professor" />
                  </div>
                  <Aviso>Esqueceu a senha? Use "Esqueceu sua senha?" na tela de login.</Aviso>
                </>
              )}
            </div>
          </section>
        </div>
      )}
    </ProfessorLayout>
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
      aria-controls="painel-meu-perfil"
      className={`${styles.aba} ${ativa === id ? styles.abaAtiva : ''}`}
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
    <div className={styles.campo}>
      <span className={styles.campoRotulo}>{rotulo}</span>
      <span className={styles.campoValor}>{valor}</span>
    </div>
  )
}

function Aviso({ children }: { children: string }) {
  return (
    <div className={styles.aviso}>
      <Icon name="info" size={16} />
      <span>{children}</span>
    </div>
  )
}
