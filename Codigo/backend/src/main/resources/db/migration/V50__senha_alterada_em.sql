-- ---------------------------------------------------------------------------
-- Renovacao da sessao pelo refresh token (POST /login/renovacao).
-- ---------------------------------------------------------------------------
-- O refresh token vive 30 dias e nao fica gravado em lugar nenhum. Para que
-- trocar a senha encerre as sessoes abertas, a renovacao recusa o refresh
-- emitido antes do instante gravado aqui (ver AuthService.renovarSessao).
-- Nulo enquanto a senha nunca foi trocada: nenhum refresh e barrado por ele.
ALTER TABLE USUARIO ADD COLUMN IF NOT EXISTS SENHA_ALTERADA_EM TIMESTAMPTZ;
