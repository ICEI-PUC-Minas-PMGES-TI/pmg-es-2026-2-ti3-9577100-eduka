import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Modal } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { criarCategoriaDespesa } from '../../../../services/despesaService'
import type { CategoriaDespesa } from '../../../../types/despesa'
import styles from './ModalCategorias.module.css'

// Limite da coluna NOME (V31).
const MAX_NOME = 80

type ModalCategoriasProps = {
  categorias: CategoriaDespesa[]
  onCriada: (categoria: CategoriaDespesa) => void
  onFechar: () => void
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError && erro.status === 409) return 'A escola já tem uma categoria com esse nome.'
  if (erro instanceof ApiError) return erro.detalhe ?? erro.message
  return 'Erro inesperado ao salvar a categoria.'
}

/** Gerenciar categorias (IU21): as categorias de despesa da escola e o cadastro de uma nova. */
export function ModalCategorias({ categorias, onCriada, onFechar }: ModalCategoriasProps) {
  const [nome, setNome] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setSucesso(null)
    const limpo = nome.trim()
    if (!limpo) {
      setErro('Informe o nome da categoria.')
      return
    }
    setErro(null)
    setSalvando(true)
    try {
      const criada = await criarCategoriaDespesa(limpo)
      onCriada(criada)
      setNome('')
      setSucesso(`Categoria "${criada.nome}" criada.`)
    } catch (e) {
      setErro(mensagemErro(e))
    } finally {
      setSalvando(false)
    }
  }

  const ordenadas = [...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))

  return (
    <Modal
      titulo="Categorias de despesa"
      subtitulo="As categorias aparecem no lançamento e nos relatórios."
      tamanho="medio"
      ocupado={salvando}
      onFechar={onFechar}
      onEnviar={enviar}
      rodape={
        <>
          <Button onClick={onFechar} disabled={salvando}>
            Fechar
          </Button>
          <Button type="submit" variant="primary" icon="plus" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Adicionar categoria'}
          </Button>
        </>
      }
    >
      <ul className={styles.lista} aria-label="Categorias da escola">
        {ordenadas.map((categoria) => (
          <li key={categoria.id}>{categoria.nome}</li>
        ))}
      </ul>
      <label htmlFor="nova-categoria" className={styles.rotulo}>
        Nova categoria
      </label>
      <input
        id="nova-categoria"
        className={styles.campo}
        maxLength={MAX_NOME}
        placeholder="Ex.: Transporte"
        value={nome}
        aria-invalid={erro !== null}
        onChange={(e) => {
          setNome(e.target.value)
          setErro(null)
        }}
      />
      {erro && (
        <p className={styles.erro} role="alert">
          {erro}
        </p>
      )}
      {sucesso && (
        <p className={styles.sucesso} role="status">
          {sucesso}
        </p>
      )}
    </Modal>
  )
}
