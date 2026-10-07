import { Button } from '../../../../components/Button/Button'
import { listarMinhasAvaliacoes } from '../../../../services/portalAlunoService'
import { ROTULOS_TIPO_AVALIACAO, type AvaliacaoDoAluno } from '../../../../types/portalAluno'
import { hojeIso } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { formatarPontos } from '../../formatacao'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Avaliacoes.module.css'

const NENHUMA: AvaliacaoDoAluno[] = []

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

export function Avaliacoes() {
  const { dados: avaliacoes, carregando, erro, recarregar } = useDadosDoAluno(listarMinhasAvaliacoes, NENHUMA)

  // O backend já manda por data; as realizadas aparecem da mais recente para trás.
  const hoje = hojeIso()
  const proximas = avaliacoes.filter((a) => a.data >= hoje)
  const realizadas = avaliacoes.filter((a) => a.data < hoje).reverse()

  return (
    <AlunoLayout
      titulo="Provas e tarefas"
      subtitulo="O que vem pela frente e o que você já fez, em todas as disciplinas."
    >
      {carregando && <div className={styles.mensagem}>Carregando suas avaliações…</div>}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && avaliacoes.length === 0 && (
        <div className={styles.mensagem}>
          <strong>Nenhuma prova ou tarefa marcada.</strong>
          <span>Quando os professores cadastrarem avaliações, elas aparecem aqui.</span>
        </div>
      )}

      {!carregando && !erro && avaliacoes.length > 0 && (
        <>
          <Secao titulo="Próximas" vazio="Nada marcado daqui para frente." avaliacoes={proximas} />
          <Secao titulo="Já realizadas" vazio="Nenhuma avaliação realizada ainda." avaliacoes={realizadas} />
        </>
      )}
    </AlunoLayout>
  )
}

function Secao({ titulo, vazio, avaliacoes }: { titulo: string; vazio: string; avaliacoes: AvaliacaoDoAluno[] }) {
  return (
    <section className={styles.secao} aria-label={titulo}>
      <h2 className={styles.secaoTitulo}>
        {titulo} <span>{avaliacoes.length}</span>
      </h2>
      {avaliacoes.length === 0 ? (
        <p className={styles.vazio}>{vazio}</p>
      ) : (
        <ul className={styles.lista}>
          {avaliacoes.map((avaliacao) => (
            <ItemAvaliacao key={avaliacao.id} avaliacao={avaliacao} />
          ))}
        </ul>
      )}
    </section>
  )
}

function ItemAvaliacao({ avaliacao }: { avaliacao: AvaliacaoDoAluno }) {
  // "AAAA-MM-DD" lido direto, sem Date, para o fuso não mudar o dia.
  const [, mes, dia] = avaliacao.data.split('-')

  return (
    <li className={styles.item}>
      <div className={styles.data} aria-label={`Dia ${dia}/${mes}`}>
        <strong>{dia}</strong>
        <span>{MESES[Number(mes) - 1]}</span>
      </div>
      <div className={styles.corpo}>
        <span className={styles.tipo}>{ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]}</span>
        <strong className={styles.titulo}>{avaliacao.titulo}</strong>
        <span className={styles.disciplina}>{avaliacao.disciplinaNome}</span>
        {avaliacao.descricao && <p className={styles.descricao}>{avaliacao.descricao}</p>}
      </div>
      <div className={styles.pontos}>
        {avaliacao.nota === null ? (
          <span>Vale {formatarPontos(avaliacao.valor)} pts</span>
        ) : (
          <>
            <strong>{formatarPontos(avaliacao.nota)}</strong>
            <span>de {formatarPontos(avaliacao.valor)} pts</span>
          </>
        )}
      </div>
    </li>
  )
}
