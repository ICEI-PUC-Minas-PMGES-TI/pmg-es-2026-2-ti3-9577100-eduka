import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type MouseEvent } from 'react'
import { Link } from 'react-router'
import iconeEduka from '../../assets/eduka-icone.svg'
import fotoBiblioteca from '../../assets/homepage/escola-biblioteca.jpg'
import fotoEnsinoMedio from '../../assets/homepage/escola-ensino-medio.jpg'
import fotoProfessora from '../../assets/homepage/escola-professora.jpg'
import fotoSalaDeAula from '../../assets/homepage/escola-sala-de-aula.jpg'
import fotoSalaMural from '../../assets/homepage/escola-sala-mural.jpg'
import celularBoletim from '../../assets/homepage/telas/celular-boletim.jpg'
import celularPagamentos from '../../assets/homepage/telas/celular-pagamentos.jpg'
import telaDirecao from '../../assets/homepage/telas/direcao.jpg'
import telaFinanceiro from '../../assets/homepage/telas/financeiro.jpg'
import telaProfessor from '../../assets/homepage/telas/professor.jpg'
import telaSecretaria from '../../assets/homepage/telas/secretaria.jpg'
import { BotaoAjudaFlutuante } from '../../components/ajuda/BotaoAjudaFlutuante/BotaoAjudaFlutuante'
import { Icon, type IconName } from '../../components/ui/Icon/Icon'
import { EMAIL_SUPORTE } from '../../config/suporte'
import { paths } from '../../routes/paths'
import styles from './Home.module.css'

const EMAIL_CONTATO = EMAIL_SUPORTE

// Na ordem em que as seções aparecem na página; o rótulo é o da própria seção.
const LINKS_NAV = [
  { id: 'inicio', rotulo: 'Início' },
  { id: 'perfis', rotulo: 'Perfis' },
  { id: 'recursos', rotulo: 'Recursos' },
  { id: 'como-funciona', rotulo: 'Como funciona' },
  { id: 'perguntas', rotulo: 'Dúvidas' },
  { id: 'contato', rotulo: 'Contato' },
]

// O que o Eduka faz de verdade, no lugar de números de vitrine.
const DESTAQUES: { icone: IconName; titulo: string; texto: string }[] = [
  { icone: 'card', titulo: 'PIX e boleto', texto: 'Pagamento confirmado sozinho' },
  { icone: 'users', titulo: '6 perfis de acesso', texto: 'Cada pessoa vê o que é dela' },
  { icone: 'calendar', titulo: 'Chamada e notas', texto: 'Direto no celular do professor' },
  { icone: 'shield', titulo: 'LGPD', texto: 'Dados separados e rastreados' },
]

// Escolas públicas brasileiras (créditos em assets/homepage/CREDITS.md e no rodapé).
const FOTOS = [
  { src: fotoSalaDeAula, legenda: 'Sala de aula', texto: 'Chamada e notas lançadas na hora, sem papel.' },
  { src: fotoEnsinoMedio, legenda: 'Alunos', texto: 'Boletim, frequência e mensalidades no portal.' },
  { src: fotoBiblioteca, legenda: 'Biblioteca', texto: 'Os espaços da escola, além da sala de aula.' },
  { src: fotoProfessora, legenda: 'Professores', texto: 'Cada um só com as turmas que leciona.' },
  { src: fotoSalaMural, legenda: 'Turmas', texto: 'Vagas, horários e professores de cada turma.' },
]

type Perfil = {
  id: string
  nome: string
  icone: IconName
  resumo: string
  titulo: string
  itens: string[]
  tela: string
  celular?: boolean
}

