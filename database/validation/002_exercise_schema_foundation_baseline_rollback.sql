-- Exercício transacional da transição local e do baseline estrutural V2.
-- Executar somente no alvo histórico local ETL_SISTEMA_V2_SHADOW já verificado; todo DDL é revertido.

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "001_validate_schema_foundation.sql"

ROLLBACK TRANSACTION;
PRINT N'Baseline estrutural V2 exercitado e revertido com sucesso.';
