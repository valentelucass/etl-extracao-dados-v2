package br.com.esl.etl.v2.plataforma.configuracao;

/** Porta para o canal protegido que fornece segredos ao runtime. */
@FunctionalInterface
public interface SecretProvider {

    /** Resolve um segredo exclusivamente no instante em que uma fonte habilitada precisa dele. */
    String require(SecretKey key);
}