const PERFIS: Perfil[] = [
  {
    id: 'direcao',
    nome: 'Direção',
    icone: 'barChart',
    resumo: 'A escola inteira num painel',
    titulo: 'Decida com os números na mão',
    itens: [
      'Recebido no mês, o que falta receber e a inadimplência',
      'Ocupação das turmas e desempenho dos alunos',
      'Equipe, plano e registro de tudo o que foi feito',
    ],
    tela: telaDirecao,
  },
  {
    id: 'secretaria',
    nome: 'Secretaria',
    icone: 'graduationCap',
    resumo: 'Alunos, turmas e matrículas',
    titulo: 'Matrícula sem planilha',
    itens: [
      'Cadastro do aluno com responsáveis e responsável financeiro',
      'Turmas, disciplinas, horários e alocação de professores',
      'Matrícula de vários alunos de uma vez, com controle de vagas',
    ],
    tela: telaSecretaria,
  },
  {
    id: 'financeiro',
    nome: 'Financeiro',
    icone: 'dollarSign',
    resumo: 'Cobranças, despesas e relatórios',
    titulo: 'O caixa da escola em dia',
    itens: [
      'Cobranças por PIX e boleto, confirmadas automaticamente',
      'Despesas por categoria e relatórios com exportação em CSV',
      'Aviso dos alunos com risco de atrasar, com o motivo',
    ],
    tela: telaFinanceiro,
  },
  {
    id: 'professor',
    nome: 'Professor',
    icone: 'book',
    resumo: 'Turmas, chamada e notas',
    titulo: 'Menos papel, mais aula',
    itens: [
      'Aulas da semana com a chamada a um toque',
      'Avaliações com rascunho antes de publicar as notas',
      'Lista de alunos e horário de cada turma',
    ],
    tela: telaProfessor,
  },
  {
    id: 'aluno',
    nome: 'Aluno e família',
    icone: 'user',
    resumo: 'Boletim e mensalidades',
    titulo: 'Tudo da vida escolar no bolso',
    itens: [
      'Boletim com notas, frequência e alerta de faltas',
      'Agenda de provas e tarefas',
      'Mensalidade paga por PIX ou boleto, com comprovante',
    ],
    tela: celularBoletim,
    celular: true,
  },
]

const RECURSOS: { icone: IconName; titulo: string; texto: string; destaque?: boolean }[] = [
  {
    icone: 'card',
    titulo: 'Cobrança que se resolve sozinha',
    texto:
      'A família paga por PIX ou boleto no portal do aluno. O pagamento é confirmado automaticamente, o comprovante fica pronto e o e-mail de confirmação sai na hora.',
    destaque: true,
  },
  {
    icone: 'calendar',
    titulo: 'Chamada e frequência',
    texto: 'O professor registra a aula e as faltas; o boletim avisa quando o aluno passa de 25% de faltas.',
  },
  {
    icone: 'fileText',
    titulo: 'Notas e boletim',
    texto: 'Avaliações por disciplina, notas em rascunho e publicação quando o professor decidir.',
  },
  {
    icone: 'alert',
    titulo: 'Risco de atraso',
    texto: 'O financeiro vê quem tende a atrasar e por quê, antes de a mensalidade vencer.',
  },
  {
    icone: 'barChart',
    titulo: 'Relatórios financeiros',
    texto: 'Receitas, despesas, saldo e inadimplência por período, curso e tipo, com exportação em CSV.',
  },
  {
    icone: 'mail',
    titulo: 'Lembretes por e-mail',
    texto: 'Cobrança disponível, vencimento chegando e pagamento confirmado, sem ninguém precisar lembrar.',
  },
  {
    icone: 'list',
    titulo: 'Registro de atividades',
    texto: 'Quem fez o quê e quando: cadastros, matrículas, notas e cobranças ficam registrados.',
    destaque: true,
  },
]

const PASSOS = [
  {
    titulo: 'A gente cadastra sua escola',
    texto: 'Você manda os dados da instituição e a lista de alunos. O cadastro fica pronto em um dia.',
  },
  {
    titulo: 'Cada pessoa recebe o acesso',
    texto: 'Secretaria, financeiro, professores e alunos recebem o primeiro acesso por e-mail.',
  },
  {
    titulo: 'A escola começa a usar',
    texto: 'Treinamos a equipe e acompanhamos o primeiro mês de perto, sem custo.',
  },
]

const GARANTIAS: { icone: IconName; titulo: string; texto: string }[] = [
  { icone: 'shield', titulo: 'Dados separados', texto: 'Uma escola nunca enxerga as informações de outra.' },
  { icone: 'lock', titulo: 'Acesso por perfil', texto: 'Cada pessoa vê só o que faz parte do trabalho dela.' },
  { icone: 'list', titulo: 'Tudo registrado', texto: 'Cada operação guarda quem fez, quando e o que mudou.' },
  { icone: 'fileText', titulo: 'LGPD', texto: 'Exportação dos dados a pedido e anonimização de quem saiu.' },
]

