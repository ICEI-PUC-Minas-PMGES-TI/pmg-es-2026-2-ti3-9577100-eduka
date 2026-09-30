import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import type { Professor } from '../../../../types/professor'
import { ProfessoresTable } from '../../components/ProfessoresTable/ProfessoresTable'
import { useProfessores } from '../../hooks/useProfessores'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Professores.module.css'

// Por enquanto só pelo nome: as disciplinas ainda não vêm na listagem.
function correspondeBusca(professor: Professor, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return professor.nome.toLowerCase().includes(termo)
}

export function Professores() {
  const { professores, carregando, erro, recarregar } = useProfessores()
  const [busca, setBusca] = useState('')

  const visiveis = professores.filter((p) => correspondeBusca(p, busca))
  const mensagemVazia =
    professores.length === 0
      ? 'Nenhum professor cadastrado ainda.'
      : 'Nenhum professor encontrado para a busca.'

  return (
    <SecretariaLayout
      titulo="Professores"
      subtitulo="Cadastre os professores e veja onde cada um dá aula."
    >
      <div className={styles.toolbar}>
        <label className={styles.busca}>
          <Icon name="search" size={17} />
          <input
            type="search"
            placeholder="Procurar pelo nome ou disciplina"
            aria-label="Buscar professor pelo nome ou disciplina"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </label>
        <Button variant="primary" icon="plus" disabled title="Em breve">
          Novo professor
        </Button>
      </div>

      <ProfessoresTable
        professores={visiveis}
        carregando={carregando}
        erro={erro}
        mensagemVazia={mensagemVazia}
        onTentarNovamente={recarregar}
      />
    </SecretariaLayout>
  )
}
