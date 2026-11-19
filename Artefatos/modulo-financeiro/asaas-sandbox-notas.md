# Asaas Sandbox — notas do spike (NOVA-2)

Respostas às pendências da §18 da spec da Sprint 3 (`spec-sprint3-pagamentos.md`), testadas no Sandbox em
19/10/2026 com escola, aluno e cobranças fictícios. As fixtures capturadas estão em
`Codigo/backend/src/test/resources/asaas/`.

**Status:** todas as perguntas da §18 respondidas. Só não foi possível capturar `PAYMENT_CONFIRMED` (o Sandbox não
emite esse evento para PIX/boleto, ver A3). Os achados da §4 mudam contratos da spec e precisam de acordo do grupo.

---

## 1. Preparar o Sandbox de cada dev

1. Criar conta em <https://sandbox.asaas.com> **como pessoa jurídica (CNPJ)**. Conta PF não cria subconta: a
   documentação exige CNPJ (Resoluções Conjuntas BCB nº 16/17).
2. Em **Minha conta → Configurações → Sandbox**, ligar **BaaS para subcontas** e **Autoaprovação de subcontas**.
   Só existe na interface; não há endpoint.
3. Em **Integrações → Chaves de API**, gerar a chave e pôr em `Codigo/.env`:
   ```
   ASAAS_API_URL=https://api-sandbox.asaas.com
   ASAAS_API_KEY='$aact_hmlg_...'
   ```
   **Aspas simples são obrigatórias:** a chave começa com `$` e o Docker Compose interpolaria o resto como
   variável (a chave chegaria vazia).
4. Limite do Sandbox: **20 subcontas por dia** por conta. Usar poucas escolas de teste.

## 2. Webhook em dev (P-07)

- O Asaas só envia para URL pública. Em dev, usar o túnel grátis do Cloudflare, sem conta:
  ```bash
  cloudflared tunnel --url http://localhost:8080
  # imprime https://<nome-aleatorio>.trycloudflare.com
  ```
  `ASAAS_WEBHOOK_URL=https://<nome>.trycloudflare.com/webhooks/asaas`
- **A URL muda a cada execução**, então não é estável. Ao reiniciar o túnel, o webhook das contas já criadas
  continua apontando para a URL antiga. URL fixa exige túnel nomeado (conta Cloudflare + domínio) e está fora da
  sprint. O que as rotinas cobrem nesse intervalo:
  - **Situação da conta filha:** a rotina de contas (a cada 15 min) lê a situação no Asaas.
  - **Cobrança e fatura presas em `EMITINDO`:** a reconciliação (a cada 1 min, `ReconciliacaoRotina`) acha pela
    referência e grava emitida, paga ou com falha de emissão.
  - **Pagamento de cobrança já emitida:** nenhuma rotina cobre, o aviso precisa chegar. Atualize a URL do
    webhook no Asaas e, se a fila tiver sido interrompida pelas falhas, reative-a no painel: o Asaas guarda os
    avisos não entregues e os reenvia.
- Aviso que chegou mas falhou ao aplicar fica em `ERRO` e é reaplicado a cada 5 min (`EventoProvedorRotina`), até 10
  tentativas.
- O webhook da subconta criado por `webhooks[]` em `POST /v3/accounts` funcionou: os eventos de situação da conta
  e de cobrança chegaram.
- O webhook da conta-pai (Fluxo A) também pode ser criado **por API** (`POST /v3/webhooks`), não só pelo painel:
  ```json
  {"name":"Eduka","url":"<ASAAS_WEBHOOK_URL>","email":"<ASAAS_WEBHOOK_EMAIL>","enabled":true,
   "interrupted":false,"authToken":"<ASAAS_WEBHOOK_TOKEN>","sendType":"SEQUENTIALLY",
   "events":["PAYMENT_CREATED","PAYMENT_CONFIRMED","PAYMENT_RECEIVED","PAYMENT_DELETED"]}
  ```
- **Depois do spike (RF22.2):** o Edúka passou a auditar estornos e contestações. Os webhooks também precisam
  assinar `PAYMENT_REFUNDED`, `PAYMENT_PARTIALLY_REFUNDED`, `PAYMENT_CHARGEBACK_REQUESTED`,
  `PAYMENT_CHARGEBACK_DISPUTE` e `PAYMENT_AWAITING_CHARGEBACK_REVERSAL`. As contas filhas criadas a partir de agora
  já assinam (`AsaasAdapter.EVENTOS_WEBHOOK`); o webhook da conta-pai e o das contas filhas já existentes precisam
  ser atualizados (`PUT /v3/webhooks/{id}` com a chave da conta dona, ou pelo painel). Nomes conferidos na
  documentação de webhooks de cobranças do Asaas; ainda não vistos chegando no Sandbox.
