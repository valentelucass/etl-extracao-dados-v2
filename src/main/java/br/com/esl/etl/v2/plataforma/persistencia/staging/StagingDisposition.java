package br.com.esl.etl.v2.plataforma.persistencia.staging;

/** Resultado de validação mínimo que pode atravessar o boundary de staging sem payload bruto. */
public enum StagingDisposition {
    VALID,
    QUARANTINE
}
