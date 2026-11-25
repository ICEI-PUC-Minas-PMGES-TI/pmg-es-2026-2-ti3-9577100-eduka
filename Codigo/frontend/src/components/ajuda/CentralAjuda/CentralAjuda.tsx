import { Fragment, useEffect, useState, type ReactNode } from 'react'
import { EMAIL_SUPORTE, mailtoSuporte } from '../../../config/suporte'
import { buscarContatoDaEscola } from '../../../services/escola/contatoEscolaService'
import type { ContatoEscola } from '../../../types/escola/contatoEscola'
import { mascararTelefone, somenteDigitos } from '../../../utils/formatters'
import { Icon, type IconName } from '../../ui/Icon/Icon'
import {
  ESCOLA_RESOLVE,
  ROTULO_PERFIL,
  TITULO_ESCOLA,
  edukaResolve,
  perguntasFrequentes,
  procuraAEscola,
  type PerfilDaAjuda,
} from './conteudo'
import styles from './CentralAjuda.module.css'

type CentralAjudaProps = {
  /** Perfil logado; `null` nas telas abertas (login, página inicial, 404...). */
  perfil: PerfilDaAjuda
  /** Título da tela atual: vai no e-mail ao suporte para ninguém precisar explicar onde estava. */
  tela?: string
  /** Orientações da área (ex.: o que cada tela do financeiro faz). */
  dicas?: ReactNode
}

/**
 * Conteúdo da Ajuda, em todas as telas: as orientações da área, a quem
 * recorrer na própria escola (com o contato real dela), quando chamar o
 * suporte do Eduka e as perguntas frequentes do perfil.
 */
export function CentralAjuda({ perfil, tela, dicas }: CentralAjudaProps) {
  const rotulo = perfil ? ROTULO_PERFIL[perfil] : null
  const assunto = `Ajuda no Eduka${rotulo ? ` · ${rotulo}` : ''}${tela ? ` · ${tela}` : ''}`
  const corpo = [
    `Tela: ${tela ?? document.title}`,
    `Endereço: ${window.location.href}`,
    '',
    'O que aconteceu:',
    '',
  ].join('\n')

  return (
    <div className={styles.central}>
      {dicas && (
        <Secao icone="info" titulo="Nesta área">
          <div className={styles.dicas}>{dicas}</div>
        </Secao>
      )}

      {procuraAEscola(perfil) ? (
        <Secao icone="home" titulo={TITULO_ESCOLA[perfil]} destaque>
          <ContatoDaEscola />
          <p className={styles.legenda}>Procure a escola para:</p>
          <Lista itens={ESCOLA_RESOLVE[perfil]} />
        </Secao>
      ) : (
        perfil === null && (
          <Secao icone="graduationCap" titulo="É aluno, professor ou funcionário de uma escola?" destaque>
            <p className={styles.texto}>
              Senha provisória, e-mail de acesso e dados cadastrais são da sua escola: fale com a secretaria dela
              primeiro, que resolve mais rápido.
            </p>
          </Secao>
        )
      )}

      <Secao icone="help" titulo="Suporte da plataforma Eduka">
        {procuraAEscola(perfil) && (
          <p className={styles.texto}>
            Para dúvidas sobre a escola, fale primeiro com ela. O suporte do Eduka cuida do sistema:
          </p>
        )}
        <Lista itens={edukaResolve(perfil)} />
        <a className={styles.botaoSuporte} href={mailtoSuporte(assunto, corpo)}>
          <Icon name="mail" size={16} />
          Escrever para o suporte
        </a>
        <p className={styles.legenda}>
          ou envie um e-mail para <strong>{EMAIL_SUPORTE}</strong> contando a tela e o que aconteceu.
        </p>
      </Secao>

      <Secao icone="list" titulo="Perguntas frequentes">
        <div className={styles.perguntas}>
          {perguntasFrequentes(perfil).map((p) => (
            <details key={p.pergunta} className={styles.pergunta}>
              <summary>
                {p.pergunta}
                <Icon name="chevronDown" size={16} />
              </summary>
              <p>{p.resposta}</p>
            </details>
          ))}
        </div>
      </Secao>
    </div>
  )
}

