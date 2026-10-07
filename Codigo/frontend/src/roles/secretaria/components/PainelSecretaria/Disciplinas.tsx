import { useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Campo } from '../../../../components/Campo/Campo'
import { ModalDetalhes } from '../../../../components/ModalDetalhes/ModalDetalhes'
import { Select } from '../../../../components/Select/Select'
import type { Curso, Disciplina, DisciplinaRequest } from '../../../../types/academico'
import { mascararInteiro } from '../../../../utils/formatters'
import { AcoesLinha, CabecalhoAba, Tabela } from './comum'
import { ConfirmarExclusao } from './ConfirmarExclusao'
import { mensagemErroAcademico } from './erros'
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
  onAtualizar: (id: number, dados: DisciplinaRequest) => Promise<Disciplina>
  onExcluir: (id: number) => Promise<void>
}

/** Formulário aberto: cadastro (sem disciplina) ou edição. */
type Formulario = { aberto: false } | { aberto: true; disciplina?: Disciplina }

export function Disciplinas({ disciplinas, cursos, onCadastrar, onAtualizar, onExcluir }: DisciplinasProps) {
  const [formulario, setFormulario] = useState<Formulario>({ aberto: false })
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [vendo, setVendo] = useState<Disciplina | null>(null)
  const [excluindo, setExcluindo] = useState<Disciplina | null>(null)

  function abrir(disciplina?: Disciplina) {
    setSucesso(null)
    setFormulario({ aberto: true, disciplina })
  }

  const editando = formulario.aberto ? formulario.disciplina : undefined

  return (
    <>
      <CabecalhoAba titulo="Disciplinas" rotuloNovo="Nova disciplina" onNovo={() => abrir()} sucesso={sucesso} />

      <Tabela
        colunas={['Código', 'Nome', 'Carga horária', 'Curso vinculado', '']}
        vazio="Nenhuma disciplina cadastrada ainda."
        linhas={disciplinas.map((d) => (
          <tr key={d.id}>
            <td className={styles.secundario}>{d.codigo ?? '—'}</td>
            <td className={styles.destaque}>{d.nome}</td>
            <td>{d.cargaHoraria} h</td>
            <td className={d.cursoNome ? undefined : styles.secundario}>{d.cursoNome ?? 'Todos os cursos'}</td>
            <td className={styles.acoes}>
              <AcoesLinha
                nome={d.nome}
                onVer={() => setVendo(d)}
                onEditar={() => abrir(d)}
                onExcluir={() => {
                  setSucesso(null)
                  setExcluindo(d)
                }}
              />
            </td>
          </tr>
        ))}
      />

      {vendo && (
        <ModalDetalhes
          titulo={vendo.nome}
          subtitulo={<Badge tone={vendo.ativa ? 'success' : 'neutral'}>{vendo.ativa ? 'Ativa' : 'Inativa'}</Badge>}
          onFechar={() => setVendo(null)}
          secoes={[
            {
              itens: [
                { rotulo: 'Código', valor: vendo.codigo },
                { rotulo: 'Carga horária', valor: `${vendo.cargaHoraria} h` },
                { rotulo: 'Curso vinculado', valor: vendo.cursoNome ?? 'Todos os cursos', largo: true },
                { rotulo: 'Descrição', valor: vendo.descricao, largo: true },
              ],
            },
          ]}
        />
      )}

      {formulario.aberto && (
        <FormularioDisciplina
          key={editando?.id ?? 'nova'}
          disciplina={editando}
          cursos={cursos}
          onFechar={() => setFormulario({ aberto: false })}
          onSalvar={async (dados) => {
            const salva = editando ? await onAtualizar(editando.id, dados) : await onCadastrar(dados)
            setFormulario({ aberto: false })
            setSucesso(editando ? `Disciplina "${salva.nome}" atualizada.` : `Disciplina "${salva.nome}" cadastrada.`)
          }}
        />
      )}

      {excluindo && (
        <ConfirmarExclusao
          titulo="Excluir disciplina"
          nome={excluindo.nome}
          onFechar={() => setExcluindo(null)}
          onConfirmar={async () => {
            await onExcluir(excluindo.id)
            setExcluindo(null)
            setSucesso(`Disciplina "${excluindo.nome}" excluída.`)
          }}
        />
      )}
    </>
  )
}

type Campos = { codigo: string; nome: string; cargaHoraria: string; cursoId: string; descricao: string }
type Erros = Partial<Record<'nome' | 'cargaHoraria', string>>

function FormularioDisciplina({
  disciplina,
  cursos,
  onFechar,
  onSalvar,
}: {
  /** Sem disciplina é cadastro; com disciplina, edição com os campos preenchidos. */
  disciplina?: Disciplina
  cursos: Curso[]
  onFechar: () => void
  onSalvar: (dados: DisciplinaRequest) => Promise<void>
}) {
  const [campos, setCampos] = useState<Campos>({
    codigo: disciplina?.codigo ?? '',
    nome: disciplina?.nome ?? '',
    cargaHoraria: disciplina ? String(disciplina.cargaHoraria) : '',
    cursoId: disciplina?.cursoId ? String(disciplina.cursoId) : '',
    descricao: disciplina?.descricao ?? '',
  })
  const [erros, setErros] = useState<Erros>({})

  // '' = disciplina sem curso, comum a todos.
  const opcoesCurso = [
    { valor: '', rotulo: 'Nenhum (comum a todos os cursos)' },
    // Na edição, o curso atual aparece mesmo se estiver inativo.
    ...cursos
      .filter((c) => c.ativo || String(c.id) === campos.cursoId)
      .map((c) => ({ valor: String(c.id), rotulo: c.nome })),
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
      titulo={disciplina ? 'Editar disciplina' : 'Nova disciplina'}
      rotuloSalvar={disciplina ? 'Salvar alterações' : 'Salvar disciplina'}
      onFechar={onFechar}
      validar={validar}
      traduzirErro={mensagemErroAcademico}
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
