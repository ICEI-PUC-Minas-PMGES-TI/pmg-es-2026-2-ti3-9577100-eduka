package br.pucminas.eduka.backend.service.privacidade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina diaria da politica de retencao ({@code app.lgpd.retencao.cron}):
 * anonimiza o aluno inativo ha mais do que o prazo ({@link PoliticaRetencao}).
 *
 * <p>
 * Se a aplicacao estiver fora do ar no horario, a proxima execucao pega quem
 * ficou para tras; rodar de novo nao muda nada.
 * </p>
 */
@Component
public class RetencaoDadosAlunoRotina {

    private static final Logger log = LoggerFactory.getLogger(RetencaoDadosAlunoRotina.class);

    private final RetencaoDadosAlunoService service;

    public RetencaoDadosAlunoRotina(RetencaoDadosAlunoService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.lgpd.retencao.cron}", zone = "America/Sao_Paulo")
    public void executar() {
        RetencaoDadosAlunoService.Resultado resultado = service.anonimizarVencidos();
        log.info("Rotina de retencao: {} aluno(s) anonimizado(s), {} adiado(s)", resultado.anonimizados(),
                resultado.adiados());
    }
}
