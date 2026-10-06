import { useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Campo } from '../../../../components/Campo/Campo'
import type { Curso, CursoRequest } from '../../../../types/academico'
import { CabecalhoAba, Tabela } from './comum'
import { opcional } from './formatacao'
import { ModalCadastro } from './ModalCadastro'
import styles from './PainelSecretaria.module.css'

// Limites das colunas de CURSO (V14).
const MAX_NOME = 150
const MAX_CODIGO = 50
const MAX_DESCRICAO = 500

type CursosProps = {
  cursos: Curso[]
  onCadastrar: (dados: CursoRequest) => Promise<Curso>
}

export function Cursos({ cursos, onCadastrar }: CursosProps) {
  const [modalAberto, setModalAberto] = useState(false)
  const [sucesso, setSucesso] = useState<string | null>(null)

  function abrir() {
    setSucesso(null)
    setModalAberto(true)
  }

  return (
    <>
      <CabecalhoAba titulo="Cursos" rotuloNovo="Novo curso" onNovo={abrir} sucesso={sucesso} />

      <Tabela
        colunas={['Código', 'Nome do curso', 'Descrição', 'Situação']}
        vazio="Nenhum curso cadastrado ainda."
        linhas={cursos.map((c) => (
          <tr key={c.id}>
            <td className={styles.secundario}>{c.codigo ?? '—'}</td>
            <td className={styles.destaque}>{c.nome}</td>
            <td className={styles.secundario}>{c.descricao ?? '—'}</td>
            <td>
              <Badge tone={c.ativo ? 'success' : 'neutral'}>{c.ativo ? 'Ativo' : 'Inativo'}</Badge>
            </td>
          </tr>
        ))}
      />

      {modalAberto && (
        <CadastroCurso
          onFechar={() => setModalAberto(false)}
          onSalvar={async (dados) => {
            const criado = await onCadastrar(dados)
            setModalAberto(false)
            setSucesso(`Curso "${criado.nome}" cadastrado.`)
          }}
        />
      )}
    </>
  )
}

type Campos = { codigo: string; nome: string; descricao: string }

function CadastroCurso({ onFechar, onSalvar }: { onFechar: () => void; onSalvar: (dados: CursoRequest) => Promise<void> }) {
  const [campos, setCampos] = useState<Campos>({ codigo: '', nome: '', descricao: '' })
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
      titulo="Novo curso"
      rotuloSalvar="Salvar curso"
      onFechar={onFechar}
      validar={validar}
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
