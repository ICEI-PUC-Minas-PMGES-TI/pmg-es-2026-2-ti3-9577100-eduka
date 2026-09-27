import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import iconeEduka from '../../assets/eduka-icone.svg'
import { Icon } from '../../components/Icon/Icon'
import { paths } from '../../routes/paths'
import styles from './Home.module.css'

const EMAIL_CONTATO = 'contato@eduka.com.br'

const LINKS_NAV = [
  { id: 'inicio', rotulo: 'Início' },
  { id: 'recursos', rotulo: 'Recursos' },
  { id: 'planos', rotulo: 'Planos' },
  { id: 'contato', rotulo: 'Contato' },
]

const NUMEROS = [
  { valor: '+120', rotulo: 'escolas usando' },
  { valor: '45 mil', rotulo: 'alunos cadastrados' },
  { valor: '30 dias', rotulo: 'de teste grátis' },
]

// Espaços para fotos reais das escolas parceiras, como no protótipo.
const ESPACOS_FOTO = ['Sala de aula', 'Secretaria', 'Pátio', 'Professores', 'Biblioteca', 'Formatura']

const PERFIS = [
  {
    nome: 'Direção',
    resumo: 'Resumo da escola',
    descricao: 'Vê quanto entrou, quanto falta receber, quem está atrasado e o que a equipe fez no dia.',
  },
  {
    nome: 'Secretaria',
    resumo: 'Alunos e matrículas',
    descricao: 'Cadastra alunos, monta as turmas e faz a matrícula em três passos, sem precisar de planilha.',
  },
  {
    nome: 'Financeiro',
    resumo: 'Cobranças e despesas',
    descricao: 'Cria cobranças, registra pagamentos e lança as despesas, com tudo somado no fim do mês.',
  },
  {
    nome: 'Professor',
    resumo: 'Suas turmas',
    descricao: 'Vê as próprias turmas e a lista de alunos de cada uma, sem acesso ao financeiro.',
  },
  {
    nome: 'Aluno',
    resumo: 'Mensalidades e boletos',
    descricao: 'Consulta as mensalidades, baixa o boleto e acompanha os pagamentos já feitos.',
  },
]

const PASSOS = [
  {
    titulo: 'A gente cadastra sua escola',
    texto: 'Você manda os dados da instituição e a lista de alunos. O cadastro fica pronto em um dia.',
  },
  {
    titulo: 'Cada pessoa recebe o acesso',
    texto: 'Secretaria, financeiro, professores e alunos entram com e-mail e senha próprios.',
  },
  {
    titulo: 'A escola começa a usar',
    texto: 'Treinamos a equipe e acompanhamos o primeiro mês de perto, sem custo.',
  },
]

const GARANTIAS = [
  { titulo: 'Dados separados', texto: 'Cada escola só enxerga as próprias informações.' },
  { titulo: 'Acesso por perfil', texto: 'Cada pessoa vê apenas o que é do trabalho dela.' },
  { titulo: 'Registro de ações', texto: 'Dá para saber quem fez o quê e quando.' },
  { titulo: 'LGPD', texto: 'Tratamento dos dados conforme a lei.' },
]

