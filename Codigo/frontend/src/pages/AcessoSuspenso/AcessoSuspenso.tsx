import { Link, useSearchParams } from 'react-router'
import logoEduka from '../../assets/eduka-logo.svg'
import { Icon } from '../../components/Icon/Icon'
import { paths } from '../../routes/paths'
import styles from './AcessoSuspenso.module.css'

/** Canal de suporte do Edúka (o mesmo do contato da página inicial). */
export const EMAIL_SUPORTE = 'contato@eduka.com.br'

/**
 * Aviso de suspensão (IU04): o login de quem é de uma escola suspensa cai
 * aqui em vez de entrar no painel, e quem já estava logado vem para cá na
 * primeira requisição depois da suspensão. `?motivo=FATURA_EM_ATRASO` explica
 * que foi a fatura do Eduka em atraso.
 */
export function AcessoSuspenso() {
  const [params] = useSearchParams()
  const porFatura = params.get('motivo') === 'FATURA_EM_ATRASO'

  return (
    <main className={styles.page}>
      <img src={logoEduka} alt="Edúka" className={styles.logo} />
      <div className={styles.card}>
        <span className={styles.icone} aria-hidden="true">
          <Icon name="lock" size={28} />
        </span>
        <h1 className={styles.title}>Acesso temporariamente indisponível</h1>
        {porFatura ? (
          <p className={styles.text}>
            O acesso da sua escola ao Edúka foi suspenso porque uma fatura do Edúka está em atraso. A gestão da
            escola recebeu o aviso por e-mail. Para reativar, a gestão deve falar com o nosso suporte.
          </p>
        ) : (
          <p className={styles.text}>
            A assinatura da sua escola está suspensa. Fale com a administração da escola ou com o nosso suporte.
          </p>
        )}
        <Link to={paths.login} className={styles.link}>
          Voltar para o login
        </Link>
        <a
          className={styles.suporte}
          href={`mailto:${EMAIL_SUPORTE}?subject=${encodeURIComponent('Acesso suspenso no Edúka')}`}
        >
          <Icon name="mail" size={16} />
          Falar com o suporte técnico
        </a>
      </div>
    </main>
  )
}
