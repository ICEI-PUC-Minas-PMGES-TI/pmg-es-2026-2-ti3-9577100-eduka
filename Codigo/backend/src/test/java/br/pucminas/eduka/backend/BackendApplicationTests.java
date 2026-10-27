package br.pucminas.eduka.backend;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.integracao.BancoRealTest;

/**
 * A aplicacao sobe contra um Postgres de verdade: todas as migrations rodam
 * como o papel da aplicacao e o Hibernate confere cada entidade com a tabela
 * ({@code ddl-auto=validate}).
 */
class BackendApplicationTests extends BancoRealTest {

	@Test
	void contextLoads() {
	}

}