const PERGUNTAS = [
  {
    pergunta: 'Preciso instalar alguma coisa?',
    resposta:
      'Não. O Eduka funciona no navegador, no computador e no celular. Cada pessoa entra com o próprio e-mail e senha.',
  },
  {
    pergunta: 'Como as famílias pagam as mensalidades?',
    resposta:
      'Pelo portal do aluno, por PIX ou boleto. Quando o pagamento cai, a cobrança muda para paga sozinha, o comprovante fica disponível e a família recebe a confirmação por e-mail.',
  },
  {
    pergunta: 'Os professores conseguem fazer a chamada pelo celular?',
    resposta:
      'Sim. O professor vê as aulas da semana, faz a chamada, registra o conteúdo e lança as notas das avaliações, tudo pelo celular.',
  },
  {
    pergunta: 'Os dados da minha escola ficam seguros?',
    resposta:
      'Os dados de cada escola ficam separados, cada perfil acessa só o que é dele e toda alteração fica registrada. A escola pode exportar os dados de um aluno a pedido e anonimizar quem saiu, como pede a LGPD.',
  },
  {
    pergunta: 'Já tenho os alunos numa planilha. Dá para aproveitar?',
    resposta: 'Dá. Você manda a lista e a gente cadastra a escola, as turmas e os alunos para você começar.',
  },
]

/**
 * Liga o link do menu à seção que está sendo lida: a última cujo topo já
 * passou de um terço da tela. No fim da página vale a última seção (Contato),
 * que é curta demais para chegar lá.
 */
function useSecaoAtiva(ids: string[]): string {
  const [ativa, setAtiva] = useState(ids[0])
  useEffect(() => {
    let quadro = 0
    function medir() {
      quadro = 0
      const linha = window.innerHeight / 3
      const noFim = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4
      let atual = ids[0]
      for (const id of ids) {
        const secao = document.getElementById(id)
        if (secao && secao.getBoundingClientRect().top <= linha) atual = id
      }
      setAtiva(noFim ? ids[ids.length - 1] : atual)
    }
    function agendar() {
      if (!quadro) quadro = requestAnimationFrame(medir)
    }
    medir()
    window.addEventListener('scroll', agendar, { passive: true })
    window.addEventListener('resize', agendar)
    return () => {
      window.removeEventListener('scroll', agendar)
      window.removeEventListener('resize', agendar)
      cancelAnimationFrame(quadro)
    }
  }, [ids])
  return ativa
}

/**
 * Links para seções da própria página (#perfis, #contato...): rola suave até o
 * título, já mostrando o conteúdo de lá (sem esperar a animação de entrada), e
 * deixa o endereço com a âncora. "#inicio" volta ao topo.
 */
function irParaSecao(evento: MouseEvent<HTMLElement>) {
  const link = (evento.target as HTMLElement).closest<HTMLAnchorElement>('a[href^="#"]')
  const id = link?.getAttribute('href')?.slice(1)
  const alvo = id ? document.getElementById(id) : null
  if (!link || !id || !alvo) return
  evento.preventDefault()
  alvo.querySelectorAll('[data-revelar]').forEach((b) => b.setAttribute('data-visivel', ''))
  if (alvo.hasAttribute('data-revelar')) alvo.setAttribute('data-visivel', '')
  const suave = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (id === 'inicio') window.scrollTo({ top: 0, behavior: suave ? 'smooth' : 'auto' })
  else alvo.scrollIntoView({ behavior: suave ? 'smooth' : 'auto', block: 'start' })
  history.replaceState(null, '', `#${id}`)
}

