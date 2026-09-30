import { useEffect, useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { obterMeuPerfil } from '../../../../services/alunoService'
import { ALUNO_MOCK_PADRAO, type AlunoPerfil } from '../../../../types/aluno'
import { ModalCorrecaoDados } from '../../components/ModalCorrecaoDados/ModalCorrecaoDados'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './MeusDados.module.css'

type AbaPerfil = 'pessoais' | 'responsavel' | 'acesso'

export function MeusDados() {
  const [perfil, setPerfil] = useState<AlunoPerfil>(ALUNO_MOCK_PADRAO)
  const [abaAtiva, setAbaAtiva] = useState<AbaPerfil>('pessoais')
  const [modalCorrecaoAberto, setModalCorrecaoAberto] = useState(false)
  const [senhaRedefinida, setSenhaRedefinida] = useState(false)

  useEffect(() => {
    const abortController = new AbortController()
    obterMeuPerfil(abortController.signal)
      .then((dados) => setPerfil(dados))
      .catch(() => {
        // Fallback já garantido em caso de erro
      })

    return () => abortController.abort()
  }, [])

  function handleSolicitarRedefinicaoSenha() {
    setSenhaRedefinida(true)
    setTimeout(() => setSenhaRedefinida(false), 3500)
  }

  return (
    <AlunoLayout
      titulo="Meus dados"
      subtitulo="Confira suas informações. Para corrigir algo, fale com a secretaria."
      escola={perfil.escolaNome}
    >
      <div className={styles.container}>
        {/* Card lateral esquerdo: Resumo do perfil do aluno */}
        <aside className={styles.perfilCard} aria-label="Resumo do perfil">
          <div className={styles.avatar} aria-hidden="true">
            {perfil.iniciais}
          </div>

          <h2 className={styles.nomeAluno}>{perfil.nomeCurto}</h2>
          <span className={styles.matricula}>Matrícula {perfil.matricula}</span>

          <Badge tone={perfil.matriculaAtiva ? 'success' : 'danger'}>
            {perfil.matriculaAtiva ? 'Matrícula ativa' : 'Matrícula inativa'}
          </Badge>

          <div className={styles.detalhesAcademicos}>
            <div className={styles.linhaInfo}>
              <span className={styles.rotuloInfo}>Turma</span>
              <span className={styles.valorInfo}>{perfil.turma}</span>
            </div>
            <div className={styles.linhaInfo}>
              <span className={styles.rotuloInfo}>Curso</span>
              <span className={styles.valorInfo}>{perfil.curso}</span>
            </div>
            <div className={styles.linhaInfo}>
              <span className={styles.rotuloInfo}>Período</span>
              <span className={styles.valorInfo}>{perfil.periodo}</span>
            </div>
            <div className={styles.linhaInfo}>
              <span className={styles.rotuloInfo}>Turno</span>
              <span className={styles.valorInfo}>{perfil.turno}</span>
            </div>
          </div>

          <button
            type="button"
            className={styles.botaoCorrecao}
            onClick={() => setModalCorrecaoAberto(true)}
          >
            Pedir correção de dados
          </button>
        </aside>

        {/* Painel lateral direito: Abas e Detalhamento */}
        <section className={styles.painelDireito} aria-label="Detalhes cadastrais">
          {/* Seletor de abas */}
          <div className={styles.tabsContainer} role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={abaAtiva === 'pessoais'}
              className={`${styles.tabBotao} ${abaAtiva === 'pessoais' ? styles.tabAtiva : ''}`}
              onClick={() => setAbaAtiva('pessoais')}
            >
              Dados pessoais
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={abaAtiva === 'responsavel'}
              className={`${styles.tabBotao} ${abaAtiva === 'responsavel' ? styles.tabAtiva : ''}`}
              onClick={() => setAbaAtiva('responsavel')}
            >
              Responsável
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={abaAtiva === 'acesso'}
              className={`${styles.tabBotao} ${abaAtiva === 'acesso' ? styles.tabAtiva : ''}`}
              onClick={() => setAbaAtiva('acesso')}
            >
              Acesso
            </button>
          </div>

          {/* Conteúdo da aba selecionada */}
          <div className={styles.conteudoCard}>
            {abaAtiva === 'pessoais' && (
              <div>
                <h3 className={styles.tituloSecao}>Dados pessoais</h3>

                <div className={styles.gridCampos}>
                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Nome completo</span>
                    <span className={styles.campoValor}>{perfil.nome}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Data de nascimento</span>
                    <span className={styles.campoValor}>{perfil.dataNascimento}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>CPF</span>
                    <span className={styles.campoValor}>{perfil.cpf}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>RG</span>
                    <span className={styles.campoValor}>{perfil.rg}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>E-mail</span>
                    <span className={styles.campoValor}>{perfil.email}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Telefone</span>
                    <span className={styles.campoValor}>{perfil.telefone}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Endereço</span>
                    <span className={styles.campoValor}>{perfil.endereco}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Cidade</span>
                    <span className={styles.campoValor}>{perfil.cidade}</span>
                  </div>
                </div>

                <div className={styles.avisoRodape}>
                  <Icon name="info" size={16} />
                  <span>Só a secretaria pode alterar estes dados.</span>
                </div>
              </div>
            )}

            {abaAtiva === 'responsavel' && (
              <div>
                <h3 className={styles.tituloSecao}>Responsável legal</h3>

                <div className={styles.gridCampos}>
                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Nome do responsável</span>
                    <span className={styles.campoValor}>{perfil.responsavel.nome}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Parentesco</span>
                    <span className={styles.campoValor}>{perfil.responsavel.parentesco}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>CPF</span>
                    <span className={styles.campoValor}>{perfil.responsavel.cpf}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>RG</span>
                    <span className={styles.campoValor}>{perfil.responsavel.rg}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>E-mail de contato</span>
                    <span className={styles.campoValor}>{perfil.responsavel.email}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Telefone celular</span>
                    <span className={styles.campoValor}>{perfil.responsavel.telefone}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Endereço residencial</span>
                    <span className={styles.campoValor}>{perfil.responsavel.endereco}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Cidade</span>
                    <span className={styles.campoValor}>{perfil.responsavel.cidade}</span>
                  </div>
                </div>

                <div className={styles.avisoRodape}>
                  <Icon name="info" size={16} />
                  <span>Só a secretaria pode alterar estes dados.</span>
                </div>
              </div>
            )}

            {abaAtiva === 'acesso' && (
              <div>
                <h3 className={styles.tituloSecao}>Credenciais de acesso</h3>

                <div className={styles.gridCampos}>
                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>E-mail institucional / Login</span>
                    <span className={styles.campoValor}>{perfil.acesso.login}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Senha</span>
                    <span className={styles.campoValor}>••••••••••••</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Perfil no sistema</span>
                    <span className={styles.campoValor}>{perfil.acesso.tipoUsuario}</span>
                  </div>

                  <div className={styles.campoItem}>
                    <span className={styles.campoRotulo}>Último acesso</span>
                    <span className={styles.campoValor}>{perfil.acesso.ultimoAcesso}</span>
                  </div>
                </div>

                <div className={styles.acoesAcesso}>
                  <Button
                    variant="outline"
                    onClick={handleSolicitarRedefinicaoSenha}
                  >
                    {senhaRedefinida ? '✓ Link enviado para seu e-mail!' : 'Solicitar redefinição de senha'}
                  </Button>
                </div>

                <div className={styles.avisoRodape}>
                  <Icon name="info" size={16} />
                  <span>A redefinição de senha envia um link seguro para o seu e-mail institucional.</span>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>

      <ModalCorrecaoDados
        aberto={modalCorrecaoAberto}
        escolaNome={perfil.escolaNome}
        escolaEmail={perfil.escolaEmail}
        escolaTelefone={perfil.escolaTelefone}
        onFechar={() => setModalCorrecaoAberto(false)}
      />
    </AlunoLayout>
  )
}
