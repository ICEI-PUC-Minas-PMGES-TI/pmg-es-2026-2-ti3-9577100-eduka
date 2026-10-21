import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Modal } from '../../../../components/Modal/Modal'
import type { NovaCobrancaRequest } from '../../../../types/cobranca'
import styles from './ModalNovaCobranca.module.css'

type ModalNovaCobrancaProps = {
  aberto: boolean
  onFechar: () => void
  onSalvar: (dados: NovaCobrancaRequest) => Promise<void>
}

export function ModalNovaCobranca({ aberto, onFechar, onSalvar }: ModalNovaCobrancaProps) {
  const [alunoNome, setAlunoNome] = useState('')
  const [descricao, setDescricao] = useState('')
  const [valor, setValor] = useState('')
  const [dataVencimento, setDataVencimento] = useState('')
  const [responsavelNome, setResponsavelNome] = useState('')
  const [salvando, setSalvando] = useState(false)

  if (!aberto) return null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!alunoNome || !descricao || !valor || !dataVencimento || !responsavelNome) return

    const valorNumerico = parseFloat(valor.replace(/\./g, '').replace(',', '.'))
    if (isNaN(valorNumerico) || valorNumerico <= 0) return

    setSalvando(true)
    try {
      await onSalvar({
        alunoNome: alunoNome.trim(),
        descricao: descricao.trim(),
        valor: valorNumerico,
        dataVencimento,
        responsavelNome: responsavelNome.trim(),
      })
      onFechar()
    } finally {
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo="Nova Cobrança"
      subtitulo="Emita uma nova mensalidade, taxa ou cobrança para um aluno."
      tamanho="medio"
      onFechar={onFechar}
      onEnviar={handleSubmit}
      ocupado={salvando}
      rodape={
        <div className={styles.rodapeBotoes}>
          <Button variant="outline" type="button" onClick={onFechar} disabled={salvando}>
            Cancelar
          </Button>
          <Button variant="primary" type="submit" disabled={salvando}>
            {salvando ? 'Emitindo…' : 'Emitir cobrança'}
          </Button>
        </div>
      }
    >
      <div className={styles.formulario}>
        <div className={styles.campo}>
          <label className={styles.label} htmlFor="alunoNome">
            Nome do aluno *
          </label>
          <input
            id="alunoNome"
            type="text"
            className={styles.input}
            placeholder="Ex: Lucas Andrade"
            required
            value={alunoNome}
            onChange={(e) => setAlunoNome(e.target.value)}
          />
        </div>

        <div className={styles.campo}>
          <label className={styles.label} htmlFor="descricaoCobranca">
            Descrição da cobrança *
          </label>
          <input
            id="descricaoCobranca"
            type="text"
            className={styles.input}
            placeholder="Ex: Mensalidade de novembro"
            required
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
          />
        </div>

        <div className={styles.gridDuplo}>
          <div className={styles.campo}>
            <label className={styles.label} htmlFor="valor">
              Valor (R$) *
            </label>
            <input
              id="valor"
              type="text"
              className={styles.input}
              placeholder="Ex: 890,00"
              required
              value={valor}
              onChange={(e) => setValor(e.target.value)}
            />
          </div>

          <div className={styles.campo}>
            <label className={styles.label} htmlFor="dataVencimento">
              Data de vencimento *
            </label>
            <input
              id="dataVencimento"
              type="date"
              className={styles.input}
              required
              value={dataVencimento}
              onChange={(e) => setDataVencimento(e.target.value)}
            />
          </div>
        </div>

        <div className={styles.campo}>
          <label className={styles.label} htmlFor="responsavelNome">
            Responsável financeiro *
          </label>
          <input
            id="responsavelNome"
            type="text"
            className={styles.input}
            placeholder="Ex: Cláudia Andrade"
            required
            value={responsavelNome}
            onChange={(e) => setResponsavelNome(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  )
}

