-- Baseline estrutural V2 gerado a partir do manifesto de fundação.
-- Execute somente em banco V2 vazio já aprovado. Não execute este arquivo pelo Flyway:
-- o Flyway usa as migrations versionadas em ../migrations e mantém seu próprio histórico.

:r "..\migrations\V001__create_v2_schema_foundation.sql"
:r "..\migrations\V002__create_v2_database_roles.sql"
:r "..\migrations\V003__create_control_plane.sql"
:r "..\migrations\V004__create_staging_promotion_kernel.sql"
:r "..\migrations\V005__create_staging_lifecycle.sql"
:r "..\migrations\V006__create_observability_data_quality.sql"
:r "..\migrations\V007__create_usuarios_current_history.sql"
:r "..\migrations\V008__create_governed_references.sql"
:r "..\migrations\V009__create_usuario_dimension_current_view.sql"
