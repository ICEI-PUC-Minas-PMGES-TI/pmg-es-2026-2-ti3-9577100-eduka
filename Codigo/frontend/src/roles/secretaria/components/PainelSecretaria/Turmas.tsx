import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { Select } from '../../../../components/Select/Select'
import {
  OPCOES_TURNO,
  type Curso,
  type Disciplina,
  type PeriodoLetivo,
  type TurmaAcademica,
  type TurmaRequest,
} from '../../../../types/academico'
import { ROTULOS_TURNO, type Turno } from '../../../../types/alocacao'
import { mascararInteiro } from '../../../../utils/formatters'
import { CabecalhoAba, Tabela } from './comum'
import { opcional } from './formatacao'
import { ModalCadastro } from './ModalCadastro'
import styles from './PainelSecretaria.module.css'

// Limites das colunas de TURMA (V14).
const MAX_NOME = 100
const MAX_CODIGO = 50

type TurmasProps = {
  turmas: TurmaAcademica[]
  periodos: PeriodoLetivo[]
  cursos: Curso[]
  disciplinas: Disciplina[]
  onCadastrar: (dados: TurmaRequest) => Promise<TurmaAcademica>
}

export function Turmas({ turmas, periodos, cursos, disciplinas, onCadastrar }: TurmasProps) {
  const [modalAberto, setModalAberto] = useState(false)
  const [sucesso, setSucesso] = useState<string | null>(null)

  function abrir() {
    setSucesso(null)
    setModalAberto(true)
  }

  return (
    <>
      <CabecalhoAba titulo="Turmas" rotuloNovo="Nova turma" onNovo={abrir} sucesso={sucesso} />

      <Tabela
        colunas={['Turma', 'Curso', 'Período letivo', 'Turno', 'Vagas', 'Disciplinas']}
        vazio="Nenhuma turma cadastrada ainda."
        linhas={turmas.map((t) => (
          <tr key={t.id}>
            <td>
              <span className={styles.destaque}>{t.nome}</span>
              {t.codigo && <span className={styles.subtexto}>{t.codigo}</span>}
            </td>
            <td>{t.cursoNome}</td>
            <td>{t.periodoLetivoNome}</td>
            <td>{ROTULOS_TURNO[t.turno]}</td>
            <td>{t.vagas}</td>
            <td className={styles.secundario}>
              {t.disciplinas.length === 0
                ? 'Sem grade'
                : t.disciplinas
                    .map((d) => d.nome)
                    .sort((a, b) => a.localeCompare(b, 'pt-BR'))
                    .join(', ')}
            </td>
          </tr>
        ))}
      />

      {modalAberto && (
        <CadastroTurma
          periodos={periodos}
          cursos={cursos}
          disciplinas={disciplinas}
          onFechar={() => setModalAberto(false)}
          onSalvar={async (dados) => {
            const criada = await onCadastrar(dados)
            setModalAberto(false)
            setSucesso(`Turma "${criada.nome}" cadastrada.`)
          }}
        />
      )}
    </>
  )
}

type Campos = {
  nome: string
  codigo: string
  periodoLetivoId: string
  cursoId: string
  turno: Turno | ''
  vagas: string
  disciplinaIds: number[]
}
type Erros = Partial<Record<'nome' | 'periodoLetivoId' | 'cursoId' | 'turno' | 'vagas', string>>

