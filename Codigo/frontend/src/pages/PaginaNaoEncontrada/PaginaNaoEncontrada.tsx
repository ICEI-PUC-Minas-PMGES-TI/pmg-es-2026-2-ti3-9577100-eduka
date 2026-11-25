import { Link } from 'react-router'
import styles from './PaginaNaoEncontrada.module.css'
import { BotaoAjudaFlutuante } from '../../components/ajuda/BotaoAjudaFlutuante/BotaoAjudaFlutuante'

export function PaginaNaoEncontrada() {
  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <strong className={styles.code}>404</strong>
        <h1 className={styles.title}>Página não encontrada</h1>
        <p className={styles.text}>O endereço acessado não existe ou foi movido.</p>
        <Link to="/" className={styles.link}>
          Voltar ao início
        </Link>
      </div>
      <BotaoAjudaFlutuante tela="Página não encontrada" />
    </main>
  )
}