/** Busca o contato quando a Ajuda abre: é pequeno e muda pouco, mas a escola pode ter acabado de atualizar. */
function ContatoDaEscola() {
  const [contato, setContato] = useState<ContatoEscola | null>(null)
  const [situacao, setSituacao] = useState<'carregando' | 'pronto' | 'erro'>('carregando')

  useEffect(() => {
    const controller = new AbortController()
    buscarContatoDaEscola(controller.signal)
      .then((dados) => {
        setContato(dados)
        setSituacao('pronto')
      })
      .catch(() => {
        if (!controller.signal.aborted) setSituacao('erro')
      })
    return () => controller.abort()
  }, [])

  if (situacao === 'carregando') {
    return (
      <p className={styles.contatoVazio} role="status">
        Carregando o contato da escola…
      </p>
    )
  }
  if (situacao === 'erro' || !contato) {
    return (
      <p className={styles.contatoVazio}>
        Não foi possível carregar o contato da escola agora. Procure a secretaria pessoalmente ou tente de novo mais
        tarde.
      </p>
    )
  }

  const telefone = contato.telefone ? somenteDigitos(contato.telefone) : null
  const local = [contato.cidade, contato.estado].filter(Boolean).join(' · ')
  const semCanal = !contato.email && !telefone

  return (
    <div className={styles.contato} aria-label={`Contato de ${contato.nome}`}>
      <div className={styles.contatoTopo}>
        <strong>{contato.nome}</strong>
        {local && <span>{local}</span>}
      </div>
      {semCanal ? (
        <p className={styles.contatoVazio}>
          A escola ainda não cadastrou e-mail nem telefone. Procure a secretaria pessoalmente.
        </p>
      ) : (
        <div className={styles.canais}>
          {contato.email && (
            <a className={styles.canal} href={`mailto:${contato.email}`} title={contato.email}>
              <Icon name="mail" size={16} />
              <span className={styles.canalTexto}>
                <small>E-mail</small>
                <span className={styles.canalValor}>{emailQuebravel(contato.email)}</span>
              </span>
            </a>
          )}
          {telefone && (
            <a className={styles.canal} href={`tel:+55${telefone}`}>
              <Icon name="phone" size={16} />
              <span className={styles.canalTexto}>
                <small>Telefone</small>
                <span className={styles.canalValor}>{mascararTelefone(telefone)}</span>
              </span>
            </a>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * E-mail longo é uma palavra só: sem ponto de quebra, ele vaza do cartão.
 * Marca antes do "@" e depois de cada "." como os lugares preferidos para
 * quebrar a linha; se um pedaço ainda não couber, o CSS quebra onde precisar.
 */
function emailQuebravel(email: string): ReactNode {
  const pedacos = email.split(/(?=@)|(?<=\.)/)
  return pedacos.map((pedaco, i) => (
    <Fragment key={i}>
      {i > 0 && <wbr />}
      {pedaco}
    </Fragment>
  ))
}

function Secao({
  icone,
  titulo,
  destaque = false,
  children,
}: {
  icone: IconName
  titulo: string
  destaque?: boolean
  children: ReactNode
}) {
  return (
    <section className={`${styles.secao} ${destaque ? styles.secaoDestaque : ''}`} aria-label={titulo}>
      <h3 className={styles.secaoTitulo}>
        <span className={styles.secaoIcone} aria-hidden="true">
          <Icon name={icone} size={15} />
        </span>
        {titulo}
      </h3>
      {children}
    </section>
  )
}

function Lista({ itens }: { itens: string[] }) {
  return (
    <ul className={styles.lista}>
      {itens.map((item) => (
        <li key={item}>
          <Icon name="check" size={14} />
          {item}
        </li>
      ))}
    </ul>
  )
}
