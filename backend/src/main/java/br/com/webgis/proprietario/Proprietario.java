package br.com.webgis.proprietario;

import java.time.OffsetDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Titular de um ou mais imoveis.
 *
 * <p>Antes era um {@code varchar} repetido dentro de cada imovel. Como entidade
 * propria, renomear passa a ser uma alteracao em <strong>uma</strong> linha, que
 * todos os imoveis enxergam pela FK — e o requisito 5 do desafio deixa de ser um
 * {@code UPDATE} em massa sujeito a atualizacao parcial.
 *
 * <p>Campos privados e mudanca de estado por metodo (nao por setter aberto):
 * {@code nome} e {@code nomeNormalizado} precisam mudar juntos, sempre.
 */
@Entity
@Table(name = "proprietario")
public class Proprietario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(name = "nome_normalizado", nullable = false, length = 120)
	private String nomeNormalizado;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private OffsetDateTime criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private OffsetDateTime atualizadoEm;

	/** Exigido pelo JPA. */
	protected Proprietario() {
	}

	public Proprietario(String nome) {
		renomear(nome);
	}

	/**
	 * Altera o nome mantendo a chave de deduplicacao coerente.
	 *
	 * <p>Nenhum imovel e tocado: eles apontam para o id, entao a mudanca aparece
	 * em todos automaticamente.
	 */
	public void renomear(String novoNome) {
		this.nome = NomeNormalizador.exibicao(novoNome);
		this.nomeNormalizado = NomeNormalizador.normalizar(novoNome);
	}

	@PrePersist
	void aoCriar() {
		OffsetDateTime agora = OffsetDateTime.now();
		this.criadoEm = agora;
		this.atualizadoEm = agora;
	}

	@PreUpdate
	void aoAtualizar() {
		this.atualizadoEm = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getNomeNormalizado() {
		return nomeNormalizado;
	}

	public OffsetDateTime getCriadoEm() {
		return criadoEm;
	}

	public OffsetDateTime getAtualizadoEm() {
		return atualizadoEm;
	}

	@Override
	public boolean equals(Object outro) {
		if (this == outro) {
			return true;
		}
		return outro instanceof Proprietario proprietario
				&& id != null
				&& Objects.equals(id, proprietario.id);
	}

	@Override
	public int hashCode() {
		return Proprietario.class.hashCode();
	}
}