export function Home() {
  const [secaoAtiva, setSecaoAtiva] = useState('inicio')
  const [perfilIndice, setPerfilIndice] = useState(1)
  const perfil = PERFIS[perfilIndice]

  // Ainda não há backend para leads: abre o e-mail já preenchido.
  function pedirDemonstracao(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const dados = new FormData(evento.currentTarget)
    const corpo = `Nome: ${dados.get('nome')}\nContato: ${dados.get('contato')}`
    window.location.href = `mailto:${EMAIL_CONTATO}?subject=${encodeURIComponent(
      'Quero uma demonstração do Eduka',
    )}&body=${encodeURIComponent(corpo)}`
  }

  return (
    <div className={styles.page}>
      <header className={styles.nav}>
        <a href="#inicio" className={styles.marca} onClick={() => setSecaoAtiva('inicio')}>
          <img src={iconeEduka} alt="" width={30} height={30} />
          <span>eduka</span>
        </a>
        <nav className={styles.navLinks} aria-label="Seções da página">
          {LINKS_NAV.map((link) => (
            <a
              key={link.id}
              href={`#${link.id}`}
              className={`${styles.navLink} ${secaoAtiva === link.id ? styles.navLinkAtivo : ''}`}
              aria-current={secaoAtiva === link.id ? 'location' : undefined}
              onClick={() => setSecaoAtiva(link.id)}
            >
              {link.rotulo}
            </a>
          ))}
        </nav>
        <Link to={paths.login} className={`${styles.botao} ${styles.botaoPrincipal} ${styles.navEntrar}`}>
          Entrar na plataforma
        </Link>
      </header>

      <main className={styles.main}>
        <section id="inicio" className={styles.hero}>
          <span className={styles.tag}>Gestão escolar multi-escola</span>
          <h1 className={styles.heroTitle}>
            Sua escola, organizada
            <br />
            em um só lugar.
          </h1>
          <p className={styles.heroText}>
            Matrículas, turmas, mensalidades e despesas na mesma plataforma. Cada pessoa entra com o
            seu acesso e vê só o que é do trabalho dela.
          </p>
          <div className={styles.heroBotoes}>
            <Link to={paths.login} className={`${styles.botao} ${styles.botaoPrincipal}`}>
              Entrar na plataforma
            </Link>
            <a href="#contato" className={`${styles.botao} ${styles.botaoVidro}`}>
              Agendar uma demonstração
            </a>
          </div>
          <dl className={styles.numeros}>
            {NUMEROS.map((numero) => (
              <div key={numero.rotulo} className={styles.numero}>
                <dt>{numero.valor}</dt>
                <dd>{numero.rotulo}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className={styles.fotos} aria-label="Fotos das escolas">
          <ul className={styles.fotosGrade}>
            {ESPACOS_FOTO.map((espaco) => (
              <li key={espaco} className={styles.foto}>
                <Icon name="camera" size={20} />
                <span>{espaco}</span>
              </li>
            ))}
          </ul>
          <p className={styles.legenda}>Espaços reservados para fotos reais das escolas parceiras.</p>
        </section>

        <section id="recursos" className={styles.secao}>
          <div className={styles.secaoCabecalho}>
            <h2 className={styles.secaoTitulo}>Um acesso para cada pessoa da escola</h2>
            <p className={styles.secaoTexto}>
              Ninguém precisa aprender o sistema inteiro: cada perfil abre direto no que interessa
              para ele.
            </p>
          </div>

          <div className={styles.perfis} role="group" aria-label="Perfis de acesso">
            {PERFIS.map((item, indice) => (
              <button
                key={item.nome}
                type="button"
                className={`${styles.perfil} ${indice === perfilIndice ? styles.perfilAtivo : ''}`}
                aria-pressed={indice === perfilIndice}
                onClick={() => setPerfilIndice(indice)}
              >
                <strong>{item.nome}</strong>
                <span>{item.resumo}</span>
              </button>
            ))}
          </div>

          <div className={styles.perfilDetalhe} aria-live="polite">
            <div>
              <span className={styles.perfilRotulo}>Perfil {perfil.nome}</span>
              <p>{perfil.descricao}</p>
            </div>
            <Link to={paths.login} className={`${styles.botao} ${styles.botaoPrincipal} ${styles.botaoPequeno}`}>
              Ver essa tela
            </Link>
          </div>
        </section>

        <section id="planos" className={styles.secao}>
          <h2 className={styles.secaoTitulo}>Começar leva três passos</h2>
          <ol className={styles.passos}>
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

        <section className={styles.seguranca}>
          <div className={styles.segurancaTexto}>
            <span className={styles.segurancaIcone} aria-hidden="true">
              <Icon name="shield" size={20} />
            </span>
            <h2>Os dados de cada escola ficam separados</h2>
            <p>
              Uma escola nunca enxerga os alunos da outra. Tudo o que é feito no sistema fica
              registrado, com dia, hora e quem fez.
            </p>
          </div>
          <ul className={styles.garantias}>
            {GARANTIAS.map((garantia) => (
              <li key={garantia.titulo} className={styles.garantia}>
                <strong>{garantia.titulo}</strong>
                <span>{garantia.texto}</span>
              </li>
            ))}
          </ul>
        </section>

        <section id="contato" className={styles.contato}>
          <div className={styles.contatoTexto}>
            <h2>Quer ver o Eduka na sua escola?</h2>
            <p>
              Deixe seu contato: a gente marca 30 minutos, mostra o sistema por dentro e responde suas
              dúvidas.
            </p>
          </div>
          <form className={styles.contatoForm} onSubmit={pedirDemonstracao}>
            <label className={styles.contatoCampo}>
              <span>Seu nome</span>
              <input name="nome" placeholder="Nome completo" autoComplete="name" required />
            </label>
            <label className={styles.contatoCampo}>
              <span>E-mail ou telefone</span>
              <input name="contato" placeholder="Como podemos falar com você" required />
            </label>
            <button type="submit" className={`${styles.botao} ${styles.botaoPrincipal}`}>
              Quero uma demonstração
            </button>
          </form>
        </section>
      </main>

      <footer className={styles.rodape}>
        <div className={styles.rodapeMarca}>
          <img src={iconeEduka} alt="" width={26} height={26} />
          <strong>eduka</strong>
          <span>
            <a href={`mailto:${EMAIL_CONTATO}`}>{EMAIL_CONTATO}</a> · (31) 0000-0000
          </span>
        </div>
        <p className={styles.rodapeLegal}>
          © 2026 Eduka · <span>Política de Privacidade</span> · <span>Termos de Uso</span>
        </p>
      </footer>
    </div>
  )
}