function CadastroTurma({
  periodos,
  cursos,
  disciplinas,
  onFechar,
  onSalvar,
}: {
  periodos: PeriodoLetivo[]
  cursos: Curso[]
  disciplinas: Disciplina[]
  onFechar: () => void
  onSalvar: (dados: TurmaRequest) => Promise<void>
}) {
  const [campos, setCampos] = useState<Campos>({
    nome: '',
    codigo: '',
    periodoLetivoId: '',
    cursoId: '',
    turno: '',
    vagas: '',
    disciplinaIds: [],
  })
  const [erros, setErros] = useState<Erros>({})

  const opcoesPeriodo = periodos.filter((p) => p.ativo).map((p) => ({ valor: String(p.id), rotulo: p.nome }))
  const opcoesCurso = cursos.filter((c) => c.ativo).map((c) => ({ valor: String(c.id), rotulo: c.nome }))
  // A grade aceita as disciplinas do curso escolhido e as comuns a todos.
  const disciplinasDoCurso = campos.cursoId
    ? disciplinas.filter((d) => d.ativa && (d.cursoId === null || String(d.cursoId) === campos.cursoId))
    : []

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (campo in erros) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function trocarCurso(cursoId: string) {
    alterar('cursoId', cursoId)
    // Disciplinas de outro curso saem da grade.
    setCampos((atuais) => ({
      ...atuais,
      disciplinaIds: atuais.disciplinaIds.filter((id) => {
        const disciplina = disciplinas.find((d) => d.id === id)
        return disciplina && (disciplina.cursoId === null || String(disciplina.cursoId) === cursoId)
      }),
    }))
  }

  function alternarDisciplina(id: number) {
    setCampos((atuais) => ({
      ...atuais,
      disciplinaIds: atuais.disciplinaIds.includes(id)
        ? atuais.disciplinaIds.filter((d) => d !== id)
        : [...atuais.disciplinaIds, id],
    }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.nome.trim()) novos.nome = 'Informe o nome da turma.'
    if (!campos.periodoLetivoId) novos.periodoLetivoId = 'Selecione o período letivo.'
    if (!campos.cursoId) novos.cursoId = 'Selecione o curso.'
    if (!campos.turno) novos.turno = 'Selecione o turno.'
    if (campos.vagas === '') novos.vagas = 'Informe a quantidade de vagas.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  return (
    <ModalCadastro
      titulo="Nova turma"
      rotuloSalvar="Salvar turma"
      onFechar={onFechar}
      validar={validar}
      onEnviar={() =>
        onSalvar({
          nome: campos.nome.trim(),
          codigo: opcional(campos.codigo),
          periodoLetivoId: Number(campos.periodoLetivoId),
          cursoId: Number(campos.cursoId),
          // validar() já garante que um turno foi escolhido.
          turno: campos.turno as Turno,
          vagas: Number(campos.vagas),
          disciplinaIds: campos.disciplinaIds,
        })
      }
    >
      <Campo label="Nome da turma" erro={erros.nome} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="nome"
            maxLength={MAX_NOME}
            placeholder="Ex.: 6º ano A"
            value={campos.nome}
            onChange={(e) => alterar('nome', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Código">
        {(props) => (
          <input
            {...props}
            name="codigo"
            maxLength={MAX_CODIGO}
            placeholder="Ex.: 6A-2026"
            value={campos.codigo}
            onChange={(e) => alterar('codigo', e.target.value)}
          />
        )}
      </Campo>
      <Campo
        label="Período letivo"
        erro={erros.periodoLetivoId}
        obrigatorio
        dica={opcoesPeriodo.length === 0 ? 'Cadastre um período letivo antes.' : undefined}
      >
        {(props) => (
          <Select<string>
            {...props}
            name="periodoLetivoId"
            opcoes={opcoesPeriodo}
            valor={campos.periodoLetivoId}
            onChange={(id) => alterar('periodoLetivoId', id)}
          />
        )}
      </Campo>
      <Campo
        label="Curso"
        erro={erros.cursoId}
        obrigatorio
        dica={opcoesCurso.length === 0 ? 'Cadastre um curso antes.' : undefined}
      >
        {(props) => (
          <Select<string> {...props} name="cursoId" opcoes={opcoesCurso} valor={campos.cursoId} onChange={trocarCurso} />
        )}
      </Campo>
      <Campo label="Turno" erro={erros.turno} obrigatorio>
        {(props) => (
          <Select<Turno | ''>
            {...props}
            name="turno"
            opcoes={OPCOES_TURNO}
            valor={campos.turno}
            onChange={(turno) => alterar('turno', turno)}
          />
        )}
      </Campo>
      <Campo label="Vagas" erro={erros.vagas} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="vagas"
            inputMode="numeric"
            placeholder="Ex.: 35"
            value={campos.vagas}
            onChange={(e) => alterar('vagas', mascararInteiro(e.target.value, 4))}
          />
        )}
      </Campo>

      <fieldset className={styles.grade}>
        <legend className={styles.gradeTitulo}>Disciplinas da grade</legend>
        {!campos.cursoId && <p className={styles.secundario}>Escolha o curso para ver as disciplinas.</p>}
        {campos.cursoId && disciplinasDoCurso.length === 0 && (
          <p className={styles.secundario}>Nenhuma disciplina ativa para este curso.</p>
        )}
        {disciplinasDoCurso.length > 0 && (
          <div className={styles.opcoesGrade}>
            {disciplinasDoCurso.map((d) => (
              <label key={d.id} className={styles.opcaoGrade}>
                <input
                  type="checkbox"
                  checked={campos.disciplinaIds.includes(d.id)}
                  onChange={() => alternarDisciplina(d.id)}
                />
                <span>{d.nome}</span>
                <span className={styles.subtexto}>{d.cargaHoraria} h</span>
              </label>
            ))}
          </div>
        )}
      </fieldset>
    </ModalCadastro>
  )
}
