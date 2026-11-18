import { useMemo, useState } from 'react'
import { Button } from '../../../../../components/ui/Button/Button'
import { ConfirmarExclusao } from '../../../../../components/modal/ConfirmarExclusao/ConfirmarExclusao'
import { ModalAlerta, ModalAviso } from '../../../../../components/modal/Modal/Modal'
import { ModalDetalhes } from '../../../../../components/modal/ModalDetalhes/ModalDetalhes'
import { useCarregamento } from '../../../../../hooks/useCarregamento'
import { useDetalhe } from '../../../../../hooks/useDetalhe'
import { anonimizarAluno, buscarAluno, exportarDadosPessoais } from '../../../../../services/pessoas/alunoService'
import { ApiError } from '../../../../../services/api'
import { obterUsuarioLogado } from '../../../../../services/acesso/authService'
import { listarMatriculas } from '../../../../../services/academico/matriculaService'
import type { AlunoResumo } from '../../../../../types/pessoas/aluno'
import type { Matricula } from '../../../../../types/academico/matricula'
import { nomeDoPdfDeDadosPessoais, salvarArquivo } from '../../../../../utils/arquivos'
import { formatarCnpjCpf, formatarEndereco, mascararTelefone } from '../../../../../utils/formatters'
import { FinanceiroAluno } from '../FinanceiroAluno/FinanceiroAluno'
import { formatarData } from '../../../../../utils/formatacao'

/**
 * A aba Financeiro só aparece para quem o backend deixa ler o histórico
 * financeiro (GET /alunos/{id}/financeiro: MASTER e FINANCEIRO). A secretaria
 * não vê: para ela a rota responde 403.
 */
function podeVerFinanceiro(): boolean {
  const perfil = obterUsuarioLogado()?.perfil
  return perfil === 'MASTER' || perfil === 'FINANCEIRO'
}

/** Exportar os dados (LGPD): quem atende o titular na escola. */
function podeExportar(): boolean {
  const perfil = obterUsuarioLogado()?.perfil
  return perfil === 'MASTER' || perfil === 'SECRETARIA'
}

/** Anonimizar não tem volta: só o MASTER. */
function podeAnonimizar(): boolean {
  return obterUsuarioLogado()?.perfil === 'MASTER'
}

function erroAoExportar(e: unknown): string {
  if (e instanceof ApiError && e.status === 409) return 'Este aluno já foi anonimizado: não há dados para exportar.'
  if (e instanceof ApiError && e.status === 403) return e.message
  return 'Não foi possível gerar o PDF. Tente de novo.'
}

/** O backend explica por que não anonimiza (matrícula ou cobrança em aberto); a tela traduz. */
function motivoParaNaoAnonimizar(e: unknown): string {
  const detalhe = e instanceof ApiError ? (e.detalhe ?? '') : ''
  if (/matricula/i.test(detalhe)) {
    return 'O aluno ainda tem matrícula ativa ou trancada. Cancele ou conclua a matrícula antes de anonimizar.'
  }
  if (/cobranca/i.test(detalhe)) {
    return 'O aluno ainda tem cobrança em aberto. Receba ou cancele a cobrança antes de anonimizar.'
  }
  if (/ja anonimizado/i.test(detalhe)) return 'Este aluno já foi anonimizado.'
  if (e instanceof ApiError && e.status === 403) return e.message
  return 'Não foi possível anonimizar o aluno. Tente de novo.'
}

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_MATRICULAS: Matricula[] = []

/**
 * Turma e curso do aluno na identificação (IU18, campo 1): os da matrícula
 * ativa ou, sem ela, da trancada, a mesma regra da turma na lista de alunos.
 */
function matriculaAtual(matriculas: Matricula[]): Matricula | null {
  return matriculas.find((m) => m.status === 'ATIVA') ?? matriculas.find((m) => m.status === 'TRANCADA') ?? null
}

type DetalhesAlunoModalProps = {
  /**
   * Da listagem: mostra nome, matrícula e turma enquanto o detalhe carrega.
   * `turmaNome` null diz que o aluno não tem matrícula ativa nem trancada.
   */
  aluno: AlunoResumo & { turmaNome?: string | null }
  /** Aluno que já teve os dados pessoais apagados: sem exportar nem anonimizar. */
  anonimizado?: boolean
  onFechar: () => void
  /** Os dados pessoais foram apagados; a lista recarrega. */
  onAnonimizado?: () => void
}

/**
 * Dados completos do aluno (GET /alunos/{id}), com os responsáveis e, para o
 * financeiro, as cobranças. No rodapé, os direitos do titular (LGPD):
 * exportar os dados em PDF e, para o MASTER, anonimizar quem saiu.
 */
