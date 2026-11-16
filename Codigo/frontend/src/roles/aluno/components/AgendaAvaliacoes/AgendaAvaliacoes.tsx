import type { ReactNode } from 'react'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon, type IconName } from '../../../../components/ui/Icon/Icon'
import { listarMinhasAvaliacoes } from '../../../../services/portal/portalAlunoService'
import { ROTULOS_TIPO_AVALIACAO, type AvaliacaoDoAluno, type TipoAvaliacao } from '../../../../types/portal/portalAluno'
import { formatarPontos, hojeIso, plural } from '../../../../utils/formatters'
import { atingiuMedia } from '../../../professor/notas'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { agruparPorDia, contarPorTipo, diaPorExtenso, diasAte, quando } from './agenda'
import styles from './AgendaAvaliacoes.module.css'

const NENHUMA: AvaliacaoDoAluno[] = []

const ICONE_DO_TIPO: Record<TipoAvaliacao, IconName> = {
  PROVA: 'fileText',
  TRABALHO: 'users',
  TAREFA: 'list',
}

/** Aba "Tarefas e provas" do boletim: o que vem pela frente e o que já foi feito. */
export function AgendaAvaliacoes() {
  const { dados: avaliacoes, carregando, erro, recarregar } = useDadosDoAluno(listarMinhasAvaliacoes, NENHUMA)

  // O backend já manda por data; as realizadas aparecem da mais recente para trás.
  const hoje = hojeIso()
  const proximas = avaliacoes.filter((a) => a.data >= hoje)
  const realizadas = avaliacoes.filter((a) => a.data < hoje).reverse()

  if (carregando) {
    return (
      <div className={styles.mensagem} role="status">
        Carregando suas avaliações…
      </div>
    )
  }

  if (erro) {
    return (
      <div className={styles.mensagem} role="alert">
        <span>{erro}</span>
        <Button size="sm" onClick={recarregar}>
          Tentar novamente
        </Button>
      </div>
    )
  }

  if (avaliacoes.length === 0) {
    return (
      <div className={styles.mensagem}>
        <strong>Nenhuma prova ou tarefa marcada.</strong>
        <span>Quando os professores cadastrarem avaliações, elas aparecem aqui.</span>
      </div>
    )
  }

  return (
    <>
      <SecaoAvaliacoes titulo="Próximas" vazio="Nada marcado daqui para frente." avaliacoes={proximas} resumo />
      <SecaoAvaliacoes titulo="Já realizadas" vazio="Nenhuma avaliação realizada ainda." avaliacoes={realizadas} />
    </>
  )
}

type SecaoAvaliacoesProps = {
  titulo: string
  vazio: string
  avaliacoes: AvaliacaoDoAluno[]
  /** Linha com quantas provas, trabalhos e tarefas e os pontos em jogo. */
  resumo?: boolean
  /** Botão no rodapé do painel (ex.: "Ver todas"). */
  acao?: ReactNode
}

/**
 * Painel de avaliações agrupadas por dia, com quanto falta para cada dia;
 * também usado no Início do aluno.
 */
export function SecaoAvaliacoes({ titulo, vazio, avaliacoes, resumo = false, acao }: SecaoAvaliacoesProps) {
  const hoje = hojeIso()
  const dias = agruparPorDia(avaliacoes)
  const pontos = avaliacoes.reduce((soma, a) => soma + a.valor, 0)

  return (
    <section className={styles.secao} aria-label={titulo}>
      <header className={styles.secaoTopo}>
        <h2 className={styles.secaoTitulo}>
          {titulo} <span className={styles.contador}>{avaliacoes.length}</span>
        </h2>
        {resumo && avaliacoes.length > 0 && (
          <p className={styles.resumo}>
            {contarPorTipo(avaliacoes)} · <strong>{formatarPontos(pontos)} pts</strong> em jogo
          </p>
        )}
      </header>

      {avaliacoes.length === 0 ? (
        <div className={styles.vazio}>
          <Icon name="check" size={20} />
          <span>{vazio}</span>
        </div>
      ) : (
        <ol className={styles.dias}>
          {dias.map((dia) => {
            const faltam = diasAte(hoje, dia.data)
            const urgencia = faltam === 0 ? styles.hoje : faltam === 1 ? styles.amanha : faltam < 0 ? styles.passado : ''
            return (
              <li key={dia.data} className={`${styles.dia} ${faltam < 0 ? styles.diaPassado : ''}`}>
                <div className={styles.diaTopo}>
                  <span className={styles.diaNome}>{diaPorExtenso(dia.data)}</span>
                  <span className={`${styles.quando} ${urgencia}`}>{quando(faltam)}</span>
                </div>
                <ul className={styles.itens}>
                  {dia.avaliacoes.map((avaliacao) => (
                    <ItemAvaliacao key={avaliacao.id} avaliacao={avaliacao} passou={faltam < 0} />
                  ))}
                </ul>
              </li>
            )
          })}
        </ol>
      )}

      {acao && <div className={styles.rodape}>{acao}</div>}
    </section>
  )
}

/** `passou`: o dia já foi; sem nota, a avaliação está esperando o professor lançar. */
function ItemAvaliacao({ avaliacao, passou }: { avaliacao: AvaliacaoDoAluno; passou: boolean }) {
  const tipo = avaliacao.tipo.toLowerCase() as 'prova' | 'trabalho' | 'tarefa'
  const naMedia = avaliacao.nota !== null && atingiuMedia(avaliacao.nota, avaliacao.valor)

  return (
    <li className={styles.item}>
      <span className={`${styles.icone} ${styles[tipo]}`} aria-hidden="true">
        <Icon name={ICONE_DO_TIPO[avaliacao.tipo]} size={18} />
      </span>
      <div className={styles.corpo}>
        {/* A disciplina em destaque: os títulos se repetem ("Prova 2") de uma matéria para outra. */}
        <span className={styles.linhaTitulo}>
          <strong className={styles.titulo}>{avaliacao.disciplinaNome}</strong>
          <span className={`${styles.tipo} ${styles[tipo]}`}>{ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]}</span>
        </span>
        <span className={styles.disciplina}>
          {avaliacao.titulo}
          {avaliacao.bimestre !== null && <span className={styles.bimestre}> · {avaliacao.bimestre}º bimestre</span>}
        </span>
        {avaliacao.descricao && <p className={styles.descricao}>{avaliacao.descricao}</p>}
      </div>
      <div className={styles.pontos}>
        {avaliacao.nota === null ? (
          <>
            <strong>{formatarPontos(avaliacao.valor)}</strong>
            <span>{plural(avaliacao.valor, 'ponto', 'pontos')}</span>
            {passou && <span className={styles.aguardando}>aguardando nota</span>}
          </>
        ) : (
          <span className={`${styles.nota} ${naMedia ? styles.naMedia : styles.abaixo}`}>
            <strong>{formatarPontos(avaliacao.nota)}</strong>
            <span>de {formatarPontos(avaliacao.valor)}</span>
          </span>
        )}
      </div>
    </li>
  )
}
