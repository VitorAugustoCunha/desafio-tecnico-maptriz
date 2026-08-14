package br.com.webgis.imovel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "imovel")
public class Imovel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long id;

	@Column(length = 120)
	public String proprietario;

	@Column(length = 120)
	public String municipio;

	@Column(length = 2)
	public String uf;

	@Column(length = 100)
	public String bairro;

	@Column(length = 150)
	public String rua;

	@Column(length = 10)
	public String numero;

	@Column(precision = 10, scale = 7)
	public BigDecimal latitude;

	@Column(precision = 10, scale = 7)
	public BigDecimal longitude;

	@Column(name = "area_m2", precision = 12, scale = 2)
	public BigDecimal areaM2;

	public boolean ativo;

	@Column(name = "criado_em")
	public OffsetDateTime criadoEm;

	@Column(name = "atualizado_em")
	public OffsetDateTime atualizadoEm;
}
