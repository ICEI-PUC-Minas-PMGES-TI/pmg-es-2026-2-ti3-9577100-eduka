import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import iconeEduka from '../../../assets/eduka-icone.svg'
import { paths } from '../../../routes/paths'
import { EVENTO_ESCOLA_SUSPENSA, EVENTO_MODULO_NAO_CONTRATADO, rotaAcessoSuspenso } from '../../../services/api'
import { limparSessao, obterUsuarioLogado } from '../../../services/acesso/authService'
import { MODULO_DO_PERFIL, useModulos } from '../../../services/acesso/modulosSessao'
import type { ModuloEscola } from '../../../types/escola/escola'
import { AjudaContexto } from '../../ajuda/AjudaContexto'
import { CentralAjuda } from '../../ajuda/CentralAjuda/CentralAjuda'
import { AvisoSuspensao } from '../AvisoSuspensao/AvisoSuspensao'
import { iniciais } from '../../../utils/formatters'
import { Button } from '../../ui/Button/Button'
import { Icon, type IconName } from '../../ui/Icon/Icon'
import { Modal } from '../../modal/Modal/Modal'
import { Dica } from '../../ui/Dica/Dica'
import styles from './PainelLayout.module.css'

/** Item do menu flutuante; sem `to` ainda não tem página e aparece desabilitado. */
export type ItemMenu = {
  label: string
  icon: IconName
  /**
   * Página do item. Sem ela, o item aparece apagado com a dica "Em breve",
   * marcando o que ainda falta implementar (no celular, some por inteiro).
   */
  to?: string
  /** Módulo que a escola precisa ter contratado; sem ele, o item some. */
  modulo?: ModuloEscola
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
  /**
   * Orientações da área, no topo da Central de ajuda. A Central abre em toda
   * tela, com ou sem elas: contato da escola, suporte do Eduka e perguntas do perfil.
   */
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
  const temModulo = useModulos()
  const itens = menu.filter((item) => !item.modulo || temModulo(item.modulo))
  const temMenu = itens.length > 0
  const perfil = obterUsuarioLogado()?.perfil ?? null
  const ehMaster = perfil === 'MASTER'

  // A escola foi suspensa com a tela aberta: qualquer requisição volta
  // ESCOLA_SUSPENSA. Encerra a sessão e mostra o aviso (IU04).
  useEffect(() => {
    function aoSuspender(evento: Event) {
      limparSessao()
      navigate(rotaAcessoSuspenso((evento as CustomEvent<string | null>).detail), { replace: true })
    }
    window.addEventListener(EVENTO_ESCOLA_SUSPENSA, aoSuspender)
    return () => window.removeEventListener(EVENTO_ESCOLA_SUSPENSA, aoSuspender)
  }, [navigate])

  // O SUPER_ADMIN tirou o módulo do perfil (ex.: a gestão financeira, com o
  // FINANCEIRO logado): nada mais funciona. Encerra a sessão e explica no
  // login. Outro módulo só some do menu (useModulos).
  useEffect(() => {
    function aoPerderModulo(evento: Event) {
      const perfil = obterUsuarioLogado()?.perfil
      const modulo = (evento as CustomEvent<ModuloEscola>).detail
      if (!perfil || MODULO_DO_PERFIL[perfil] !== modulo) return
      limparSessao()
      navigate(`${paths.login}?${new URLSearchParams({ modulo })}`, { replace: true })
    }
    window.addEventListener(EVENTO_MODULO_NAO_CONTRATADO, aoPerderModulo)
    return () => window.removeEventListener(EVENTO_MODULO_NAO_CONTRATADO, aoPerderModulo)
  }, [navigate])

  function abrirAjuda() {
    setAjudaAberta(true)
  }

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
          <Dica texto="Contato da escola, suporte e dúvidas frequentes">
            <button type="button" className={styles.pilula} onClick={abrirAjuda} aria-haspopup="dialog">
              <Icon name="help" size={17} />
              Ajuda
            </button>
          </Dica>
          <button type="button" className={styles.pilula} onClick={sair}>
            <Icon name="logout" size={17} />
            Sair
          </button>
        </div>
      </header>

      <main className={styles.main}>
        {ehMaster && <AvisoSuspensao />}
        {/* Os indicadores de ajuda das telas (IndicadorAjuda) abrem a Central por aqui. */}
        <AjudaContexto.Provider value={abrirAjuda}>{children}</AjudaContexto.Provider>
      </main>

      {temMenu && (
        <nav className={styles.nav} aria-label={menuRotulo}>
          {itens.map((item) =>
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
              <Dica key={item.label} texto="Em breve">
                <button type="button" className={styles.item} disabled>
                  <Icon name={item.icon} size={16} />
                  <span>{item.label}</span>
                </button>
              </Dica>
            ),
          )}
        </nav>
      )}

      {ajudaAberta && (
        <Modal
          titulo="Central de ajuda"
          subtitulo={
            perfil === 'MASTER' || perfil === 'SUPER_ADMIN'
              ? 'Orientações, dúvidas frequentes e o suporte do Eduka.'
              : 'Fale com a sua escola ou com o suporte do Eduka.'
          }
          onFechar={() => setAjudaAberta(false)}
          rodape={<Button onClick={() => setAjudaAberta(false)}>Fechar</Button>}
        >
          <CentralAjuda perfil={perfil} tela={titulo} dicas={ajuda} />
        </Modal>
      )}
    </div>
  )
}