- **Valor do estorno:** o Edúka lê `payment.refunds` do aviso (`status` e `value`) e soma só os `DONE`, conforme a
  [documentação da cobrança](https://docs.asaas.com/reference/retrieve-a-single-payment). Nos exemplos capturados no
  Sandbox o campo só apareceu nulo, em cobranças sem estorno. **A conferir no Sandbox:** se o `PAYMENT_PARTIALLY_REFUNDED`
  já chega com o estorno em `DONE`. Se chegar `PENDING`, o valor não é descontado e o estorno fica só na auditoria até
  outro aviso trazer o `DONE`.
- **`email` é obrigatório** (A2).
- O token chega no header **`Asaas-Access-Token`** (o valor exato de `authToken`).
- Antes de derrubar o túnel, remover ou desligar o webhook (`DELETE /v3/webhooks/{id}` com a chave da conta dona).
  Senão o Asaas acumula falhas, interrompe a fila e manda e-mail.

## 3. Respostas da §18

| ID | Resposta | Como foi verificado |
|---|---|---|
| P-01 | **Pagamento:** `POST /v3/sandbox/payment/{id}/confirm`, sem corpo → 200 com a cobrança já `RECEIVED` (PIX e boleto), sem passar por `CONFIRMED`. **Vencer:** `POST /v3/sandbox/payment/{id}/overdue` → `OVERDUE` e `dueDate` = hoje − 3 dias. **Aprovar conta:** com a autoaprovação ligada, a subconta nasce aprovada (tudo `APPROVED` em ~1 s). Sem ela, `POST /v3/sandbox/myAccount/approve` com a chave da subconta (doc, não testado) | Testado (pagamento, vencimento, autoaprovação) |
| P-03 | A credencial vem em **`apiKey`** (string, 166 caracteres) na raiz da resposta. **`accessToken` é um objeto**, não uma string: `{id, name, enabled, disabledReason, expirationDate, dateCreated, projectedExpirationDateByLackOfUse, apiKey}`. Ler `apiKey` da raiz (A15) | Testado (`conta-criada.json`) |
| P-07 | Túnel `cloudflared` sem conta; URL muda a cada execução (§2) | Testado |
| P-10 | **`POST /v3/accounts` não tem campos de conta bancária externa.** O `accountNumber` (`agency`, `account`, `accountDigit`) da resposta é o número da conta **Asaas** criada, não dados que enviamos. No Sandbox, `bankAccountInfo` fica `APPROVED` sem enviar nada (A16) | Testado + schema da doc "Criar subconta" |
| P-11 | O evento traz o objeto `payment` **completo**, com os mesmos campos do `GET /v3/payments/{id}`: `paymentDate`, `clientPaymentDate`, `confirmedDate`, `creditDate`, `estimatedCreditDate`, `pixTransaction`, `nossoNumero`, `transactionReceiptUrl`. Não precisa consultar a cobrança depois do evento | Testado (fixtures `webhook-payment-*.json`) |
| P-12 | `expirationDate` do QR = **vencimento + 1 ano, 23:59:59** (`"2027-10-24 23:59:59"`, sem fuso). Chamadas repetidas devolvem o mesmo `payload`. Depois de vencida, o QR é o mesmo e a validade é recalculada | Testado |
| P-13 | **Não dá para recuperar** a chave. `GET` e `POST /v3/accounts/{id}/accessTokens` → 400 `invalid_action` "Para utilizar este recurso é necessário ter uma configuração de whitelist de IPs configurada". A busca por CNPJ devolve `apiKey: null` e `accessToken: null`. Fica o caminho `CREDENCIAL_INDISPONIVEL` da spec | Testado |
| P-14 | `PUT /v3/payments/{id}` PIX → BOLETO e BOLETO → PIX **mantém `id` e `externalReference`**; ao virar boleto, ganha `nossoNumero`. `PUT` só com `billingType` também é aceito. Cobrança paga → 400 `invalid_billingType` | Testado |
| P-15 | Doc "Notificações": `notificationDisabled: true` desliga todas as notificações de cobrança ao cliente. `GET /v3/customers/{id}/notifications` continua listando cada aviso como `enabled: true`, então não serve de prova. O envio real não é observável pela API | Doc + teste do cadastro |
| P-16 | Sandbox: **20 subcontas por dia**; as comunicações das subcontas vão para o e-mail da conta raiz. A criação não enviou nada ao e-mail da subconta (fictício) que dê para observar | Doc "Criação de subcontas" |

## 4. Achados que afetam a spec (o grupo precisa combinar)

| # | Achado | Impacto |
|---|---|---|
| A1 | Conta recém-criada **não tem chave PIX**: `pixQrCode` → 400 `invalid_action` "Você não possui uma chave Pix cadastrada para recebimentos de cobranças via Pix". Reproduzido na conta-pai e na subconta: o Asaas **criou sozinho** uma chave aleatória (EVP) ~3 s depois da primeira falha, que ficou `ACTIVE` em < 40 s; depois o QR funcionou. Não deu para separar se o gatilho é criar a cobrança PIX ou pedir o QR | O primeiro aluno a pagar com PIX numa escola nova recebe erro. **Testado numa segunda subconta:** `POST /v3/pix/addressKeys` `{"type":"EVP"}` com a chave da escola, logo após criar a conta → 200, chave já `ACTIVE`; o primeiro `pixQrCode` saiu 200 e o Asaas não criou outra chave. Recomendação: ao ativar recebimentos, `GET /v3/pix/addressKeys` e, se não houver chave, criar a EVP; tratar o 400 de "sem chave Pix" como inconclusivo, por segurança |
| A2 | `email` obrigatório no webhook: sem ele, 400 `invalid_email` "O parâmetro email deve ser informado" | `asaas.webhook-email` vazio não pode gerar `webhooks[]` com e-mail vazio; tratar como obrigatório quando `webhook-url` estiver preenchida |
| A3 | Sandbox não emite `PAYMENT_CONFIRMED` para PIX/boleto: só `PAYMENT_RECEIVED` | `webhook-payment-confirmed.json` não pôde ser capturada. O mapeamento da §5.3 continua, mas o fluxo de teste do Sandbox é sempre `RECEIVED` |
| A4 | `DELETE /v3/payments/{id}` repetido → **200** `{"deleted":true}`, não 404. Id inexistente → 404 corpo vazio | Bate com "404 = sucesso" e acrescenta: 200 repetido também é sucesso |
| A5 | `GET` de cobrança removida → 200 com `deleted: true` e o status anterior (`OVERDUE`). `GET /v3/payments?externalReference=` **omite** removidas | `buscarCobrancaPorReferencia` não precisa filtrar `deleted` (manter o filtro por segurança) |
| A6 | Um `PUT` boleto → PIX deu 500 `unknow.error`; repetido 3 s depois → 200 | Confirma tratar 5xx como inconclusivo e consultar/repetir |
| A7 | Id do evento tem formato `evt_<32 hex>&<número>` (ex.: `evt_05b7...f621&22696482`, 45 caracteres) | Coluna do id externo do evento precisa de folga (≥ 100) e o `&` não pode ir cru em URL |
| A8 | Cobrança em BOLETO também devolve `pixQrCode` (boleto com PIX) | Sem impacto; o roteador só busca o QR quando a forma é PIX |
| A9 | O `payload` PIX e a imagem do QR levam o **nome e a cidade do titular da conta** recebedora (na subconta: "Escola Teste Eduka Ltda", Belo Horizonte) | Esperado. Na fixture `pix-qrcode.json` (capturada na conta-pai, PF) o nome foi trocado e a imagem virou PNG 1×1; o CRC do `payload` ficou inválido |
| A10 | Taxa do Sandbox: `netValue` = `value` − 0,99 | Só informativo |
| A11 | O `accountStatus` do evento de conta é a **foto atual** da conta, não o estado do momento do evento: `ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL` chegou com `general: APPROVED` | O aplicador deve usar o `accountStatus` do payload (ou consultar), **nunca** derivar a situação do nome do evento |
| A12 | `POST /v3/accounts` levou **7,4 s** (as outras chamadas, 0,1–0,8 s) | Perto do timeout de leitura de 10 s. Timeout na criação é inconclusivo: antes de recriar, `buscarContaPorCnpj` (já previsto). Avaliar timeout maior só para essa operação |
| A13 | CNPJ é **único no Sandbox inteiro** (compartilhado): um CNPJ gerado já estava em uso → 400 `invalid_object` "O CNPJ ... já está em uso." | Testes manuais e S1 precisam gerar CNPJ válido aleatório a cada escola |
| A14 | Eventos da subconta trazem `account.id` = id da subconta e `account.ownerId` = id da conta-pai. Na conta-pai, `ownerId` é `null` | Dá para identificar a escola pelo `account.id` do evento (= `idContaExterna`) |
| A15 | `accessToken` na resposta da criação é objeto (P-03) | §5.3 diz "credencial em `accessToken` (se faltar, ler `apiKey`)". Inverter: ler `apiKey`; `accessToken` como string quebraria a desserialização |
| A16 | Criação de subconta não recebe conta bancária externa (P-10) | `DadosBancarios` em `DadosNovaConta` e os campos bancários do formulário R6 não têm para onde ir na criação. Saque para banco externo é transferência (fora da sprint). O grupo decide se remove do contrato ou guarda para depois |
| A17 | Uma segunda `POST /v3/pix/addressKeys` na mesma conta, segundos depois → **400** `invalid_action` "Serviço temporariamente indisponível devido a excesso de consultas. Tente novamente mais tarde." | Falha temporária com status 400: pela tabela da §5.3 viraria `ProvedorRecusouException` (definitiva). Listar as chaves antes de criar evita a chamada; se acontecer, a ativação deve poder ser repetida |

## 5. Fixtures (`Codigo/backend/src/test/resources/asaas/`)

Respostas reais do Sandbox. Alterações: CPF do aluno → `12345678909`, CNPJ da escola → `11222333000181`, chave da
subconta → `$aact_hmlg_CHAVE_FICTICIA_DA_SUBCONTA`, e o nome/imagem do QR (A9). E-mails e telefones já eram
fictícios.

| Arquivo | Origem |
|---|---|
| `conta-criada.json` | `POST /v3/accounts` (subconta BaaS com `webhooks[]`) |
| `conta-busca-cnpj.json` | `GET /v3/accounts?cpfCnpj=` (credenciais `null`) |
| `conta-status.json` | `GET /v3/myAccount/status` com a chave da subconta |
| `cliente-criado.json` | `POST /v3/customers` com `notificationDisabled: true` |
| `cobranca-pix-criada.json` / `cobranca-boleto-criada.json` | `POST /v3/payments` |
| `cobranca-recebida.json` | `GET /v3/payments/{id}` após confirmar (PIX que passou por boleto, por isso tem `nossoNumero`) |
| `cobranca-remover.json` | `DELETE /v3/payments/{id}` |
| `cobranca-removida.json` | `GET /v3/payments/{id}` após remover |
| `lista-cobrancas-vazia.json` | `GET /v3/payments?externalReference=` sem resultado |
| `pix-qrcode.json` | `GET /v3/payments/{id}/pixQrCode` (sanitizado, A9) |
| `pix-chave-criada.json` | `POST /v3/pix/addressKeys` `{"type":"EVP"}` na subconta (imagem trocada por PNG 1×1) |
| `erro-400-excesso-consultas.json` | segunda criação de chave PIX seguida (A17) |
| `boleto-linha.json` | `GET /v3/payments/{id}/identificationField` |
| `erro-400.json` | `PUT` em cobrança paga |
| `erro-400-remover-paga.json` | `DELETE` em cobrança paga |
| `erro-400-pix-sem-chave.json` | `pixQrCode` em conta sem chave PIX (A1) |
| `erro-400-cnpj-em-uso.json` | `POST /v3/accounts` com CNPJ repetido (A13) |
| `erro-401.json` | chave inválida |
| `erro-500.json` | `PUT` com falha transitória (A6) |
| `webhook-account-approved.json` | evento `ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED` |
| `webhook-account-awaiting-approval.json` | evento `ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL` com foto já aprovada (A11) |
| `webhook-payment-created.json` | evento `PAYMENT_CREATED` (PIX, conta-pai) |
| `webhook-payment-received.json` / `webhook-payment-received-boleto.json` | evento `PAYMENT_RECEIVED` (conta-pai) |
| `webhook-payment-received-subconta.json` | evento `PAYMENT_RECEIVED` da subconta (`ownerId` preenchido, A14) |
| `webhook-payment-overdue.json` | evento `PAYMENT_OVERDUE` |
| `webhook-payment-deleted.json` | evento `PAYMENT_DELETED` |

Falta: `webhook-payment-confirmed.json` (não existe no Sandbox para PIX/boleto, A3).

## 6. Estado deixado no Sandbox

- Conta-pai PJ: sem webhooks; cobranças de teste `eduka-spike-cob-*`.
- Subconta "Escola Teste Eduka Ltda" (`3d134583-...`), aprovada, com chave PIX EVP criada pelo Asaas, sem
  webhooks, uma cobrança paga `eduka-spike-sub-cob-1`.
- Subconta "Escola Teste Eduka Dois Ltda" (`2f8c556f-...`), aprovada, criada sem webhook, com chave EVP criada por
  API e uma cobrança PIX pendente `eduka-spike-sub2-cob-1`.

## 7. Pendências

- Decisões de contrato: A1, A2, A11, A12, A15, A16, A17.
