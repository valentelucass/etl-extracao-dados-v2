package br.com.esl.etl.v2.plataforma.identidade;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import java.util.Objects;

/** Catálogo fechado V2-009a, sem descoberta dinâmica nem materialização do universo de chaves. */
public final class FirstWaveIdentityCatalog {

    private static final FirstWaveIdentityContract COLETAS =
            FirstWaveIdentityContract.create(
                    FirstWaveIdentityContract.Entity.COLETAS,
                    "dataexport-6908",
                    DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                    new FirstWaveIdentityContract.SourceKeyDefinition(
                            "/id", "id", FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY),
                    FirstWaveIdentityContract.BusinessAliasDefinition.versioned(
                            "/sequence_code",
                            "sequence_code",
                            FirstWaveIdentityContract.AliasCardinalityPolicy
                                    .OBSERVED_ONE_TO_ONE_NOT_GLOBAL),
                    FirstWaveIdentityContract.RootCardinalityPolicy
                            .LOGICAL_ROOT_MAY_EXPAND_TO_REPEATED_PHYSICAL_ROWS);

    private static final FirstWaveIdentityContract FRETES =
            FirstWaveIdentityContract.create(
                    FirstWaveIdentityContract.Entity.FRETES,
                    "dataexport-6389",
                    DataExportFirstWaveContractCatalog.release(DataExportTemplate.FRETES),
                    new FirstWaveIdentityContract.SourceKeyDefinition(
                            "/id", "id", FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY),
                    FirstWaveIdentityContract.BusinessAliasDefinition.versioned(
                            "/corporation_sequence_number",
                            "corporation_sequence_number",
                            FirstWaveIdentityContract.AliasCardinalityPolicy
                                    .ZERO_TO_MANY_LOOKUP_AMBIGUITY_BLOCKS_RESOLUTION),
                    FirstWaveIdentityContract.RootCardinalityPolicy
                            .LOGICAL_ROOT_MAY_EXPAND_TO_REPEATED_PHYSICAL_ROWS);

    private static final FirstWaveIdentityContract USUARIOS =
            FirstWaveIdentityContract.create(
                    FirstWaveIdentityContract.Entity.USUARIOS,
                    "graphql-individual",
                    GraphQlFirstWaveContractCatalog.release(GraphQlReadOperation.USERS_SNAPSHOT),
                    new FirstWaveIdentityContract.SourceKeyDefinition(
                            "/node/id",
                            "user_id",
                            FirstWaveIdentityContract.WireTypePolicy
                                    .STRING_OR_INTEGER_TYPE_TAGGED_DISTINCT),
                    FirstWaveIdentityContract.BusinessAliasDefinition.absent(),
                    FirstWaveIdentityContract.RootCardinalityPolicy.ONE_NODE_PER_OBSERVED_EDGE);

    private FirstWaveIdentityCatalog() {}

    public static FirstWaveIdentityContract contract(
            final FirstWaveIdentityContract.Entity entity) {
        return switch (Objects.requireNonNull(entity, "A entidade é obrigatória.")) {
            case COLETAS -> COLETAS;
            case FRETES -> FRETES;
            case USUARIOS -> USUARIOS;
        };
    }
}
