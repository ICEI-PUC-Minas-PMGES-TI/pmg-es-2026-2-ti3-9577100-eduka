import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { paths } from '../../../../routes/paths'
import { useEstruturaAcademica } from '../../hooks/useEstruturaAcademica'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import { Cursos } from './Cursos'
import { Disciplinas } from './Disciplinas'
import { PeriodosLetivos } from './PeriodosLetivos'
import styles from './PainelSecretaria.module.css'
import { Turmas } from './Turmas'

// Na ordem em que a estrutura é montada: o período e o curso vêm antes da
// disciplina, e a turma usa os três.
const ABAS = [
  { id: 'periodos', rotulo: 'Períodos letivos' },
  { id: 'cursos', rotulo: 'Cursos' },
  { id: 'disciplinas', rotulo: 'Disciplinas' },
  { id: 'turmas', rotulo: 'Turmas' },
] as const

type Aba = (typeof ABAS)[number]['id']

function abaValida(valor: string | null): Aba {
  return ABAS.some((a) => a.id === valor) ? (valor as Aba) : 'periodos'
}

/**
 * Painel acadêmico da secretaria (rota paths.secretaria.academico). A aba
 * fica na URL (?aba=cursos), para o recarregar e o voltar do navegador
 * manterem a tela.
 */
export function SecretariaDashboard() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const aba = abaValida(params.get('aba'))
  const estrutura = useEstruturaAcademica()

  function trocarAba(nova: Aba) {
    setParams(nova === 'periodos' ? {} : { aba: nova }, { replace: true })
  }

  return (
    <SecretariaLayout
      titulo="Acadêmico"
      subtitulo="Monte a estrutura da escola: períodos, cursos, disciplinas e turmas."
    >
      <div className={styles.topo}>
        <div className={styles.abas} role="tablist" aria-label="Estrutura acadêmica">
          {ABAS.map((a) => (
            <button
              key={a.id}
              type="button"
              role="tab"
              id={`aba-${a.id}`}
              aria-selected={aba === a.id}
              aria-controls="painel-academico"
              className={`${styles.aba} ${aba === a.id ? styles.abaAtiva : ''}`}
              onClick={() => trocarAba(a.id)}
            >
              {a.rotulo}
            </button>
          ))}
        </div>
        <Button icon="graduationCap" onClick={() => navigate(paths.secretaria.alocacoes)}>
          Alocação de professores
        </Button>
      </div>

      <section id="painel-academico" role="tabpanel" aria-labelledby={`aba-${aba}`} className={styles.painel}>
        {estrutura.carregando && <p className={styles.estado}>Carregando a estrutura acadêmica…</p>}

        {!estrutura.carregando && estrutura.erro && (
          <div className={styles.estado}>
            <span>{estrutura.erro}</span>
            <Button size="sm" onClick={estrutura.recarregar}>
              Tentar novamente
            </Button>
          </div>
        )}

        {!estrutura.carregando && !estrutura.erro && (
          <>
            {aba === 'periodos' && (
              <PeriodosLetivos periodos={estrutura.periodos} onCadastrar={estrutura.cadastrarPeriodo} />
            )}
            {aba === 'cursos' && <Cursos cursos={estrutura.cursos} onCadastrar={estrutura.cadastrarCurso} />}
            {aba === 'disciplinas' && (
              <Disciplinas
                disciplinas={estrutura.disciplinas}
                cursos={estrutura.cursos}
                onCadastrar={estrutura.cadastrarDisciplina}
              />
            )}
            {aba === 'turmas' && (
              <Turmas
                turmas={estrutura.turmas}
                periodos={estrutura.periodos}
                cursos={estrutura.cursos}
                disciplinas={estrutura.disciplinas}
                onCadastrar={estrutura.cadastrarTurma}
              />
            )}
          </>
        )}
      </section>
    </SecretariaLayout>
  )
}
