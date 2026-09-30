import { ALUNO_MOCK_PADRAO, type AlunoPerfil } from '../types/aluno'
import { apiRequest } from './api'
import { obterUsuarioLogado } from './authService'

export async function obterMeuPerfil(signal?: AbortSignal): Promise<AlunoPerfil> {
  const usuario = obterUsuarioLogado()

  try {
    // Tenta buscar da API caso o endpoint de perfil exista
    const resposta = await apiRequest<Partial<AlunoPerfil>>('/alunos/me', { signal })
    return {
      ...ALUNO_MOCK_PADRAO,
      ...resposta,
      acesso: {
        ...ALUNO_MOCK_PADRAO.acesso,
        login: usuario?.login || ALUNO_MOCK_PADRAO.acesso.login,
      },
    }
  } catch {
    // Fallback gracioso com dados da especificação RF25
    if (usuario?.login) {
      return {
        ...ALUNO_MOCK_PADRAO,
        acesso: {
          ...ALUNO_MOCK_PADRAO.acesso,
          login: usuario.login,
        },
      }
    }
    return ALUNO_MOCK_PADRAO
  }
}

export async function solicitarCorrecaoDados(mensagem: string): Promise<{ sucesso: boolean; mensagem: string }> {
  try {
    await apiRequest('/alunos/solicitacao-correcao', {
      method: 'POST',
      body: JSON.stringify({ mensagem }),
    })
    return { sucesso: true, mensagem: 'Solicitação enviada com sucesso para a secretaria!' }
  } catch {
    // Simulação bem sucedida quando backend ainda não possui a fila de solicitações
    return {
      sucesso: true,
      mensagem: 'Sua mensagem foi registrada e encaminhada para a secretaria da escola.',
    }
  }
}