/** Marca os blocos com [data-revelar] quando entram na tela, para surgirem com uma animação curta. */
function useRevelar() {
  useEffect(() => {
    const blocos = [...document.querySelectorAll<HTMLElement>('[data-revelar]')]
    if (typeof IntersectionObserver === 'undefined') {
      blocos.forEach((b) => b.setAttribute('data-visivel', ''))
      return
    }
    const observador = new IntersectionObserver(
      (entradas) => {
        for (const entrada of entradas) {
          if (!entrada.isIntersecting) continue
          entrada.target.setAttribute('data-visivel', '')
          observador.unobserve(entrada.target)
        }
      },
      { rootMargin: '0px 0px -8% 0px' },
    )
    blocos.forEach((b) => observador.observe(b))
    return () => observador.disconnect()
  }, [])
}

const IDS_SECOES = LINKS_NAV.map((l) => l.id)

export function Home() {
  const secaoAtiva = useSecaoAtiva(IDS_SECOES)
  const [menuAberto, setMenuAberto] = useState(false)
  const [perfilId, setPerfilId] = useState(PERFIS[0].id)
  const abasRef = useRef<HTMLDivElement>(null)
  const perfil = PERFIS.find((p) => p.id === perfilId) ?? PERFIS[0]
  useRevelar()

  // Setas do teclado trocam de aba, como pede o padrão de abas da WAI-ARIA.
  function navegarAbas(evento: KeyboardEvent<HTMLDivElement>) {
    if (evento.key !== 'ArrowRight' && evento.key !== 'ArrowLeft') return
    const indice = PERFIS.findIndex((p) => p.id === perfilId)
    const proximo = (indice + (evento.key === 'ArrowRight' ? 1 : PERFIS.length - 1)) % PERFIS.length
    setPerfilId(PERFIS[proximo].id)
    abasRef.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[proximo]?.focus()
  }

  // Ainda não há backend para leads: abre o e-mail já preenchido.
  function pedirDemonstracao(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const dados = new FormData(evento.currentTarget)
    const corpo = `Nome: ${dados.get('nome')}\nEscola: ${dados.get('escola')}\nContato: ${dados.get('contato')}`
    window.location.href = `mailto:${EMAIL_CONTATO}?subject=${encodeURIComponent(
      'Quero uma demonstração do Eduka',
    )}&body=${encodeURIComponent(corpo)}`
  }

  return (
    <div className={styles.page} onClick={irParaSecao}>
      <div className={styles.fundo} aria-hidden="true">
        <span className={styles.bolha1} />
        <span className={styles.bolha2} />
        <span className={styles.bolha3} />
      </div>

      <header className={`${styles.nav} ${menuAberto ? styles.navAberta : ''}`}>
        <a href="#inicio" className={styles.marca} onClick={() => setMenuAberto(false)}>
          <img src={iconeEduka} alt="" width={32} height={32} />
          <span>eduka</span>
        </a>
        <nav id="menu-principal" className={styles.navLinks} aria-label="Seções da página">
          {LINKS_NAV.map((link) => (
            <a
              key={link.id}
              href={`#${link.id}`}
              className={`${styles.navLink} ${secaoAtiva === link.id ? styles.navLinkAtivo : ''}`}
              aria-current={secaoAtiva === link.id ? 'location' : undefined}
              onClick={() => setMenuAberto(false)}
            >
              {link.rotulo}
            </a>
          ))}
        </nav>
        <div className={styles.navAcoes}>
          <Link to={paths.login} className={`${styles.botao} ${styles.botaoPrincipal} ${styles.navEntrar}`}>
            Entrar
            <Icon name="arrowRight" size={16} />
          </Link>
          <button
            type="button"
            className={styles.menuBotao}
            aria-expanded={menuAberto}
            aria-controls="menu-principal"
            aria-label={menuAberto ? 'Fechar o menu' : 'Abrir o menu'}
            onClick={() => setMenuAberto((aberto) => !aberto)}
          >
            <Icon name={menuAberto ? 'close' : 'list'} size={20} />
          </button>
        </div>
      </header>

      <main>
        {/* ======================================================== topo */}
        <section id="inicio" className={styles.hero}>
          <div className={styles.heroTexto}>
            <span className={styles.selo}>
              <span className={styles.seloPonto} aria-hidden="true" />
              Gestão escolar com cobrança por PIX e boleto
            </span>
            <h1 className={styles.heroTitulo}>
              A escola inteira, <span className={styles.realce}>num só lugar.</span>
            </h1>
            <p className={styles.heroDescricao}>
              Matrículas, turmas, chamada, notas, mensalidades e despesas na mesma plataforma. Cada pessoa da
              escola entra com o próprio acesso e vê só o que é do trabalho dela.
            </p>
            <div className={styles.heroBotoes}>
              <a href="#contato" className={`${styles.botao} ${styles.botaoPrincipal} ${styles.botaoGrande}`}>
                Agendar uma demonstração
                <Icon name="arrowRight" size={18} />
              </a>
              <Link to={paths.login} className={`${styles.botao} ${styles.botaoVidro} ${styles.botaoGrande}`}>
                Entrar na plataforma
              </Link>
            </div>
            <ul className={styles.heroGarantias}>
              {['Sem instalar nada', 'Funciona no celular', 'Implantação acompanhada'].map((item) => (
                <li key={item}>
                  <Icon name="check" size={16} />
                  {item}
                </li>
              ))}
            </ul>
          </div>

          <div className={styles.vitrine}>
            <figure className={styles.navegador}>
              <div className={styles.navegadorBarra} aria-hidden="true">
                <span className={styles.navegadorPontos}>
                  <i />
                  <i />
                  <i />
                </span>
                <span className={styles.navegadorEndereco}>
                  <Icon name="lock" size={12} />
                  app.eduka.com.br/inicio
                </span>
              </div>
              <img
                src={telaDirecao}
                alt="Painel da direção no Eduka: alunos matriculados, recebido no mês, valor a receber e o gráfico do dinheiro que entrou e saiu."
                width={1280}
                height={800}
                fetchPriority="high"
              />
            </figure>

            <figure className={styles.celular}>
              <img
                src={celularPagamentos}
                alt="Portal do aluno no celular: a próxima mensalidade com o botão Pagar."
                width={390}
                height={844}
                fetchPriority="high"
              />
            </figure>

            <div className={`${styles.flutuante} ${styles.flutuantePix}`} aria-hidden="true">
              <span className={`${styles.flutuanteIcone} ${styles.iconeSucesso}`}>
                <Icon name="check" size={16} />
              </span>
              <span>
                <strong>Pagamento confirmado</strong>
                <small>PIX · R$ 1.450,00 · agora</small>
              </span>
            </div>
            <div className={`${styles.flutuante} ${styles.flutuanteChamada}`} aria-hidden="true">
              <span className={styles.flutuanteIcone}>
                <Icon name="calendar" size={16} />
              </span>
              <span>
                <strong>Chamada feita</strong>
                <small>1º ano A · 33 presentes</small>
              </span>
            </div>
          </div>
        </section>

        {/* ======================================================== destaques */}
        <section className={styles.destaques} aria-label="Destaques do Eduka" data-revelar>
          {DESTAQUES.map((d) => (
            <div key={d.titulo} className={styles.destaque}>
              <span className={styles.destaqueIcone}>
                <Icon name={d.icone} size={20} />
              </span>
              <span>
                <strong>{d.titulo}</strong>
                <small>{d.texto}</small>
              </span>
            </div>
          ))}
        </section>

        {/* ======================================================== fotos */}
        <section className={styles.secao} aria-labelledby="titulo-fotos">
          <div className={styles.cabecalho} data-revelar>
            <span className={styles.sobretitulo}>Para a rotina real da escola</span>
            <h2 id="titulo-fotos" className={styles.secaoTitulo}>
              Feito para quem faz a escola acontecer
            </h2>
            <p className={styles.secaoTexto}>
              Da sala de aula à secretaria, cada parte da escola tem o seu lugar no Eduka.
            </p>
          </div>
          <ul className={styles.galeria} data-revelar>
            {FOTOS.map((foto) => (
              <li key={foto.legenda} className={styles.foto}>
                <img src={foto.src} alt="" loading="lazy" decoding="async" />
                <span className={styles.fotoLegenda}>
                  <strong>{foto.legenda}</strong>
                  <small>{foto.texto}</small>
                </span>
              </li>
            ))}
          </ul>
        </section>

        {/* ======================================================== perfis */}
        <section id="perfis" className={styles.secao} aria-labelledby="titulo-perfis">
          <div className={styles.cabecalho} data-revelar>
            <span className={styles.sobretitulo}>Para quem é</span>
            <h2 id="titulo-perfis" className={styles.secaoTitulo}>
              Um acesso para cada pessoa da escola
            </h2>
            <p className={styles.secaoTexto}>
              Ninguém precisa aprender o sistema inteiro: cada perfil abre direto no que interessa para ele.
            </p>
          </div>

          <div className={styles.perfis} data-revelar>
            <div
              ref={abasRef}
              className={styles.perfisAbas}
              role="tablist"
              aria-label="Perfis de acesso"
              onKeyDown={navegarAbas}
            >
              {PERFIS.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  role="tab"
                  id={`aba-${p.id}`}
                  aria-selected={p.id === perfil.id}
                  aria-controls="painel-perfil"
                  tabIndex={p.id === perfil.id ? 0 : -1}
                  className={`${styles.perfilAba} ${p.id === perfil.id ? styles.perfilAbaAtiva : ''}`}
                  onClick={() => setPerfilId(p.id)}
                >
                  <span className={styles.perfilIcone}>
                    <Icon name={p.icone} size={18} />
                  </span>
                  <span className={styles.perfilNome}>
                    <strong>{p.nome}</strong>
                    <small>{p.resumo}</small>
                  </span>
                </button>
              ))}
            </div>

            <div
              id="painel-perfil"
              role="tabpanel"
              aria-labelledby={`aba-${perfil.id}`}
              className={styles.perfilPainel}
              key={perfil.id}
            >
              <div className={styles.perfilTexto}>
                <h3>{perfil.titulo}</h3>
                <ul>
                  {perfil.itens.map((item) => (
                    <li key={item}>
                      <Icon name="check" size={16} />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
              <figure className={perfil.celular ? styles.perfilCelular : styles.perfilTela}>
                <img
                  src={perfil.tela}
                  alt={`Tela do perfil ${perfil.nome} no Eduka.`}
                  width={perfil.celular ? 390 : 1280}
                  height={perfil.celular ? 844 : 800}
                  loading="lazy"
                />
              </figure>
            </div>
          </div>
        </section>

        {/* ======================================================== recursos */}
        <section id="recursos" className={styles.secao} aria-labelledby="titulo-recursos">
          <div className={styles.cabecalho} data-revelar>
            <span className={styles.sobretitulo}>Recursos</span>
            <h2 id="titulo-recursos" className={styles.secaoTitulo}>
              O que a escola ganha no dia a dia
            </h2>
          </div>
          <ul className={styles.recursos}>
            {RECURSOS.map((r) => (
              <li key={r.titulo} className={`${styles.recurso} ${r.destaque ? styles.recursoDestaque : ''}`} data-revelar>
                <span className={styles.recursoIcone}>
                  <Icon name={r.icone} size={22} />
                </span>
                <h3>{r.titulo}</h3>
                <p>{r.texto}</p>
              </li>
            ))}
          </ul>
        </section>

        {/* ======================================================== como funciona */}
        <section id="como-funciona" className={styles.secao} aria-labelledby="titulo-passos">
          <div className={styles.cabecalho} data-revelar>
            <span className={styles.sobretitulo}>Como funciona</span>
            <h2 id="titulo-passos" className={styles.secaoTitulo}>
              Começar leva três passos
            </h2>
          </div>
          <ol className={styles.passos} data-revelar>
            {PASSOS.map((passo, indice) => (
              <li key={passo.titulo} className={styles.passo}>
                <span className={styles.passoNumero} aria-hidden="true">
                  {indice + 1}
                </span>
                <h3>{passo.titulo}</h3>
                <p>{passo.texto}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* ======================================================== segurança */}
        <section className={styles.seguranca} aria-labelledby="titulo-seguranca" data-revelar>
          <div className={styles.segurancaTexto}>
            <span className={styles.segurancaSelo}>
              <Icon name="shield" size={18} />
              Segurança e LGPD
            </span>
            <h2 id="titulo-seguranca">Os dados de cada escola ficam protegidos</h2>
            <p>
              Uma escola nunca enxerga os alunos da outra, cada pessoa acessa só o que é dela e tudo o que é feito
              no sistema fica registrado.
            </p>
          </div>
          <ul className={styles.garantias}>
            {GARANTIAS.map((g) => (
              <li key={g.titulo} className={styles.garantia}>
                <span className={styles.garantiaIcone}>
                  <Icon name={g.icone} size={18} />
                </span>
                <strong>{g.titulo}</strong>
                <span>{g.texto}</span>
              </li>
            ))}
          </ul>
        </section>

        {/* ======================================================== perguntas */}
        <section id="perguntas" className={`${styles.secao} ${styles.perguntasSecao}`} aria-labelledby="titulo-perguntas">
          <div className={styles.cabecalho} data-revelar>
            <span className={styles.sobretitulo}>Dúvidas</span>
            <h2 id="titulo-perguntas" className={styles.secaoTitulo}>
              Perguntas frequentes
            </h2>
          </div>
          <div className={styles.perguntas} data-revelar>
            {PERGUNTAS.map((p, indice) => (
              <details key={p.pergunta} className={styles.pergunta} open={indice === 0}>
                <summary>
                  {p.pergunta}
                  <Icon name="chevronDown" size={20} />
                </summary>
                <p>{p.resposta}</p>
              </details>
            ))}
          </div>
        </section>

        {/* ======================================================== contato */}
        <section id="contato" className={styles.contato} aria-labelledby="titulo-contato" data-revelar>
          <div className={styles.contatoTexto}>
            <h2 id="titulo-contato">Quer ver o Eduka funcionando na sua escola?</h2>
            <p>
              Deixe seu contato: a gente marca 30 minutos, mostra o sistema por dentro com dados de exemplo e responde
              suas dúvidas.
            </p>
            <ul>
              <li>
                <Icon name="check" size={16} /> Demonstração sem compromisso
              </li>
              <li>
                <Icon name="check" size={16} /> Implantação acompanhada no primeiro mês
              </li>
            </ul>
          </div>
          <form className={styles.contatoForm} onSubmit={pedirDemonstracao}>
            <label className={styles.campo}>
              <span>Seu nome</span>
              <input name="nome" placeholder="Nome completo" autoComplete="name" required />
            </label>
            <label className={styles.campo}>
              <span>Escola</span>
              <input name="escola" placeholder="Nome da escola" autoComplete="organization" required />
            </label>
            <label className={styles.campo}>
              <span>E-mail ou telefone</span>
              <input name="contato" placeholder="Como podemos falar com você" required />
            </label>
            <button type="submit" className={`${styles.botao} ${styles.botaoPrincipal} ${styles.botaoGrande}`}>
              Quero uma demonstração
              <Icon name="arrowRight" size={18} />
            </button>
          </form>
        </section>
      </main>

      <footer className={styles.rodape}>
        <div className={styles.rodapeTopo}>
          <div className={styles.rodapeMarca}>
            <a href="#inicio" className={styles.marca}>
              <img src={iconeEduka} alt="" width={30} height={30} />
              <span>eduka</span>
            </a>
            <p>Gestão escolar e financeira para escolas que querem trabalhar com menos papel.</p>
          </div>
          <nav className={styles.rodapeColuna} aria-label="Produto">
            <strong>Produto</strong>
            {LINKS_NAV.map((link) => (
              <a key={link.id} href={`#${link.id}`}>
                {link.rotulo}
              </a>
            ))}
          </nav>
          <div className={styles.rodapeColuna}>
            <strong>Acesso</strong>
            <Link to={paths.login}>Entrar na plataforma</Link>
            <a href="#contato">Agendar demonstração</a>
          </div>
          <div className={styles.rodapeColuna}>
            <strong>Contato</strong>
            <a href={`mailto:${EMAIL_CONTATO}`}>{EMAIL_CONTATO}</a>
            <span>Belo Horizonte · MG</span>
          </div>
        </div>
        <p className={styles.rodapeLegal}>
          © 2026 Eduka · Fotos de escolas públicas brasileiras via Wikimedia Commons: Prefeitura de Olinda (CC BY
          2.0), Fronteira e Ámonbaal (CC0).
        </p>
      </footer>
      <BotaoAjudaFlutuante tela="Página inicial" />
    </div>
  )
}
