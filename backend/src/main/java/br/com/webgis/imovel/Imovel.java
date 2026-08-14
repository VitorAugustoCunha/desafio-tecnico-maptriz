package br.com.webgis.imovel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import br.com.webgis.proprietario.Proprietario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Imovel georreferenciado.
 *
 * <p>Latitude e longitude sao o <strong>centro</strong> do lote, em WGS 84
 * (EPSG:4326) — convencao adotada e documentada em docs/DECISIONS.md, ADR-004.
 *
 * <p>As colunas espaciais ({@code geom} em EPSG:31982 e {@code ponto} em 4326)
 * existem no banco mas <strong>nao sao mapeadas aqui</strong>, de proposito:
 *
 * <ul>
 *   <li>a listagem carrega entidades e nao deve arrastar geometria junto;</li>
 *   <li>o poligono e construido e comparado pelo PostGIS, entao mante-lo fora do
 *       estado da entidade elimina a chance de a aplicacao e o banco discordarem
 *       sobre qual e a geometria atual;</li>
 *   <li>evita a dependencia de Hibernate Spatial para um tipo que so e lido em
 *       consulta espacial e escrito por uma funcao SQL.</li>
 * </ul>
 *
 * O acesso a geometria fica em {@code br.com.webgis.gis}.
 */
@Entity
@Table(name = "imovel")
public class Imovel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proprietario_id", nullable = false)
	private Proprietario proprietario;

	@Column(nullable = false, length = 120)
	private String municipio;

	@Column(nullable = false, length = 2)
	private String uf;

	@Column(nullable = false, length = 100)
	private String bairro;

	@Column(nullable = false, length = 150)
	private String rua;

	@Column(nullable = false, length = 10)
	private String numero;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "area_m2", nullable = false, precision = 12, scale = 2)
	private BigDecimal areaM2;

	/** Metros. Nula em imovel legado, que so tem ponto. Anda em par com {@link #comprimentoM}. */
	@Column(name = "largura_m", precision = 10, scale = 2)
	private BigDecimal larguraM;

	/** Metros. Nula em imovel legado. Anda em par com {@link #larguraM}. */
	@Column(name = "comprimento_m", precision = 10, scale = 2)
	private BigDecimal comprimentoM;

	@Column(nullable = false)
	private boolean ativo;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private OffsetDateTime criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private OffsetDateTime atualizadoEm;

	/** Exigido pelo JPA. */
	protected Imovel() {
	}

	public Imovel(Proprietario proprietario, DadosDoImovel dados) {
		this.proprietario = Objects.requireNonNull(proprietario, "proprietario");
		aplicar(dados);
	}

	/** Aplica os dados editaveis. Id, proprietario e datas nao entram aqui. */
	public void aplicar(DadosDoImovel dados) {
		this.municipio = dados.municipio();
		this.uf = dados.uf();
		this.bairro = dados.bairro();
		this.rua = dados.rua();
		this.numero = dados.numero();
		this.latitude = dados.latitude();
		this.longitude = dados.longitude();
		this.areaM2 = dados.areaM2();
		this.larguraM = dados.larguraM();
		this.comprimentoM = dados.comprimentoM();
		this.ativo = dados.ativo();
	}

	public void trocarProprietario(Proprietario novoProprietario) {
		this.proprietario = Objects.requireNonNull(novoProprietario, "proprietario");
	}

	/**
	 * Alinha area e ponto com o poligono desenhado.
	 *
	 * <p>No modo desenho, quem manda e a geometria: a area vem do
	 * {@code ST_Area} e o ponto vem do centroide, ambos calculados pelo PostGIS.
	 * Guardar a area que o cliente enviou deixaria o imovel afirmando um tamanho
	 * que o proprio poligono desmente — e o mapa mostraria a segunda versao.
	 *
	 * <p>As dimensoes sao zeradas porque um lote desenhado nao tem "largura" e
	 * "comprimento" unicos.
	 */
	public void sincronizarComPoligono(BigDecimal areaCalculada, BigDecimal latitudeDoCentroide,
			BigDecimal longitudeDoCentroide) {

		this.areaM2 = areaCalculada;
		this.latitude = latitudeDoCentroide;
		this.longitude = longitudeDoCentroide;
		this.larguraM = null;
		this.comprimentoM = null;
	}

	/** {@code true} quando o imovel tem dimensoes e, portanto, poligono no banco. */
	public boolean possuiDimensoes() {
		return larguraM != null && comprimentoM != null;
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

	public Proprietario getProprietario() {
		return proprietario;
	}

	public String getMunicipio() {
		return municipio;
	}

	public String getUf() {
		return uf;
	}

	public String getBairro() {
		return bairro;
	}

	public String getRua() {
		return rua;
	}

	public String getNumero() {
		return numero;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public BigDecimal getAreaM2() {
		return areaM2;
	}

	public BigDecimal getLarguraM() {
		return larguraM;
	}

	public BigDecimal getComprimentoM() {
		return comprimentoM;
	}

	public boolean isAtivo() {
		return ativo;
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
		return outro instanceof Imovel imovel && id != null && Objects.equals(id, imovel.id);
	}

	@Override
	public int hashCode() {
		return Imovel.class.hashCode();
	}
}
