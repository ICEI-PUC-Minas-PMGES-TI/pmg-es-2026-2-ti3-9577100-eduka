import { Link } from 'react-router'
import classroom1 from '../../assets/homepage/classroom-1.jpg'
import classroom2 from '../../assets/homepage/classroom-2.jpg'
import classroom3 from '../../assets/homepage/classroom-3.jpg'
import classroom4 from '../../assets/homepage/classroom-4.jpg'
import classroom5 from '../../assets/homepage/classroom-5.jpg'
import iconeEduka from '../../assets/eduka-icone.svg'
import logoEduka from '../../assets/eduka-logo.svg'
import { paths } from '../../routes/paths'
import styles from './Home.module.css'

// Fotos coloridas de domínio público (Wikimedia Commons) - créditos em
// src/assets/homepage/CREDITS.md.
const FOTOS = [
  { src: classroom1, alt: 'Sala de aula com carteiras organizadas para os alunos' },
  { src: classroom2, alt: 'Crianças reunidas em sala de aula' },
  { src: classroom3, alt: 'Turma do ensino médio em sala de aula com o professor' },
  { src: classroom4, alt: 'Alunos em atividade na escola' },
  { src: classroom5, alt: 'Alunos do ensino secundário em sala de aula' },
]

// Duplicada para o carrossel rolar em loop sem "salto" (anima só a primeira
// metade da faixa; a segunda é a mesma sequência, então -50% fecha o ciclo).
const FOTOS_CARROSSEL = [...FOTOS, ...FOTOS]

export function Home() {
  return (
    <main className={styles.page}>
      <header className={styles.topbar}>
        <div className={styles.brand}>
          <img src={iconeEduka} alt="" className={styles.brandLogo} width={32} height={32} />
          <span className={styles.brandName}>eduka</span>
        </div>
        <Link to={paths.login} className={styles.topbarBtn}>
          Entrar
        </Link>
      </header>

      <section className={styles.hero}>
        <img src={logoEduka} alt="eduka" className={styles.heroLogo} />
        <span className={styles.tag}>Gestão escolar multi-escola</span>
        <h1 className={styles.title}>
          Sua escola,
          <br />
          organizada em um só lugar.
        </h1>
        <p className={styles.text}>
          Acadêmico, administrativo e financeiro reunidos numa plataforma só, com acesso
          separado para cada perfil: direção, secretaria, financeiro, professores e alunos.
        </p>
        <Link to={paths.login} className={styles.ctaBtn}>
          Entrar na plataforma
        </Link>
      </section>

      <section className={styles.carouselPanel} aria-hidden="true">
        <div className={styles.carouselTrack}>
          {FOTOS_CARROSSEL.map((foto, indice) => (
            <img
              key={`${foto.src}-${indice}`}
              src={foto.src}
              alt={foto.alt}
              className={styles.carouselImg}
            />
          ))}
        </div>
      </section>
    </main>
  )
}