export function DetalhesAlunoModal({ aluno, anonimizado = false, onFechar, onAnonimizado }: DetalhesAlunoModalProps) {
  const { dados, carregando, erro } = useDetalhe(buscarAluno, aluno.id)
  const [exportando, setExportando] = useState(false)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [confirmando, setConfirmando] = useState(false)
  const semDados = anonimizado || dados?.situacao === 'ANONIMIZADO'
  // Indefinido enquanto o cadastro carrega; null quando ninguém responde pelas cobranças.
  const responsavelFinanceiro = dados ? (dados.responsaveis.find((r) => r.financeiro)?.nome ?? null) : undefined
  const abas = podeVerFinanceiro()
    ? [
        {
          id: 'financeiro',
          rotulo: 'Financeiro',
          conteudo: (
            <FinanceiroAluno alunoId={aluno.id} responsavelFinanceiro={responsavelFinanceiro} anonimizado={semDados} />
          ),
        },
      ]
    : []
  // Só para a identificação: sem turma na lista, não há matrícula para buscar.
  const buscarMatriculas = useMemo(
    () =>
      aluno.turmaNome === null ? null : (signal: AbortSignal) => listarMatriculas({ alunoId: aluno.id }, signal),
    [aluno.id, aluno.turmaNome],
  )
  // Se falhar, a identificação fica com a turma que veio da lista.
  const { dados: matriculas } = useCarregamento(buscarMatriculas, SEM_MATRICULAS, 'O aluno não existe mais.')
  const atual = matriculaAtual(matriculas)
  const identificacao = [
    `Matrícula ${dados?.matricula ?? aluno.matricula}`,
    ...(atual ? [atual.turmaNome, atual.cursoNome] : [aluno.turmaNome]),
  ]
    .filter(Boolean)
    .join(' · ')

  async function exportar() {
    setErroAcao(null)
    setExportando(true)
    try {
      salvarArquivo(await exportarDadosPessoais(aluno.id), nomeDoPdfDeDadosPessoais(dados?.nome ?? aluno.nome))
    } catch (e) {
      setErroAcao(erroAoExportar(e))
    } finally {
      setExportando(false)
    }
  }

  const acoes =
    semDados || !dados ? null : (
      <>
        {podeAnonimizar() && (
          <Button variant="dangerOutline" icon="eyeOff" onClick={() => setConfirmando(true)}>
            Anonimizar
          </Button>
        )}
        {podeExportar() && (
          <Button icon="download" disabled={exportando} onClick={exportar}>
            {exportando ? 'Gerando PDF…' : 'Exportar dados (PDF)'}
          </Button>
        )}
      </>
    )

  return (
    <>
      <ModalDetalhes
        titulo={dados?.nome ?? aluno.nome}
        subtitulo={identificacao}
        carregando={carregando}
        erro={erro}
        onFechar={onFechar}
        abas={abas}
        acoes={acoes}
        secoes={
          dados
            ? [
                {
                  titulo: 'Dados do aluno',
                  itens: [
                    { rotulo: 'CPF', valor: dados.cpf && formatarCnpjCpf(dados.cpf) },
                    { rotulo: 'Data de nascimento', valor: dados.dataNascimento && formatarData(dados.dataNascimento) },
                    { rotulo: 'E-mail (login)', valor: dados.email },
                    { rotulo: 'Gênero', valor: dados.genero },
                    { rotulo: 'Endereço', valor: formatarEndereco(dados.endereco), largo: true },
                  ],
                },
                ...(dados.responsaveis.length === 0
                  ? [{ titulo: 'Responsáveis', itens: [{ rotulo: 'Responsável', valor: 'Nenhum cadastrado', largo: true }] }]
                  : dados.responsaveis.map((r, i) => ({
                      titulo: dados.responsaveis.length > 1 ? `Responsável ${i + 1}` : 'Responsável',
                      itens: [
                        { rotulo: 'Nome', valor: r.nome },
                        { rotulo: 'Parentesco', valor: r.parentesco },
                        { rotulo: 'E-mail', valor: r.email },
                        { rotulo: 'Telefone', valor: r.telefone && mascararTelefone(r.telefone) },
                        { rotulo: 'Responsável financeiro', valor: r.financeiro ? 'Sim, paga as cobranças' : 'Não' },
                      ],
                    }))),
              ]
            : []
        }
      >
        {semDados && (
          <ModalAlerta>
            Os dados pessoais deste aluno foram apagados a pedido (LGPD). O histórico financeiro continua guardado.
          </ModalAlerta>
        )}
        {!semDados && dados?.anonimizacaoPrevistaEm && (
          <ModalAviso>
            Aluno inativo. Pela política de retenção (LGPD), os dados pessoais serão anonimizados automaticamente em{' '}
            {formatarData(dados.anonimizacaoPrevistaEm)}, se ele não for reativado até lá.
          </ModalAviso>
        )}
        {erroAcao && <ModalAlerta>{erroAcao}</ModalAlerta>}
      </ModalDetalhes>

      {confirmando && (
        <ConfirmarExclusao
          titulo="Anonimizar aluno"
          nome={dados?.nome ?? aluno.nome}
          rotuloConfirmar="Anonimizar"
          iconeConfirmar="eyeOff"
          traduzirErro={motivoParaNaoAnonimizar}
          onFechar={() => setConfirmando(false)}
          onConfirmar={async () => {
            await anonimizarAluno(aluno.id)
            setConfirmando(false)
            onAnonimizado?.()
          }}
        >
          <p>
            Apagar os dados pessoais de <strong>{dados?.nome ?? aluno.nome}</strong>? Nome, CPF, nascimento, e-mail,
            endereço e o vínculo com os responsáveis somem, e o login deixa de funcionar.
          </p>
          <p>
            Cobranças e pagamentos continuam guardados, como a lei exige. Se o aluno pediu os dados, exporte o PDF
            antes. <strong>Esta ação não pode ser desfeita.</strong>
          </p>
        </ConfirmarExclusao>
      )}
    </>
  )
}
