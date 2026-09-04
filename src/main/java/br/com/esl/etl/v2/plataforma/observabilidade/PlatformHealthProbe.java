package br.com.esl.etl.v2.plataforma.observabilidade;

import java.time.Duration;

@FunctionalInterface
public interface PlatformHealthProbe {

    PlatformHealthSnapshot readiness(Duration maximumRunningAge);
}
