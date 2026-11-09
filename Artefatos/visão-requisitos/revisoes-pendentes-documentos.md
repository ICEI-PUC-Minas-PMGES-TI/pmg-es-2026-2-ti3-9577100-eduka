# Revisões pendentes nos documentos em PDF

O sistema mudou e estes documentos ainda não acompanham, porque só existem em PDF neste repositório e os arquivos
originais não estão aqui. **Enquanto não forem atualizados, vale o que está escrito abaixo.** Ao gerar uma nova versão
de um documento, aplique os itens dele e apague-os desta lista.

## Decisões do grupo (05/11/2026)

| # | Tema | Decisão |
|---|---|---|
| 1 | Saldo dos relatórios | Recebido menos despesas **pagas**, pelo dia do pagamento. O gráfico usa o mesmo critério e passa a bater com o do Início. O card de despesas continua mostrando as lançadas. |
| 2 | Estorno | O valor devolvido sai do recebido, **no período do estorno**. Vale também para estorno parcial. A cobrança fica encerrada (`PAGA`): para cobrar de novo, a escola lança outra cobrança. |
| 3 | Aluno com mais de uma matrícula ativa | A Nova cobrança pergunta qual matrícula recebe a cobrança, com a opção de cobrança avulsa (sem curso). |

## Documento de Interface de Usuário (`Documento_Interface_de_Usuario_EDUKA.pdf`)

### IU19 — Relatórios financeiros

| Campo | Como fica |
|---|---|
| Total recebido | Soma dos pagamentos confirmados no período, inclusive baixas manuais, **menos os estornos do período**. A quantidade é a de pagamentos. |
| Total de despesas | Sem mudança: despesas lançadas (não canceladas) que vencem no período. |
| Saldo do período | Total recebido menos despesas **pagas** no período. |
| Receitas e despesas no período | Por mês, o recebido e as despesas **pagas**, com o saldo de cada mês. |
| Formas de pagamento | PIX, Boleto e, se houver, **Baixa manual**. Sai a frase "só existem PIX e boleto". Os valores já vêm sem os estornos. |
| Inadimplência | Valor vencido e não pago sobre tudo o que já venceu no período. Cobrança que ainda não venceu não entra. |
| Resumo do período | Recebido é o que entrou pelas cobranças que vencem no período, sem o estornado. Cobrança estornada não volta para "em aberto". |

### IU19-A — Modal Exportar em CSV

* **Pagamentos:** uma linha por pagamento ou estorno, com a coluna *Movimento*. O estorno vem com valor negativo, para o
  arquivo somar o mesmo que a tela.
* **Nome do arquivo:** traz as datas completas (`relatorio-financeiro-2026-08-01-a-2026-09-30.csv`), porque o período
  pode começar ou terminar no meio do mês.

### IU20 e IU20-A — Cobranças e Nova cobrança

* **IU20:** sai "Não há registro de pagamento manual", decisão revista em 21/10. O detalhe mostra o valor estornado,
  quando houver.
* **IU20-A:** o campo *O que está sendo cobrado* passa a ser gravado, porque agrupa o resumo por tipo dos relatórios.
* **IU20-A, campo novo:** *Matrícula que recebe a cobrança*, seleção única, obrigatório e visível só quando o aluno tem
  mais de uma matrícula ativa. Valores: as matrículas ativas e *Cobrança avulsa (sem curso)*.

### IU17 e IU17-D — Pagamentos do aluno e comprovante

* A cobrança paga e o comprovante mostram o valor devolvido, quando houver estorno.

### IU22 e IU05 — Início do financeiro e do master

* O recebido e o gráfico descontam os estornos, no mês em que aconteceram.

## DER e diagrama de classes (`Artefatos/der`, `Artefatos/diagrama-classes-dominio`)

* `COBRANCA.TIPO`: `MENSALIDADE`, `MATRICULA`, `MATERIAL` ou `OUTRO` (V40).
* `PAGAMENTO_COBRANCA.VALOR_ESTORNADO`: soma dos estornos do pagamento (V41).
* Nova tabela `ESTORNO_COBRANCA`: uma linha por estorno, com valor e data, ligada à cobrança e à escola (V41).
