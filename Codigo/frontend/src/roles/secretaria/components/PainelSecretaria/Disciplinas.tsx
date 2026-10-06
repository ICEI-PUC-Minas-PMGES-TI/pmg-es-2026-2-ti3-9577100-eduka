import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { Select } from '../../../../components/Select/Select'
import type { Curso, Disciplina, DisciplinaRequest } from '../../../../types/academico'
import { mascararInteiro } from '../../../../utils/formatters'
import { CabecalhoAba, Tabela } from './comum'
import { opcional } from './formatacao'
import { ModalCadastro } from './ModalCadastro'
import styles from './PainelSecretaria.module.css'

// Limites das colunas de DISCIPLINA (V14).
const MAX_NOME = 150
const MAX_CODIGO = 50
const MAX_DESCRICAO = 500

type DisciplinasProps = {
  disciplinas: Disciplina[]
  cursos: Curso[]
  onCadastrar: (dados: DisciplinaRequest) => Promise<Disciplina>
}

export function Disciplinas({ disciplinas, cursos, onCadastrar }: DisciplinasProps) {
  const [modalAberto, setModalAberto] = useState(false)
  const [sucesso, setSucesso] = useState<string | null>(null)

  function abrir() {
    setSucesso(null)
    setModalAberto(true)
  }

  return (
    <>
      <CabecalhoAba titulo="Disciplinas" rotuloNovo="Nova disciplina" onNovo={abrir} sucesso={sucesso} />

      <Tabela
        colunas={['Código', 'Nome', 'Carga horária', 'Curso vinculado']}
        vazio="Nenhuma disciplina cadastrada ainda."
        linhas={disciplinas.map((d) => (
          <tr key={d.id}>
            <td className={styles.secundario}>{d.codigo ?? '—'}</td>
            <td className={styles.destaque}>{d.nome}</td>
            <td>{d.cargaHoraria} h</td>
            <td className={d.cursoNome ? undefined : styles.secundario}>{d.cursoNome ?? 'Todos os cursos'}</td>
          </tr>
        ))}
      />

      {modalAberto && (
        <CadastroDisciplina
          cursos={cursos}
          onFechar={() => setModalAberto(false)}
          onSalvar={async (dados) => {
            const criada = await onCadastrar(dados)
            setModalAberto(false)
            setSucesso(`Disciplina "${criada.nome}" cadastrada.`)
          }}
        />
      )}
    </>
  )
}

type Campos = { codigo: string; nome: string; cargaHoraria: string; cursoId: string; descricao: string }
type Erros = Partial<Record<'nome' | 'cargaHoraria', string>>

function CadastroDisciplina({
  cursos,
  onFechar,
  onSalvar,
}: {
  cursos: Curso[]
  onFechar: () => void
  onSalvar: (dados: DisciplinaRequest) => Promise<void>
}) {
  const [campos, setCampos] = useState<Campos>({ codigo: '', nome: '', cargaHoraria: '', cursoId: '', descricao: '' })
  const [erros, setErros] = useState<Erros>({})

  // '' = disciplina sem curso, comum a todos.
  const opcoesCurso = [
    { valor: '', rotulo: 'Nenhum (comum a todos os cursos)' },
    ...cursos.filter((c) => c.ativo).map((c) => ({ valor: String(c.id), rotulo: c.nome })),
  ]

  function alterar<K extends keyof Campos>(campo: K, valor: string) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (campo in erros) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.nome.trim()) novos.nome = 'Informe o nome da disciplina.'
    if (!(Number(campos.cargaHoraria) > 0)) novos.cargaHoraria = 'Informe a carga horária em horas.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  return (
    <ModalCadastro
      titulo="Nova disciplina"
      rotuloSalvar="Salvar disciplina"
      onFechar={onFechar}
      validar={validar}
      onEnviar={() =>
        onSalvar({
          nome: campos.nome.trim(),
          codigo: opcional(campos.codigo),
          cargaHoraria: Number(campos.cargaHoraria),
          cursoId: campos.cursoId ? Number(campos.cursoId) : null,
          descricao: opcional(campos.descricao),
        })
      }
    >
      <Campo label="Código">
        {(props) => (
          <input
            {...props}
            name="codigo"
            maxLength={MAX_CODIGO}
            placeholder="Ex.: MAT-6"
            value={campos.codigo}
            onChange={(e) => alterar('codigo', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Nome da disciplina" erro={erros.nome} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="nome"
            maxLength={MAX_NOME}
            placeholder="Ex.: Matemática"
            value={campos.nome}
            onChange={(e) => alterar('nome', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Carga horária (horas)" erro={erros.cargaHoraria} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="cargaHoraria"
            inputMode="numeric"
            placeholder="Ex.: 80"
            value={campos.cargaHoraria}
            onChange={(e) => alterar('cargaHoraria', mascararInteiro(e.target.value, 4))}
          />
        )}
      </Campo>
      <Campo label="Curso vinculado">
        {(props) => (
          <Select<string>
            {...props}
            name="cursoId"
            opcoes={opcoesCurso}
            valor={campos.cursoId}
            onChange={(cursoId) => alterar('cursoId', cursoId)}
          />
        )}
      </Campo>
      <Campo label="Descrição" largo>
        {(props) => (
          <textarea
            {...props}
            name="descricao"
            rows={3}
            maxLength={MAX_DESCRICAO}
            value={campos.descricao}
            onChange={(e) => alterar('descricao', e.target.value)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
