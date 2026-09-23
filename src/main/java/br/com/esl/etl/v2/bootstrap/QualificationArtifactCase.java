package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationArtifactIndex;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.HashSet;
import java.util.List;

/** A technical case identifier selects declared package members, never a business identity. */
public final class QualificationArtifactCase {
    private QualificationArtifactCase() {}

    public static LocalArtifactSequence loadSequence(
            final QualifiedPackage payload,
            final QualificationCampaign campaign,
            final QualificationCampaign.Case item,
            final CancellationToken token)
            throws Exception {
        if (item.action() != QualificationCampaign.Action.SEQUENCE
                || item.mode() != ExecutionMode.BOOTSTRAP
                || item.fault() != QualificationCampaign.Fault.NONE
                || !item.zone().equals(AnalyticScenarioRuntime.ZONE)
                || !new HashSet<>(item.outputs())
                        .equals(new HashSet<>(List.of(AnalyticSqlContract.values())))) {
            throw new IllegalArgumentException("QUAL_SEQUENCE_CASE_SCOPE");
        }
        QualificationArtifactIndex.verify(payload);
        final var sequence =
                new LocalArtifactSequence(
                        payload.member("artifact-cases/" + item.id() + "/sequence.json", "FIXTURE"),
                        token);
        if (sequence.roots() != campaign.roots()
                || sequence.pageSize() != campaign.pageSize()
                || !sequence.start().equals(item.start())
                || !sequence.endExclusive().equals(item.endExclusive())
                || sequence.maximumSeconds() > campaign.maximumSeconds()) {
            throw new IllegalArgumentException("QUAL_SEQUENCE_CASE_LIMITS");
        }
        return sequence;
    }

    static int caseSeconds(
            final QualificationCampaign.Case item,
            final br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration
                    configuration) {
        return item.action() == QualificationCampaign.Action.SEQUENCE
                ? 1800
                : configuration.caseSeconds();
    }

    public static LocalArtifactScenario load(
            final QualifiedPackage payload,
            final QualificationCampaign campaign,
            final QualificationCampaign.Case item,
            final CancellationToken token)
            throws Exception {
        if (item.action() != QualificationCampaign.Action.ARTIFACT
                || item.mode() != ExecutionMode.BOOTSTRAP
                || item.fault() != QualificationCampaign.Fault.NONE
                || !item.zone().equals(AnalyticScenarioRuntime.ZONE)
                || item.outputs().size() != 19
                || !new HashSet<>(item.outputs())
                        .equals(new HashSet<>(List.of(AnalyticSqlContract.values())))) {
            throw new IllegalArgumentException("QUAL_ARTIFACT_CASE_SCOPE");
        }
        QualificationArtifactIndex.verify(payload);
        final String directory = "artifact-cases/" + item.id() + "/";
        final var scenario =
                new LocalArtifactScenario(
                        payload.member(directory + "input.json", "FIXTURE"),
                        payload.member(directory + "oracle.json", "ORACLE"),
                        token);
        if (scenario.roots() != campaign.roots()
                || scenario.pageSize() != campaign.pageSize()
                || !item.start().equals(scenario.start())
                || !item.endExclusive().equals(scenario.endExclusive())) {
            throw new IllegalArgumentException("QUAL_ARTIFACT_CASE_LIMITS");
        }
        scenario.verifyFiles(token);
        return scenario;
    }
}
