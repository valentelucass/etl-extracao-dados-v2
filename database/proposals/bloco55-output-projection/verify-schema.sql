IF OBJECT_ID(N'core.localizacao_cargas') IS NULL OR NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'IX_manifesto_pick_candidate_execution') OR NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'IX_manifesto_mdfe_candidate_execution') THROW 52853,N'V023_SHAPE_REQUIRED',1;
IF EXISTS(SELECT 1 FROM sys.dm_sql_referenced_entities(N'recon.usp_capture_runtime_vertical_output',N'OBJECT') WHERE referenced_id IS NULL AND referenced_server_name IS NULL AND referenced_database_name IS NULL) THROW 52853,N'OUTPUT_DEPENDENCY_UNRESOLVED',1;
PRINT N'V023_ALL_OUTPUT_DEPENDENCIES_RESOLVE';
