import { useState } from 'react'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Campo } from '../../../../../components/formulario/Campo/Campo'
import { ModalDetalhes } from '../../../../../components/modal/ModalDetalhes/ModalDetalhes'
import type { Curso, CursoRequest } from '../../../../../types/academico/academico'
import { AcoesLinha, CabecalhoAba, Tabela } from './comum'
import { ConfirmarExclusao } from '../../../../../components/modal/ConfirmarExclusao/ConfirmarExclusao'
import { mensagemErroAcademico } from './erros'
import { opcional } from '../../../../../utils/formatacao'
import { ModalCadastro } from '../../../../../components/modal/ModalCadastro/ModalCadastro'
import styles from './PainelSecretaria.module.css'

// Limites das colunas de CURSO (V14).
const MAX_NOME = 150
const MAX_CODIGO = 50
const MAX_DESCRICAO = 500

type CursosProps = {
  cursos: Curso[]
  onCadastrar: (dados: CursoRequest) => Promise<Curso>
  onAtualizar: (id: number, dados: CursoRequest) => Promise<Curso>
  onExcluir: (id: number) => Promise<void>
}

/** Formulário aberto: cadastro (sem curso) ou edição. */
type Formulario = { aberto: false } | { aberto: true; curso?: Curso }

export function Cursos({ cursos, onCadastrar, onAtualizar, onExcluir }: CursosProps) {
  const [formulario, setFormulario] = useState<Formulario>({ aberto: false })
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [vendo, setVendo] = useState<Curso | null>(null)
  const [excluindo, setExcluindo] = useState<Curso | null>(null)

  function abrir(curso?: Curso) {
    setSucesso(null)
    setFormulario({ aberto: true, curso })
  }

  const editando = formulario.aberto ? formulario.curso : undefined

  return (
    <>
      <CabecalhoAba titulo="Cursos" rotuloNovo="Novo curso" onNovo={() => abrir()} sucesso={sucesso} />

      <Tabela
        colunas={['Código', 'Nome do curso', 'Descrição', 'Situação', '']}
        vazio="Nenhum curso cadastrado ainda."
        rotuloPaginacao="Páginas dos cursos"
        linhas={cursos.map((c) => (
          <tr key={c.id}>
            <td className={styles.secundario}>{c.codigo ?? '—'}</td>
            <td className={styles.destaque}>{c.nome}</td>
            <td className={styles.secundario}>{c.descricao ?? '—'}</td>
            <td>
              <Badge tone={c.ativo ? 'success' : 'neutral'}>{c.ativo ? 'Ativo' : 'Inativo'}</Badge>
            </td>
            <td className={styles.acoes}>
              <AcoesLinha
                nome={c.nome}
                onVer={() => setVendo(c)}
                onEditar={() => abrir(c)}
                onExcluir={() => {
                  setSucesso(null)
                  setExcluindo(c)
                }}
              />
            </td>
          </tr>
        ))}
      />

      {vendo && (
        <ModalDetalhes
          titulo={vendo.nome}
          subtitulo={<Badge tone={vendo.ativo ? 'success' : 'neutral'}>{vendo.ativo ? 'Ativo' : 'Inativo'}</Badge>}
          onFechar={() => setVendo(null)}
          secoes={[
            {
              itens: [
                { rotulo: 'Código', valor: vendo.codigo },
                { rotulo: 'Nome do curso', valor: vendo.nome },
                { rotulo: 'Descrição', valor: vendo.descricao, largo: true },
              ],
            },
          ]}
        />
      )}

      {formulario.aberto && (
        <FormularioCurso
          key={editando?.id ?? 'novo'}
          curso={editando}
          onFechar={() => setFormulario({ aberto: false })}
          onSalvar={async (dados) => {
            const salvo = editando ? await onAtualizar(editando.id, dados) : await onCadastrar(dados)
            setFormulario({ aberto: false })
            setSucesso(editando ? `Curso "${salvo.nome}" atualizado.` : `Curso "${salvo.nome}" cadastrado.`)
          }}
        />
      )}

      {excluindo && (
        <ConfirmarExclusao
          traduzirErro={mensagemErroAcademico}
          titulo="Excluir curso"
          nome={excluindo.nome}
          onFechar={() => setExcluindo(null)}
          onConfirmar={async () => {
            await onExcluir(excluindo.id)
            setExcluindo(null)
            setSucesso(`Curso "${excluindo.nome}" excluído.`)
          }}
        />
      )}
    </>
  )
}

type Campos = { codigo: string; nome: string; descricao: string }

function FormularioCurso({
  curso,
  onFechar,
  onSalvar,
}: {
  /** Sem curso é cadastro; com curso, edição com os campos preenchidos. */
  curso?: Curso
  onFechar: () => void
  onSalvar: (dados: CursoRequest) => Promise<void>
}) {
  const [campos, setCampos] = useState<Campos>({
    codigo: curso?.codigo ?? '',
    nome: curso?.nome ?? '',
    descricao: curso?.descricao ?? '',
  })
  const [erroNome, setErroNome] = useState<string | undefined>()

  function alterar<K extends keyof Campos>(campo: K, valor: string) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (campo === 'nome') setErroNome(undefined)
  }

  function validar(): boolean {
    const erro = campos.nome.trim() ? undefined : 'Informe o nome do curso.'
    setErroNome(erro)
    return !erro
  }

  return (
    <ModalCadastro
      titulo={curso ? 'Editar curso' : 'Novo curso'}
      rotuloSalvar={curso ? 'Salvar alterações' : 'Salvar curso'}
      onFechar={onFechar}
      validar={validar}
      traduzirErro={mensagemErroAcademico}
      onEnviar={() =>
        onSalvar({ nome: campos.nome.trim(), codigo: opcional(campos.codigo), descricao: opcional(campos.descricao) })
      }
    >
      <Campo label="Código">
        {(props) => (
          <input
            {...props}
            name="codigo"
            maxLength={MAX_CODIGO}
            placeholder="Ex.: EF-ANOS-FINAIS"
            value={campos.codigo}
            onChange={(e) => alterar('codigo', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Nome do curso" erro={erroNome} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="nome"
            maxLength={MAX_NOME}
            placeholder="Ex.: Ensino Fundamental II"
            value={campos.nome}
            onChange={(e) => alterar('nome', e.target.value)}
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
