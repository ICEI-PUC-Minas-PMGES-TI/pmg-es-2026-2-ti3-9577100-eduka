import { useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import iconeEduka from '../../assets/eduka-icone.svg'
import { paths } from '../../routes/paths'
import { limparSessao } from '../../services/authService'
import { iniciais } from '../../utils/formatters'
import { Button } from '../Button/Button'
import { Icon, type IconName } from '../Icon/Icon'
import { Modal } from '../Modal/Modal'
import styles from './PainelLayout.module.css'

/** Item do menu flutuante; sem `to` ainda não tem página e aparece desabilitado. */
export type ItemMenu = {
  label: string
  icon: IconName
  to?: string
}

type PainelLayoutProps = {
  /** Área na linha da marca, depois de "eduka ·" (ex.: "Secretaria"). */
  contexto: string
  titulo: string
  subtitulo?: ReactNode
  /** Botão principal da tela, ao lado de "Ajuda" e "Sair". */
  acao?: ReactNode
  /** Link de volta, exibido no lugar da linha da marca. */
  voltar?: ReactNode
  /** Sem itens, a tela fica sem menu flutuante. */
  menu?: ItemMenu[]
  /** Rótulo acessível do menu (ex.: "Navegação da secretaria"). */
  menuRotulo?: string
  /** Texto do diálogo de ajuda; sem ele, "Ajuda" aparece desabilitado. */
  ajuda?: ReactNode
  /** Para onde o "Sair" leva; o padrão é o login da escola. */
  rotaSaida?: string
  /** Quem está logado, em um card antes de "Ajuda" (ex.: o login e o perfil). */
  usuario?: { nome: string; papel: string }
  children: ReactNode
}

/**
 * Casca das telas logadas de todos os perfis: cabeçalho com a marca, título,
 * "Ajuda" e "Sair", o conteúdo e o menu flutuante no rodapé.
 */
export function PainelLayout({
  contexto,
  titulo,
  subtitulo,
  acao,
  voltar,
  menu = [],
  menuRotulo,
  ajuda,
  rotaSaida = paths.login,
  usuario,
  children,
}: PainelLayoutProps) {
  const navigate = useNavigate()
  const [ajudaAberta, setAjudaAberta] = useState(false)
  const temMenu = menu.length > 0

  // JWT e stateless: basta descartar o token local. replace impede que o
  // "voltar" reabra o painel.
  function sair() {
    limparSessao()
    navigate(rotaSaida, { replace: true })
  }

  return (
    <div className={`${styles.shell} ${temMenu ? styles.comMenu : ''}`}>
      <header className={styles.header}>
        <div className={styles.texto}>
          {voltar ?? (
            <div className={styles.brand}>
              <img src={iconeEduka} alt="" className={styles.logoIcon} width={26} height={26} />
              <span className={styles.logo}>eduka</span>
              <span className={styles.contexto}> · {contexto}</span>
            </div>
          )}
          <h1 className={styles.title}>{titulo}</h1>
          {subtitulo && <p className={styles.subtitle}>{subtitulo}</p>}
        </div>

        <div className={styles.acoes}>
          {acao}
          {usuario && (
            <div className={styles.usuario}>
              <span className={styles.avatar} aria-hidden="true">
                {iniciais(usuario.nome)}
              </span>
              <span className={styles.usuarioTexto}>
                <strong>{usuario.nome}</strong>
                <span>{usuario.papel}</span>
              </span>
            </div>
          )}
          <button
            type="button"
            className={styles.pilula}
            disabled={!ajuda}
            title={ajuda ? 'Ajuda e orientações' : 'Em breve'}
            onClick={() => setAjudaAberta(true)}
          >
            <Icon name="help" size={17} />
            Ajuda
          </button>
          <button type="button" className={styles.pilula} onClick={sair}>
            <Icon name="logout" size={17} />
            Sair
          </button>
        </div>
      </header>

      <main className={styles.main}>{children}</main>

      {temMenu && (
        <nav className={styles.nav} aria-label={menuRotulo}>
          {menu.map((item) =>
            item.to ? (
              <NavLink
                key={item.label}
                to={item.to}
                className={({ isActive }) => `${styles.item} ${isActive ? styles.active : ''}`}
              >
                <Icon name={item.icon} size={16} />
                <span>{item.label}</span>
              </NavLink>
            ) : (
              <button key={item.label} type="button" className={styles.item} disabled title="Em breve">
                <Icon name={item.icon} size={16} />
                <span>{item.label}</span>
              </button>
            ),
          )}
        </nav>
      )}

      {ajudaAberta && (
        <Modal
          titulo="Precisa de ajuda?"
          tamanho="pequeno"
          onFechar={() => setAjudaAberta(false)}
          rodape={<Button onClick={() => setAjudaAberta(false)}>Fechar</Button>}
        >
          <div className={styles.ajuda}>{ajuda}</div>
        </Modal>
      )}
    </div>
  )
}
