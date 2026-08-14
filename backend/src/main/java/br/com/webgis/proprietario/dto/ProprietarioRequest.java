package br.com.webgis.proprietario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de criacao e de renomeacao de proprietario. */
public record ProprietarioRequest(

		@NotBlank(message = "informe o nome")
		@Size(max = 120, message = "no maximo 120 caracteres")
		String nome) {
}
