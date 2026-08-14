package br.com.webgis.gis.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Configuracao do pool GIS")
class GisWorkerPropertiesTest {

	@Test
	@DisplayName("recusa fila ilimitada: sem fila limitada nao existe backpressure")
	void recusaFilaZerada() {
		assertThatThrownBy(() -> new GisWorkerProperties(2, 4, 0, 30, 120, 500))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("backpressure");
	}

	@Test
	@DisplayName("recusa maximo menor que o nucleo")
	void recusaMaximoMenorQueNucleo() {
		assertThatThrownBy(() -> new GisWorkerProperties(4, 2, 8, 30, 120, 500))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("aplica padroes seguros quando nada e configurado")
	void padroes() {
		GisWorkerProperties padrao = new GisWorkerProperties(null, null, null, null, null, null);

		assertThat(padrao.tamanhoNucleo()).isEqualTo(2);
		assertThat(padrao.tamanhoMaximo()).isEqualTo(4);
		assertThat(padrao.capacidadeFila()).isEqualTo(8);
		assertThat(padrao.tamanhoLote()).isEqualTo(500);
	}
}
