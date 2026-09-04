-- Exercício rollback-only de V2-015b. Compara os dois caminhos autorizados sem DDL persistido:
-- baseline SQLCMD (V001-V009) e migrations aplicadas individualmente.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51341, N'O gate progressivo aceita somente o banco local V2 de sombra autorizado.', 1;
END;

-- O exercício existente cobre o baseline SQLCMD e a semântica completa do control plane.
:r "004_exercise_control_plane_baseline_rollback.sql"

-- O segundo caminho aplica cada migration ativa em ordem, valida o contrato integral e reverte tudo.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\migrations\V001__create_v2_schema_foundation.sql"
:r "..\migrations\V002__create_v2_database_roles.sql"
:r "..\migrations\V003__create_control_plane.sql"
:r "..\migrations\V004__create_staging_promotion_kernel.sql"
:r "..\migrations\V005__create_staging_lifecycle.sql"
:r "..\migrations\V006__create_observability_data_quality.sql"
:r "..\migrations\V007__create_usuarios_current_history.sql"
:r "..\migrations\V008__create_governed_references.sql"
:r "..\migrations\V009__create_usuario_dimension_current_view.sql"
:r "005_validate_progressive_data_gate.sql"
GO
:r "007_validate_staging_promotion_kernel.sql"
GO
:r "009_validate_atomic_publication_protocol.sql"
GO
:r "012_validate_staging_lifecycle.sql"
GO
:r "021_validate_observability_data_quality.sql"
GO
:r "026_validate_usuarios_current_history.sql"
GO
:r "030_validate_governed_references.sql"
GO
:r "035_validate_usuarios_dimension_current.sql"
GO

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51343, N'O escopo rollback-only do gate progressivo foi consumido ou invalidado.', 1;
ROLLBACK TRANSACTION;
IF @@TRANCOUNT <> 0
    THROW 51344, N'O rollback do gate progressivo não encerrou o escopo sintético.', 1;
PRINT N'Gate progressivo de dados V2 exercitado e revertido com sucesso.';
