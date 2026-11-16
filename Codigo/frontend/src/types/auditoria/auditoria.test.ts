import { describe, expect, it } from 'vitest'
import { descreverAcao } from './auditoria'

describe('descreverAcao', () => {
  it('descreve as ações da estrutura acadêmica, da chamada e dos cadastros de apoio', () => {
    expect(descreverAcao('EDITAR', 'FREQUENCIA')).toBe('Editou a chamada de um aluno')
    expect(descreverAcao('ABRIR_MATRICULAS', 'PERIODO_LETIVO')).toBe('Abriu as matrículas de um período letivo')
    expect(descreverAcao('ENCERRAR', 'PERIODO_LETIVO')).toBe('Encerrou um período letivo')
    expect(descreverAcao('VINCULAR', 'RESPONSAVEL')).toBe('Vinculou um responsável')
    expect(descreverAcao('ATENDER', 'PEDIDO_CORRECAO')).toBe('Atendeu um pedido de correção')
    expect(descreverAcao('EXCLUIR', 'ALOCACAO')).toBe('Excluiu a alocação de um professor')
  })
})
