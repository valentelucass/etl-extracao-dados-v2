-- ADR0050 ANA-01/03: synthetic Raster capture with typed columns and immutable provenance.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_lab_run (
 run_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
 source_instance VARCHAR(40) NOT NULL,tenant_scope VARCHAR(40) NOT NULL,contract_version VARCHAR(40) NOT NULL,
 window_start DATE NOT NULL,window_end_exclusive DATE NOT NULL,zone_id VARCHAR(64) NOT NULL,
 expansion_run UNIQUEIDENTIFIER NULL REFERENCES ctl.expansion_lab_run(run_id),
 relational_run UNIQUEIDENTIFIER NULL REFERENCES ctl.relational_lab_run(run_id),
 maximum_rows INT NOT NULL,maximum_pages INT NOT NULL,created_at DATETIME2(7) NOT NULL,
 CONSTRAINT CK_analytic_run CHECK(source_instance='SYNTHETIC_ANALYTIC_LAB' AND tenant_scope='SYNTHETIC_ANALYTIC_TENANT'
 AND contract_version='synthetic-analytic-v1' AND window_start<window_end_exclusive
 AND DATEDIFF(DAY,window_start,window_end_exclusive)<=3660 AND maximum_rows BETWEEN 1 AND 100000
 AND maximum_pages BETWEEN 1 AND 10000 AND zone_id IN('America/Sao_Paulo','UTC'))
);
CREATE TABLE ctl.analytic_raster_capture (
 capture_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 start_date DATE NOT NULL,end_exclusive DATE NOT NULL,mode VARCHAR(16) NOT NULL,
 source_instance VARCHAR(40) NOT NULL,tenant_scope VARCHAR(40) NOT NULL,contract_version VARCHAR(40) NOT NULL,
 state VARCHAR(16) NOT NULL,expected_roots BIGINT NULL,expected_stops BIGINT NULL,pages INT NOT NULL DEFAULT 0,
 observed_roots BIGINT NULL,observed_stops BIGINT NULL,applied BIGINT NULL,noops BIGINT NULL,stale BIGINT NULL,
 duplicates BIGINT NULL,quarantine BIGINT NULL,unbound BIGINT NULL,receipt VARCHAR(64) NULL,extracted_at DATETIME2(7) NOT NULL,
 CONSTRAINT CK_analytic_raster_capture CHECK(start_date<end_exclusive AND mode IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 AND state IN('CAPTURING','COMPLETE','APPLIED','DEGRADED') AND source_instance='SYNTHETIC_ANALYTIC_LAB'
 AND tenant_scope='SYNTHETIC_ANALYTIC_TENANT' AND contract_version='synthetic-analytic-v1')
);
GO
CREATE PROCEDURE ctl.usp_analytic_lab_lock @run_id UNIQUEIDENTIFIER,@lane VARCHAR(16)
AS BEGIN SET NOCOUNT ON;
 IF @@TRANCOUNT=0 OR @lane NOT IN('RASTER','DIMENSION','MAT01','MAT02','MAT05','SWEEP','PLAN') THROW 53501,N'ANA_TRANSACTION_LANE',1;
 DECLARE @result INT,@resource NVARCHAR(255)=CONCAT(N'ANA_',@lane,N'_',CONVERT(NVARCHAR(36),@run_id));
 EXEC @result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1200;
 IF @result<0 THROW 53502,N'ANA_LOCK_BUSY',1;
END;
GO
CREATE TABLE stg.analytic_raster_trip (
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,capture_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_raster_capture(capture_id),
 occurrence BIGINT NOT NULL,trip_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 revision INT NULL,active BIT NULL,reactivate BIT NULL,evidence VARCHAR(64) NULL,valid BIT NOT NULL,
 comparison_bytes VARBINARY(MAX) NOT NULL,disposition VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 cod_solicitacao_p VARCHAR(6) NOT NULL,
 cod_solicitacao_w VARCHAR(8) NOT NULL,
 cod_solicitacao_raw NVARCHAR(4000) NULL,
 cod_solicitacao VARCHAR(40) NULL,
 sequencial_p VARCHAR(6) NOT NULL,
 sequencial_w VARCHAR(8) NOT NULL,
 sequencial_raw NVARCHAR(4000) NULL,
 sequencial VARCHAR(40) NULL,
 cod_filial_p VARCHAR(6) NOT NULL,
 cod_filial_w VARCHAR(8) NOT NULL,
 cod_filial_raw NVARCHAR(4000) NULL,
 cod_filial VARCHAR(40) NULL,
 status_viagem_p VARCHAR(6) NOT NULL,
 status_viagem_w VARCHAR(8) NOT NULL,
 status_viagem_raw NVARCHAR(4000) NULL,
 status_viagem NVARCHAR(1024) NULL,
 placa_veiculo_p VARCHAR(6) NOT NULL,
 placa_veiculo_w VARCHAR(8) NOT NULL,
 placa_veiculo_raw NVARCHAR(4000) NULL,
 placa_veiculo NVARCHAR(1024) NULL,
 placa_carreta1_p VARCHAR(6) NOT NULL,
 placa_carreta1_w VARCHAR(8) NOT NULL,
 placa_carreta1_raw NVARCHAR(4000) NULL,
 placa_carreta1 NVARCHAR(1024) NULL,
 placa_carreta2_p VARCHAR(6) NOT NULL,
 placa_carreta2_w VARCHAR(8) NOT NULL,
 placa_carreta2_raw NVARCHAR(4000) NULL,
 placa_carreta2 NVARCHAR(1024) NULL,
 placa_carreta3_p VARCHAR(6) NOT NULL,
 placa_carreta3_w VARCHAR(8) NOT NULL,
 placa_carreta3_raw NVARCHAR(4000) NULL,
 placa_carreta3 NVARCHAR(1024) NULL,
 cpf_motorista1_p VARCHAR(6) NOT NULL,
 cpf_motorista1_w VARCHAR(8) NOT NULL,
 cpf_motorista1_raw NVARCHAR(4000) NULL,
 cpf_motorista1 NVARCHAR(1024) NULL,
 cpf_motorista2_p VARCHAR(6) NOT NULL,
 cpf_motorista2_w VARCHAR(8) NOT NULL,
 cpf_motorista2_raw NVARCHAR(4000) NULL,
 cpf_motorista2 NVARCHAR(1024) NULL,
 cnpj_cliente_orig_p VARCHAR(6) NOT NULL,
 cnpj_cliente_orig_w VARCHAR(8) NOT NULL,
 cnpj_cliente_orig_raw NVARCHAR(4000) NULL,
 cnpj_cliente_orig NVARCHAR(1024) NULL,
 cnpj_cliente_dest_p VARCHAR(6) NOT NULL,
 cnpj_cliente_dest_w VARCHAR(8) NOT NULL,
 cnpj_cliente_dest_raw NVARCHAR(4000) NULL,
 cnpj_cliente_dest NVARCHAR(1024) NULL,
 cod_ibge_cidade_orig_p VARCHAR(6) NOT NULL,
 cod_ibge_cidade_orig_w VARCHAR(8) NOT NULL,
 cod_ibge_cidade_orig_raw NVARCHAR(4000) NULL,
 cod_ibge_cidade_orig VARCHAR(40) NULL,
 cod_ibge_cidade_dest_p VARCHAR(6) NOT NULL,
 cod_ibge_cidade_dest_w VARCHAR(8) NOT NULL,
 cod_ibge_cidade_dest_raw NVARCHAR(4000) NULL,
 cod_ibge_cidade_dest VARCHAR(40) NULL,
 data_hora_prev_ini_p VARCHAR(6) NOT NULL,
 data_hora_prev_ini_w VARCHAR(8) NOT NULL,
 data_hora_prev_ini_raw NVARCHAR(4000) NULL,
 data_hora_prev_ini BIGINT NULL,
 data_hora_prev_ini_nano INT NULL,
 data_hora_prev_ini_offset INT NULL,
 data_hora_prev_ini_sentinel BIT NULL,
 data_hora_prev_fim_p VARCHAR(6) NOT NULL,
 data_hora_prev_fim_w VARCHAR(8) NOT NULL,
 data_hora_prev_fim_raw NVARCHAR(4000) NULL,
 data_hora_prev_fim BIGINT NULL,
 data_hora_prev_fim_nano INT NULL,
 data_hora_prev_fim_offset INT NULL,
 data_hora_prev_fim_sentinel BIT NULL,
 data_hora_real_ini_p VARCHAR(6) NOT NULL,
 data_hora_real_ini_w VARCHAR(8) NOT NULL,
 data_hora_real_ini_raw NVARCHAR(4000) NULL,
 data_hora_real_ini BIGINT NULL,
 data_hora_real_ini_nano INT NULL,
 data_hora_real_ini_offset INT NULL,
 data_hora_real_ini_sentinel BIT NULL,
 data_hora_real_fim_p VARCHAR(6) NOT NULL,
 data_hora_real_fim_w VARCHAR(8) NOT NULL,
 data_hora_real_fim_raw NVARCHAR(4000) NULL,
 data_hora_real_fim BIGINT NULL,
 data_hora_real_fim_nano INT NULL,
 data_hora_real_fim_offset INT NULL,
 data_hora_real_fim_sentinel BIT NULL,
 data_hora_identificou_fim_viagem_p VARCHAR(6) NOT NULL,
 data_hora_identificou_fim_viagem_w VARCHAR(8) NOT NULL,
 data_hora_identificou_fim_viagem_raw NVARCHAR(4000) NULL,
 data_hora_identificou_fim_viagem BIGINT NULL,
 data_hora_identificou_fim_viagem_nano INT NULL,
 data_hora_identificou_fim_viagem_offset INT NULL,
 data_hora_identificou_fim_viagem_sentinel BIT NULL,
 tempo_total_viagem_p VARCHAR(6) NOT NULL,
 tempo_total_viagem_w VARCHAR(8) NOT NULL,
 tempo_total_viagem_raw NVARCHAR(4000) NULL,
 tempo_total_viagem INT NULL,
 dentro_prazo_p VARCHAR(6) NOT NULL,
 dentro_prazo_w VARCHAR(8) NOT NULL,
 dentro_prazo_raw NVARCHAR(4000) NULL,
 dentro_prazo NVARCHAR(1024) NULL,
 percentual_atraso_p VARCHAR(6) NOT NULL,
 percentual_atraso_w VARCHAR(8) NOT NULL,
 percentual_atraso_raw NVARCHAR(4000) NULL,
 percentual_atraso DECIMAL(28,8) NULL,
 rodou_fora_horario_p VARCHAR(6) NOT NULL,
 rodou_fora_horario_w VARCHAR(8) NOT NULL,
 rodou_fora_horario_raw NVARCHAR(4000) NULL,
 rodou_fora_horario NVARCHAR(1024) NULL,
 velocidade_media_p VARCHAR(6) NOT NULL,
 velocidade_media_w VARCHAR(8) NOT NULL,
 velocidade_media_raw NVARCHAR(4000) NULL,
 velocidade_media DECIMAL(28,8) NULL,
 eventos_velocidade_p VARCHAR(6) NOT NULL,
 eventos_velocidade_w VARCHAR(8) NOT NULL,
 eventos_velocidade_raw NVARCHAR(4000) NULL,
 eventos_velocidade INT NULL,
 desvios_de_rota_p VARCHAR(6) NOT NULL,
 desvios_de_rota_w VARCHAR(8) NOT NULL,
 desvios_de_rota_raw NVARCHAR(4000) NULL,
 desvios_de_rota INT NULL,
 link_timeline_p VARCHAR(6) NOT NULL,
 link_timeline_w VARCHAR(8) NOT NULL,
 link_timeline_raw NVARCHAR(4000) NULL,
 link_timeline NVARCHAR(1024) NULL,
 route_cod_rota_p VARCHAR(6) NOT NULL,
 route_cod_rota_w VARCHAR(8) NOT NULL,
 route_cod_rota_raw NVARCHAR(4000) NULL,
 route_cod_rota VARCHAR(40) NULL,
 route_descricao_p VARCHAR(6) NOT NULL,
 route_descricao_w VARCHAR(8) NOT NULL,
 route_descricao_raw NVARCHAR(4000) NULL,
 route_descricao NVARCHAR(1024) NULL,
 CONSTRAINT UQ_analytic_trip_observation UNIQUE(capture_id,occurrence),
 CONSTRAINT CK_analytic_trip_binding CHECK(occurrence>0 AND (revision IS NULL OR revision BETWEEN 1 AND 100000)
 AND(trip_key IS NULL OR (trip_key LIKE 'synthetic-%' AND DATALENGTH(trip_key)=DATALENGTH(RTRIM(trip_key))))
 ),
 CONSTRAINT CK_analytic_trip_fields CHECK((cod_solicitacao_p IN('ABSENT','NULL','VALUE') AND cod_solicitacao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (sequencial_p IN('ABSENT','NULL','VALUE') AND sequencial_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cod_filial_p IN('ABSENT','NULL','VALUE') AND cod_filial_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (status_viagem_p IN('ABSENT','NULL','VALUE') AND status_viagem_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (placa_veiculo_p IN('ABSENT','NULL','VALUE') AND placa_veiculo_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (placa_carreta1_p IN('ABSENT','NULL','VALUE') AND placa_carreta1_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (placa_carreta2_p IN('ABSENT','NULL','VALUE') AND placa_carreta2_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (placa_carreta3_p IN('ABSENT','NULL','VALUE') AND placa_carreta3_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cpf_motorista1_p IN('ABSENT','NULL','VALUE') AND cpf_motorista1_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cpf_motorista2_p IN('ABSENT','NULL','VALUE') AND cpf_motorista2_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cnpj_cliente_orig_p IN('ABSENT','NULL','VALUE') AND cnpj_cliente_orig_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cnpj_cliente_dest_p IN('ABSENT','NULL','VALUE') AND cnpj_cliente_dest_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cod_ibge_cidade_orig_p IN('ABSENT','NULL','VALUE') AND cod_ibge_cidade_orig_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cod_ibge_cidade_dest_p IN('ABSENT','NULL','VALUE') AND cod_ibge_cidade_dest_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_prev_ini_p IN('ABSENT','NULL','VALUE') AND data_hora_prev_ini_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_prev_fim_p IN('ABSENT','NULL','VALUE') AND data_hora_prev_fim_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_real_ini_p IN('ABSENT','NULL','VALUE') AND data_hora_real_ini_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_real_fim_p IN('ABSENT','NULL','VALUE') AND data_hora_real_fim_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_identificou_fim_viagem_p IN('ABSENT','NULL','VALUE') AND data_hora_identificou_fim_viagem_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (tempo_total_viagem_p IN('ABSENT','NULL','VALUE') AND tempo_total_viagem_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (dentro_prazo_p IN('ABSENT','NULL','VALUE') AND dentro_prazo_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (percentual_atraso_p IN('ABSENT','NULL','VALUE') AND percentual_atraso_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (rodou_fora_horario_p IN('ABSENT','NULL','VALUE') AND rodou_fora_horario_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (velocidade_media_p IN('ABSENT','NULL','VALUE') AND velocidade_media_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (eventos_velocidade_p IN('ABSENT','NULL','VALUE') AND eventos_velocidade_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (desvios_de_rota_p IN('ABSENT','NULL','VALUE') AND desvios_de_rota_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (link_timeline_p IN('ABSENT','NULL','VALUE') AND link_timeline_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (route_cod_rota_p IN('ABSENT','NULL','VALUE') AND route_cod_rota_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (route_descricao_p IN('ABSENT','NULL','VALUE') AND route_descricao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')))
);
CREATE INDEX IX_analytic_trip_capture ON stg.analytic_raster_trip(capture_id,trip_key,revision DESC);
CREATE TABLE core.analytic_raster_trip (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 trip_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 revision INT NOT NULL,active BIT NOT NULL,observation_id BIGINT NOT NULL REFERENCES stg.analytic_raster_trip(observation_id),
 comparison_bytes VARBINARY(MAX) NOT NULL,extracted_at DATETIME2(7) NOT NULL,
 cod_solicitacao_p VARCHAR(6) NOT NULL,
 cod_solicitacao_w VARCHAR(8) NOT NULL,
 cod_solicitacao_raw NVARCHAR(4000) NULL,
 cod_solicitacao VARCHAR(40) NULL,
 sequencial_p VARCHAR(6) NOT NULL,
 sequencial_w VARCHAR(8) NOT NULL,
 sequencial_raw NVARCHAR(4000) NULL,
 sequencial VARCHAR(40) NULL,
 cod_filial_p VARCHAR(6) NOT NULL,
 cod_filial_w VARCHAR(8) NOT NULL,
 cod_filial_raw NVARCHAR(4000) NULL,
 cod_filial VARCHAR(40) NULL,
 status_viagem_p VARCHAR(6) NOT NULL,
 status_viagem_w VARCHAR(8) NOT NULL,
 status_viagem_raw NVARCHAR(4000) NULL,
 status_viagem NVARCHAR(1024) NULL,
 placa_veiculo_p VARCHAR(6) NOT NULL,
 placa_veiculo_w VARCHAR(8) NOT NULL,
 placa_veiculo_raw NVARCHAR(4000) NULL,
 placa_veiculo NVARCHAR(1024) NULL,
 placa_carreta1_p VARCHAR(6) NOT NULL,
 placa_carreta1_w VARCHAR(8) NOT NULL,
 placa_carreta1_raw NVARCHAR(4000) NULL,
 placa_carreta1 NVARCHAR(1024) NULL,
 placa_carreta2_p VARCHAR(6) NOT NULL,
 placa_carreta2_w VARCHAR(8) NOT NULL,
 placa_carreta2_raw NVARCHAR(4000) NULL,
 placa_carreta2 NVARCHAR(1024) NULL,
 placa_carreta3_p VARCHAR(6) NOT NULL,
 placa_carreta3_w VARCHAR(8) NOT NULL,
 placa_carreta3_raw NVARCHAR(4000) NULL,
 placa_carreta3 NVARCHAR(1024) NULL,
 cpf_motorista1_p VARCHAR(6) NOT NULL,
 cpf_motorista1_w VARCHAR(8) NOT NULL,
 cpf_motorista1_raw NVARCHAR(4000) NULL,
 cpf_motorista1 NVARCHAR(1024) NULL,
 cpf_motorista2_p VARCHAR(6) NOT NULL,
 cpf_motorista2_w VARCHAR(8) NOT NULL,
 cpf_motorista2_raw NVARCHAR(4000) NULL,
 cpf_motorista2 NVARCHAR(1024) NULL,
 cnpj_cliente_orig_p VARCHAR(6) NOT NULL,
 cnpj_cliente_orig_w VARCHAR(8) NOT NULL,
 cnpj_cliente_orig_raw NVARCHAR(4000) NULL,
 cnpj_cliente_orig NVARCHAR(1024) NULL,
 cnpj_cliente_dest_p VARCHAR(6) NOT NULL,
 cnpj_cliente_dest_w VARCHAR(8) NOT NULL,
 cnpj_cliente_dest_raw NVARCHAR(4000) NULL,
 cnpj_cliente_dest NVARCHAR(1024) NULL,
 cod_ibge_cidade_orig_p VARCHAR(6) NOT NULL,
 cod_ibge_cidade_orig_w VARCHAR(8) NOT NULL,
 cod_ibge_cidade_orig_raw NVARCHAR(4000) NULL,
 cod_ibge_cidade_orig VARCHAR(40) NULL,
 cod_ibge_cidade_dest_p VARCHAR(6) NOT NULL,
 cod_ibge_cidade_dest_w VARCHAR(8) NOT NULL,
 cod_ibge_cidade_dest_raw NVARCHAR(4000) NULL,
 cod_ibge_cidade_dest VARCHAR(40) NULL,
 data_hora_prev_ini_p VARCHAR(6) NOT NULL,
 data_hora_prev_ini_w VARCHAR(8) NOT NULL,
 data_hora_prev_ini_raw NVARCHAR(4000) NULL,
 data_hora_prev_ini BIGINT NULL,
 data_hora_prev_ini_nano INT NULL,
 data_hora_prev_ini_offset INT NULL,
 data_hora_prev_ini_sentinel BIT NULL,
 data_hora_prev_fim_p VARCHAR(6) NOT NULL,
 data_hora_prev_fim_w VARCHAR(8) NOT NULL,
 data_hora_prev_fim_raw NVARCHAR(4000) NULL,
 data_hora_prev_fim BIGINT NULL,
 data_hora_prev_fim_nano INT NULL,
 data_hora_prev_fim_offset INT NULL,
 data_hora_prev_fim_sentinel BIT NULL,
 data_hora_real_ini_p VARCHAR(6) NOT NULL,
 data_hora_real_ini_w VARCHAR(8) NOT NULL,
 data_hora_real_ini_raw NVARCHAR(4000) NULL,
 data_hora_real_ini BIGINT NULL,
 data_hora_real_ini_nano INT NULL,
 data_hora_real_ini_offset INT NULL,
 data_hora_real_ini_sentinel BIT NULL,
 data_hora_real_fim_p VARCHAR(6) NOT NULL,
 data_hora_real_fim_w VARCHAR(8) NOT NULL,
 data_hora_real_fim_raw NVARCHAR(4000) NULL,
 data_hora_real_fim BIGINT NULL,
 data_hora_real_fim_nano INT NULL,
 data_hora_real_fim_offset INT NULL,
 data_hora_real_fim_sentinel BIT NULL,
 data_hora_identificou_fim_viagem_p VARCHAR(6) NOT NULL,
 data_hora_identificou_fim_viagem_w VARCHAR(8) NOT NULL,
 data_hora_identificou_fim_viagem_raw NVARCHAR(4000) NULL,
 data_hora_identificou_fim_viagem BIGINT NULL,
 data_hora_identificou_fim_viagem_nano INT NULL,
 data_hora_identificou_fim_viagem_offset INT NULL,
 data_hora_identificou_fim_viagem_sentinel BIT NULL,
 tempo_total_viagem_p VARCHAR(6) NOT NULL,
 tempo_total_viagem_w VARCHAR(8) NOT NULL,
 tempo_total_viagem_raw NVARCHAR(4000) NULL,
 tempo_total_viagem INT NULL,
 dentro_prazo_p VARCHAR(6) NOT NULL,
 dentro_prazo_w VARCHAR(8) NOT NULL,
 dentro_prazo_raw NVARCHAR(4000) NULL,
 dentro_prazo NVARCHAR(1024) NULL,
 percentual_atraso_p VARCHAR(6) NOT NULL,
 percentual_atraso_w VARCHAR(8) NOT NULL,
 percentual_atraso_raw NVARCHAR(4000) NULL,
 percentual_atraso DECIMAL(28,8) NULL,
 rodou_fora_horario_p VARCHAR(6) NOT NULL,
 rodou_fora_horario_w VARCHAR(8) NOT NULL,
 rodou_fora_horario_raw NVARCHAR(4000) NULL,
 rodou_fora_horario NVARCHAR(1024) NULL,
 velocidade_media_p VARCHAR(6) NOT NULL,
 velocidade_media_w VARCHAR(8) NOT NULL,
 velocidade_media_raw NVARCHAR(4000) NULL,
 velocidade_media DECIMAL(28,8) NULL,
 eventos_velocidade_p VARCHAR(6) NOT NULL,
 eventos_velocidade_w VARCHAR(8) NOT NULL,
 eventos_velocidade_raw NVARCHAR(4000) NULL,
 eventos_velocidade INT NULL,
 desvios_de_rota_p VARCHAR(6) NOT NULL,
 desvios_de_rota_w VARCHAR(8) NOT NULL,
 desvios_de_rota_raw NVARCHAR(4000) NULL,
 desvios_de_rota INT NULL,
 link_timeline_p VARCHAR(6) NOT NULL,
 link_timeline_w VARCHAR(8) NOT NULL,
 link_timeline_raw NVARCHAR(4000) NULL,
 link_timeline NVARCHAR(1024) NULL,
 route_cod_rota_p VARCHAR(6) NOT NULL,
 route_cod_rota_w VARCHAR(8) NOT NULL,
 route_cod_rota_raw NVARCHAR(4000) NULL,
 route_cod_rota VARCHAR(40) NULL,
 route_descricao_p VARCHAR(6) NOT NULL,
 route_descricao_w VARCHAR(8) NOT NULL,
 route_descricao_raw NVARCHAR(4000) NULL,
 route_descricao NVARCHAR(1024) NULL,
 CONSTRAINT PK_analytic_trip_current PRIMARY KEY(run_id,trip_key)
);
CREATE TABLE core.analytic_raster_trip_history (
 history_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 trip_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 prior_observation BIGINT NULL,observation_id BIGINT NOT NULL REFERENCES stg.analytic_raster_trip(observation_id),
 action VARCHAR(16) NOT NULL,recorded_at DATETIME2(7) NOT NULL
);
GO
CREATE TRIGGER stg.trg_analytic_raster_trip_immutable ON stg.analytic_raster_trip AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF (EXISTS(SELECT 1 FROM deleted) AND (UPDATE(observation_id) OR UPDATE(capture_id) OR UPDATE(occurrence) OR UPDATE(trip_key) OR UPDATE(revision) OR UPDATE(active) OR UPDATE(reactivate) OR UPDATE(evidence) OR UPDATE(valid) OR UPDATE(comparison_bytes) OR UPDATE(cod_solicitacao_p) OR UPDATE(cod_solicitacao_w) OR UPDATE(cod_solicitacao_raw) OR UPDATE(cod_solicitacao) OR UPDATE(sequencial_p) OR UPDATE(sequencial_w) OR UPDATE(sequencial_raw) OR UPDATE(sequencial) OR UPDATE(cod_filial_p) OR UPDATE(cod_filial_w) OR UPDATE(cod_filial_raw) OR UPDATE(cod_filial) OR UPDATE(status_viagem_p) OR UPDATE(status_viagem_w) OR UPDATE(status_viagem_raw) OR UPDATE(status_viagem) OR UPDATE(placa_veiculo_p) OR UPDATE(placa_veiculo_w) OR UPDATE(placa_veiculo_raw) OR UPDATE(placa_veiculo) OR UPDATE(placa_carreta1_p) OR UPDATE(placa_carreta1_w) OR UPDATE(placa_carreta1_raw) OR UPDATE(placa_carreta1) OR UPDATE(placa_carreta2_p) OR UPDATE(placa_carreta2_w) OR UPDATE(placa_carreta2_raw) OR UPDATE(placa_carreta2) OR UPDATE(placa_carreta3_p) OR UPDATE(placa_carreta3_w) OR UPDATE(placa_carreta3_raw) OR UPDATE(placa_carreta3) OR UPDATE(cpf_motorista1_p) OR UPDATE(cpf_motorista1_w) OR UPDATE(cpf_motorista1_raw) OR UPDATE(cpf_motorista1) OR UPDATE(cpf_motorista2_p) OR UPDATE(cpf_motorista2_w) OR UPDATE(cpf_motorista2_raw) OR UPDATE(cpf_motorista2) OR UPDATE(cnpj_cliente_orig_p) OR UPDATE(cnpj_cliente_orig_w) OR UPDATE(cnpj_cliente_orig_raw) OR UPDATE(cnpj_cliente_orig) OR UPDATE(cnpj_cliente_dest_p) OR UPDATE(cnpj_cliente_dest_w) OR UPDATE(cnpj_cliente_dest_raw) OR UPDATE(cnpj_cliente_dest) OR UPDATE(cod_ibge_cidade_orig_p) OR UPDATE(cod_ibge_cidade_orig_w) OR UPDATE(cod_ibge_cidade_orig_raw) OR UPDATE(cod_ibge_cidade_orig) OR UPDATE(cod_ibge_cidade_dest_p) OR UPDATE(cod_ibge_cidade_dest_w) OR UPDATE(cod_ibge_cidade_dest_raw) OR UPDATE(cod_ibge_cidade_dest) OR UPDATE(data_hora_prev_ini_p) OR UPDATE(data_hora_prev_ini_w) OR UPDATE(data_hora_prev_ini_raw) OR UPDATE(data_hora_prev_ini) OR UPDATE(data_hora_prev_ini_nano) OR UPDATE(data_hora_prev_ini_offset) OR UPDATE(data_hora_prev_ini_sentinel) OR UPDATE(data_hora_prev_fim_p) OR UPDATE(data_hora_prev_fim_w) OR UPDATE(data_hora_prev_fim_raw) OR UPDATE(data_hora_prev_fim) OR UPDATE(data_hora_prev_fim_nano) OR UPDATE(data_hora_prev_fim_offset) OR UPDATE(data_hora_prev_fim_sentinel) OR UPDATE(data_hora_real_ini_p) OR UPDATE(data_hora_real_ini_w) OR UPDATE(data_hora_real_ini_raw) OR UPDATE(data_hora_real_ini) OR UPDATE(data_hora_real_ini_nano) OR UPDATE(data_hora_real_ini_offset) OR UPDATE(data_hora_real_ini_sentinel) OR UPDATE(data_hora_real_fim_p) OR UPDATE(data_hora_real_fim_w) OR UPDATE(data_hora_real_fim_raw) OR UPDATE(data_hora_real_fim) OR UPDATE(data_hora_real_fim_nano) OR UPDATE(data_hora_real_fim_offset) OR UPDATE(data_hora_real_fim_sentinel) OR UPDATE(data_hora_identificou_fim_viagem_p) OR UPDATE(data_hora_identificou_fim_viagem_w) OR UPDATE(data_hora_identificou_fim_viagem_raw) OR UPDATE(data_hora_identificou_fim_viagem) OR UPDATE(data_hora_identificou_fim_viagem_nano) OR UPDATE(data_hora_identificou_fim_viagem_offset) OR UPDATE(data_hora_identificou_fim_viagem_sentinel) OR UPDATE(tempo_total_viagem_p) OR UPDATE(tempo_total_viagem_w) OR UPDATE(tempo_total_viagem_raw) OR UPDATE(tempo_total_viagem) OR UPDATE(dentro_prazo_p) OR UPDATE(dentro_prazo_w) OR UPDATE(dentro_prazo_raw) OR UPDATE(dentro_prazo) OR UPDATE(percentual_atraso_p) OR UPDATE(percentual_atraso_w) OR UPDATE(percentual_atraso_raw) OR UPDATE(percentual_atraso) OR UPDATE(rodou_fora_horario_p) OR UPDATE(rodou_fora_horario_w) OR UPDATE(rodou_fora_horario_raw) OR UPDATE(rodou_fora_horario) OR UPDATE(velocidade_media_p) OR UPDATE(velocidade_media_w) OR UPDATE(velocidade_media_raw) OR UPDATE(velocidade_media) OR UPDATE(eventos_velocidade_p) OR UPDATE(eventos_velocidade_w) OR UPDATE(eventos_velocidade_raw) OR UPDATE(eventos_velocidade) OR UPDATE(desvios_de_rota_p) OR UPDATE(desvios_de_rota_w) OR UPDATE(desvios_de_rota_raw) OR UPDATE(desvios_de_rota) OR UPDATE(link_timeline_p) OR UPDATE(link_timeline_w) OR UPDATE(link_timeline_raw) OR UPDATE(link_timeline) OR UPDATE(route_cod_rota_p) OR UPDATE(route_cod_rota_w) OR UPDATE(route_cod_rota_raw) OR UPDATE(route_cod_rota) OR UPDATE(route_descricao_p) OR UPDATE(route_descricao_w) OR UPDATE(route_descricao_raw) OR UPDATE(route_descricao)))
 OR EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.observation_id=d.observation_id WHERE i.observation_id IS NULL)
 THROW 53503,N'ANA_OBSERVATION_IMMUTABLE',1;
 IF NOT EXISTS(SELECT 1 FROM deleted) AND EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_raster_capture c ON c.capture_id=i.capture_id WHERE c.state<>'CAPTURING')
 THROW 53504,N'ANA_CAPTURE_SEALED',1;
END;
GO
CREATE TABLE stg.analytic_raster_stop (
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,capture_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_raster_capture(capture_id),
 occurrence BIGINT NOT NULL,trip_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,stop_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 revision INT NULL,active BIT NULL,reactivate BIT NULL,evidence VARCHAR(64) NULL,valid BIT NOT NULL,
 comparison_bytes VARBINARY(MAX) NOT NULL,disposition VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 ordem_p VARCHAR(6) NOT NULL,
 ordem_w VARCHAR(8) NOT NULL,
 ordem_raw NVARCHAR(4000) NULL,
 ordem VARCHAR(40) NULL,
 tipo_p VARCHAR(6) NOT NULL,
 tipo_w VARCHAR(8) NOT NULL,
 tipo_raw NVARCHAR(4000) NULL,
 tipo NVARCHAR(1024) NULL,
 cod_ibge_cidade_p VARCHAR(6) NOT NULL,
 cod_ibge_cidade_w VARCHAR(8) NOT NULL,
 cod_ibge_cidade_raw NVARCHAR(4000) NULL,
 cod_ibge_cidade VARCHAR(40) NULL,
 cnpj_cliente_p VARCHAR(6) NOT NULL,
 cnpj_cliente_w VARCHAR(8) NOT NULL,
 cnpj_cliente_raw NVARCHAR(4000) NULL,
 cnpj_cliente NVARCHAR(1024) NULL,
 codigo_cliente_p VARCHAR(6) NOT NULL,
 codigo_cliente_w VARCHAR(8) NOT NULL,
 codigo_cliente_raw NVARCHAR(4000) NULL,
 codigo_cliente NVARCHAR(1024) NULL,
 data_hora_prev_chegada_p VARCHAR(6) NOT NULL,
 data_hora_prev_chegada_w VARCHAR(8) NOT NULL,
 data_hora_prev_chegada_raw NVARCHAR(4000) NULL,
 data_hora_prev_chegada BIGINT NULL,
 data_hora_prev_chegada_nano INT NULL,
 data_hora_prev_chegada_offset INT NULL,
 data_hora_prev_chegada_sentinel BIT NULL,
 data_hora_prev_saida_p VARCHAR(6) NOT NULL,
 data_hora_prev_saida_w VARCHAR(8) NOT NULL,
 data_hora_prev_saida_raw NVARCHAR(4000) NULL,
 data_hora_prev_saida BIGINT NULL,
 data_hora_prev_saida_nano INT NULL,
 data_hora_prev_saida_offset INT NULL,
 data_hora_prev_saida_sentinel BIT NULL,
 data_hora_real_chegada_p VARCHAR(6) NOT NULL,
 data_hora_real_chegada_w VARCHAR(8) NOT NULL,
 data_hora_real_chegada_raw NVARCHAR(4000) NULL,
 data_hora_real_chegada BIGINT NULL,
 data_hora_real_chegada_nano INT NULL,
 data_hora_real_chegada_offset INT NULL,
 data_hora_real_chegada_sentinel BIT NULL,
 data_hora_real_saida_p VARCHAR(6) NOT NULL,
 data_hora_real_saida_w VARCHAR(8) NOT NULL,
 data_hora_real_saida_raw NVARCHAR(4000) NULL,
 data_hora_real_saida BIGINT NULL,
 data_hora_real_saida_nano INT NULL,
 data_hora_real_saida_offset INT NULL,
 data_hora_real_saida_sentinel BIT NULL,
 latitude_p VARCHAR(6) NOT NULL,
 latitude_w VARCHAR(8) NOT NULL,
 latitude_raw NVARCHAR(4000) NULL,
 latitude DECIMAL(28,8) NULL,
 longitude_p VARCHAR(6) NOT NULL,
 longitude_w VARCHAR(8) NOT NULL,
 longitude_raw NVARCHAR(4000) NULL,
 longitude DECIMAL(28,8) NULL,
 dentro_prazo_p VARCHAR(6) NOT NULL,
 dentro_prazo_w VARCHAR(8) NOT NULL,
 dentro_prazo_raw NVARCHAR(4000) NULL,
 dentro_prazo NVARCHAR(1024) NULL,
 diferenca_tempo_p VARCHAR(6) NOT NULL,
 diferenca_tempo_w VARCHAR(8) NOT NULL,
 diferenca_tempo_raw NVARCHAR(4000) NULL,
 diferenca_tempo NVARCHAR(1024) NULL,
 km_percorrido_entrega_p VARCHAR(6) NOT NULL,
 km_percorrido_entrega_w VARCHAR(8) NOT NULL,
 km_percorrido_entrega_raw NVARCHAR(4000) NULL,
 km_percorrido_entrega DECIMAL(28,8) NULL,
 km_restante_entrega_p VARCHAR(6) NOT NULL,
 km_restante_entrega_w VARCHAR(8) NOT NULL,
 km_restante_entrega_raw NVARCHAR(4000) NULL,
 km_restante_entrega DECIMAL(28,8) NULL,
 chegou_na_entrega_p VARCHAR(6) NOT NULL,
 chegou_na_entrega_w VARCHAR(8) NOT NULL,
 chegou_na_entrega_raw NVARCHAR(4000) NULL,
 chegou_na_entrega NVARCHAR(1024) NULL,
 data_hora_ultima_posicao_p VARCHAR(6) NOT NULL,
 data_hora_ultima_posicao_w VARCHAR(8) NOT NULL,
 data_hora_ultima_posicao_raw NVARCHAR(4000) NULL,
 data_hora_ultima_posicao BIGINT NULL,
 data_hora_ultima_posicao_nano INT NULL,
 data_hora_ultima_posicao_offset INT NULL,
 data_hora_ultima_posicao_sentinel BIT NULL,
 latitude_ultima_posicao_p VARCHAR(6) NOT NULL,
 latitude_ultima_posicao_w VARCHAR(8) NOT NULL,
 latitude_ultima_posicao_raw NVARCHAR(4000) NULL,
 latitude_ultima_posicao DECIMAL(28,8) NULL,
 longitude_ultima_posicao_p VARCHAR(6) NOT NULL,
 longitude_ultima_posicao_w VARCHAR(8) NOT NULL,
 longitude_ultima_posicao_raw NVARCHAR(4000) NULL,
 longitude_ultima_posicao DECIMAL(28,8) NULL,
 referencia_ultima_posicao_p VARCHAR(6) NOT NULL,
 referencia_ultima_posicao_w VARCHAR(8) NOT NULL,
 referencia_ultima_posicao_raw NVARCHAR(4000) NULL,
 referencia_ultima_posicao NVARCHAR(1024) NULL,
 CONSTRAINT UQ_analytic_stop_observation UNIQUE(capture_id,occurrence),
 CONSTRAINT CK_analytic_stop_binding CHECK(occurrence>0 AND (revision IS NULL OR revision BETWEEN 1 AND 100000)
 AND(trip_key IS NULL OR (trip_key LIKE 'synthetic-%' AND DATALENGTH(trip_key)=DATALENGTH(RTRIM(trip_key))))
 AND(stop_key IS NULL OR(stop_key LIKE 'synthetic-%' AND DATALENGTH(stop_key)=DATALENGTH(RTRIM(stop_key))))),
 CONSTRAINT CK_analytic_stop_fields CHECK((ordem_p IN('ABSENT','NULL','VALUE') AND ordem_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (tipo_p IN('ABSENT','NULL','VALUE') AND tipo_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cod_ibge_cidade_p IN('ABSENT','NULL','VALUE') AND cod_ibge_cidade_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (cnpj_cliente_p IN('ABSENT','NULL','VALUE') AND cnpj_cliente_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (codigo_cliente_p IN('ABSENT','NULL','VALUE') AND codigo_cliente_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_prev_chegada_p IN('ABSENT','NULL','VALUE') AND data_hora_prev_chegada_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_prev_saida_p IN('ABSENT','NULL','VALUE') AND data_hora_prev_saida_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_real_chegada_p IN('ABSENT','NULL','VALUE') AND data_hora_real_chegada_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_real_saida_p IN('ABSENT','NULL','VALUE') AND data_hora_real_saida_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (latitude_p IN('ABSENT','NULL','VALUE') AND latitude_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (longitude_p IN('ABSENT','NULL','VALUE') AND longitude_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (dentro_prazo_p IN('ABSENT','NULL','VALUE') AND dentro_prazo_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (diferenca_tempo_p IN('ABSENT','NULL','VALUE') AND diferenca_tempo_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (km_percorrido_entrega_p IN('ABSENT','NULL','VALUE') AND km_percorrido_entrega_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (km_restante_entrega_p IN('ABSENT','NULL','VALUE') AND km_restante_entrega_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (chegou_na_entrega_p IN('ABSENT','NULL','VALUE') AND chegou_na_entrega_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (data_hora_ultima_posicao_p IN('ABSENT','NULL','VALUE') AND data_hora_ultima_posicao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (latitude_ultima_posicao_p IN('ABSENT','NULL','VALUE') AND latitude_ultima_posicao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (longitude_ultima_posicao_p IN('ABSENT','NULL','VALUE') AND longitude_ultima_posicao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT'))
 AND (referencia_ultima_posicao_p IN('ABSENT','NULL','VALUE') AND referencia_ultima_posicao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')))
);
CREATE INDEX IX_analytic_stop_capture ON stg.analytic_raster_stop(capture_id,trip_key,stop_key,revision DESC);
CREATE TABLE core.analytic_raster_stop (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 trip_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,stop_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 revision INT NOT NULL,active BIT NOT NULL,observation_id BIGINT NOT NULL REFERENCES stg.analytic_raster_stop(observation_id),
 comparison_bytes VARBINARY(MAX) NOT NULL,extracted_at DATETIME2(7) NOT NULL,
 ordem_p VARCHAR(6) NOT NULL,
 ordem_w VARCHAR(8) NOT NULL,
 ordem_raw NVARCHAR(4000) NULL,
 ordem VARCHAR(40) NULL,
 tipo_p VARCHAR(6) NOT NULL,
 tipo_w VARCHAR(8) NOT NULL,
 tipo_raw NVARCHAR(4000) NULL,
 tipo NVARCHAR(1024) NULL,
 cod_ibge_cidade_p VARCHAR(6) NOT NULL,
 cod_ibge_cidade_w VARCHAR(8) NOT NULL,
 cod_ibge_cidade_raw NVARCHAR(4000) NULL,
 cod_ibge_cidade VARCHAR(40) NULL,
 cnpj_cliente_p VARCHAR(6) NOT NULL,
 cnpj_cliente_w VARCHAR(8) NOT NULL,
 cnpj_cliente_raw NVARCHAR(4000) NULL,
 cnpj_cliente NVARCHAR(1024) NULL,
 codigo_cliente_p VARCHAR(6) NOT NULL,
 codigo_cliente_w VARCHAR(8) NOT NULL,
 codigo_cliente_raw NVARCHAR(4000) NULL,
 codigo_cliente NVARCHAR(1024) NULL,
 data_hora_prev_chegada_p VARCHAR(6) NOT NULL,
 data_hora_prev_chegada_w VARCHAR(8) NOT NULL,
 data_hora_prev_chegada_raw NVARCHAR(4000) NULL,
 data_hora_prev_chegada BIGINT NULL,
 data_hora_prev_chegada_nano INT NULL,
 data_hora_prev_chegada_offset INT NULL,
 data_hora_prev_chegada_sentinel BIT NULL,
 data_hora_prev_saida_p VARCHAR(6) NOT NULL,
 data_hora_prev_saida_w VARCHAR(8) NOT NULL,
 data_hora_prev_saida_raw NVARCHAR(4000) NULL,
 data_hora_prev_saida BIGINT NULL,
 data_hora_prev_saida_nano INT NULL,
 data_hora_prev_saida_offset INT NULL,
 data_hora_prev_saida_sentinel BIT NULL,
 data_hora_real_chegada_p VARCHAR(6) NOT NULL,
 data_hora_real_chegada_w VARCHAR(8) NOT NULL,
 data_hora_real_chegada_raw NVARCHAR(4000) NULL,
 data_hora_real_chegada BIGINT NULL,
 data_hora_real_chegada_nano INT NULL,
 data_hora_real_chegada_offset INT NULL,
 data_hora_real_chegada_sentinel BIT NULL,
 data_hora_real_saida_p VARCHAR(6) NOT NULL,
 data_hora_real_saida_w VARCHAR(8) NOT NULL,
 data_hora_real_saida_raw NVARCHAR(4000) NULL,
 data_hora_real_saida BIGINT NULL,
 data_hora_real_saida_nano INT NULL,
 data_hora_real_saida_offset INT NULL,
 data_hora_real_saida_sentinel BIT NULL,
 latitude_p VARCHAR(6) NOT NULL,
 latitude_w VARCHAR(8) NOT NULL,
 latitude_raw NVARCHAR(4000) NULL,
 latitude DECIMAL(28,8) NULL,
 longitude_p VARCHAR(6) NOT NULL,
 longitude_w VARCHAR(8) NOT NULL,
 longitude_raw NVARCHAR(4000) NULL,
 longitude DECIMAL(28,8) NULL,
 dentro_prazo_p VARCHAR(6) NOT NULL,
 dentro_prazo_w VARCHAR(8) NOT NULL,
 dentro_prazo_raw NVARCHAR(4000) NULL,
 dentro_prazo NVARCHAR(1024) NULL,
 diferenca_tempo_p VARCHAR(6) NOT NULL,
 diferenca_tempo_w VARCHAR(8) NOT NULL,
 diferenca_tempo_raw NVARCHAR(4000) NULL,
 diferenca_tempo NVARCHAR(1024) NULL,
 km_percorrido_entrega_p VARCHAR(6) NOT NULL,
 km_percorrido_entrega_w VARCHAR(8) NOT NULL,
 km_percorrido_entrega_raw NVARCHAR(4000) NULL,
 km_percorrido_entrega DECIMAL(28,8) NULL,
 km_restante_entrega_p VARCHAR(6) NOT NULL,
 km_restante_entrega_w VARCHAR(8) NOT NULL,
 km_restante_entrega_raw NVARCHAR(4000) NULL,
 km_restante_entrega DECIMAL(28,8) NULL,
 chegou_na_entrega_p VARCHAR(6) NOT NULL,
 chegou_na_entrega_w VARCHAR(8) NOT NULL,
 chegou_na_entrega_raw NVARCHAR(4000) NULL,
 chegou_na_entrega NVARCHAR(1024) NULL,
 data_hora_ultima_posicao_p VARCHAR(6) NOT NULL,
 data_hora_ultima_posicao_w VARCHAR(8) NOT NULL,
 data_hora_ultima_posicao_raw NVARCHAR(4000) NULL,
 data_hora_ultima_posicao BIGINT NULL,
 data_hora_ultima_posicao_nano INT NULL,
 data_hora_ultima_posicao_offset INT NULL,
 data_hora_ultima_posicao_sentinel BIT NULL,
 latitude_ultima_posicao_p VARCHAR(6) NOT NULL,
 latitude_ultima_posicao_w VARCHAR(8) NOT NULL,
 latitude_ultima_posicao_raw NVARCHAR(4000) NULL,
 latitude_ultima_posicao DECIMAL(28,8) NULL,
 longitude_ultima_posicao_p VARCHAR(6) NOT NULL,
 longitude_ultima_posicao_w VARCHAR(8) NOT NULL,
 longitude_ultima_posicao_raw NVARCHAR(4000) NULL,
 longitude_ultima_posicao DECIMAL(28,8) NULL,
 referencia_ultima_posicao_p VARCHAR(6) NOT NULL,
 referencia_ultima_posicao_w VARCHAR(8) NOT NULL,
 referencia_ultima_posicao_raw NVARCHAR(4000) NULL,
 referencia_ultima_posicao NVARCHAR(1024) NULL,
 CONSTRAINT PK_analytic_stop_current PRIMARY KEY(run_id,trip_key,stop_key),CONSTRAINT FK_analytic_stop_parent FOREIGN KEY(run_id,trip_key) REFERENCES core.analytic_raster_trip(run_id,trip_key)
);
CREATE TABLE core.analytic_raster_stop_history (
 history_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 trip_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,stop_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 prior_observation BIGINT NULL,observation_id BIGINT NOT NULL REFERENCES stg.analytic_raster_stop(observation_id),
 action VARCHAR(16) NOT NULL,recorded_at DATETIME2(7) NOT NULL
);
GO
CREATE TRIGGER stg.trg_analytic_raster_stop_immutable ON stg.analytic_raster_stop AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF (EXISTS(SELECT 1 FROM deleted) AND (UPDATE(observation_id) OR UPDATE(capture_id) OR UPDATE(occurrence) OR UPDATE(trip_key) OR UPDATE(stop_key) OR UPDATE(revision) OR UPDATE(active) OR UPDATE(reactivate) OR UPDATE(evidence) OR UPDATE(valid) OR UPDATE(comparison_bytes) OR UPDATE(ordem_p) OR UPDATE(ordem_w) OR UPDATE(ordem_raw) OR UPDATE(ordem) OR UPDATE(tipo_p) OR UPDATE(tipo_w) OR UPDATE(tipo_raw) OR UPDATE(tipo) OR UPDATE(cod_ibge_cidade_p) OR UPDATE(cod_ibge_cidade_w) OR UPDATE(cod_ibge_cidade_raw) OR UPDATE(cod_ibge_cidade) OR UPDATE(cnpj_cliente_p) OR UPDATE(cnpj_cliente_w) OR UPDATE(cnpj_cliente_raw) OR UPDATE(cnpj_cliente) OR UPDATE(codigo_cliente_p) OR UPDATE(codigo_cliente_w) OR UPDATE(codigo_cliente_raw) OR UPDATE(codigo_cliente) OR UPDATE(data_hora_prev_chegada_p) OR UPDATE(data_hora_prev_chegada_w) OR UPDATE(data_hora_prev_chegada_raw) OR UPDATE(data_hora_prev_chegada) OR UPDATE(data_hora_prev_chegada_nano) OR UPDATE(data_hora_prev_chegada_offset) OR UPDATE(data_hora_prev_chegada_sentinel) OR UPDATE(data_hora_prev_saida_p) OR UPDATE(data_hora_prev_saida_w) OR UPDATE(data_hora_prev_saida_raw) OR UPDATE(data_hora_prev_saida) OR UPDATE(data_hora_prev_saida_nano) OR UPDATE(data_hora_prev_saida_offset) OR UPDATE(data_hora_prev_saida_sentinel) OR UPDATE(data_hora_real_chegada_p) OR UPDATE(data_hora_real_chegada_w) OR UPDATE(data_hora_real_chegada_raw) OR UPDATE(data_hora_real_chegada) OR UPDATE(data_hora_real_chegada_nano) OR UPDATE(data_hora_real_chegada_offset) OR UPDATE(data_hora_real_chegada_sentinel) OR UPDATE(data_hora_real_saida_p) OR UPDATE(data_hora_real_saida_w) OR UPDATE(data_hora_real_saida_raw) OR UPDATE(data_hora_real_saida) OR UPDATE(data_hora_real_saida_nano) OR UPDATE(data_hora_real_saida_offset) OR UPDATE(data_hora_real_saida_sentinel) OR UPDATE(latitude_p) OR UPDATE(latitude_w) OR UPDATE(latitude_raw) OR UPDATE(latitude) OR UPDATE(longitude_p) OR UPDATE(longitude_w) OR UPDATE(longitude_raw) OR UPDATE(longitude) OR UPDATE(dentro_prazo_p) OR UPDATE(dentro_prazo_w) OR UPDATE(dentro_prazo_raw) OR UPDATE(dentro_prazo) OR UPDATE(diferenca_tempo_p) OR UPDATE(diferenca_tempo_w) OR UPDATE(diferenca_tempo_raw) OR UPDATE(diferenca_tempo) OR UPDATE(km_percorrido_entrega_p) OR UPDATE(km_percorrido_entrega_w) OR UPDATE(km_percorrido_entrega_raw) OR UPDATE(km_percorrido_entrega) OR UPDATE(km_restante_entrega_p) OR UPDATE(km_restante_entrega_w) OR UPDATE(km_restante_entrega_raw) OR UPDATE(km_restante_entrega) OR UPDATE(chegou_na_entrega_p) OR UPDATE(chegou_na_entrega_w) OR UPDATE(chegou_na_entrega_raw) OR UPDATE(chegou_na_entrega) OR UPDATE(data_hora_ultima_posicao_p) OR UPDATE(data_hora_ultima_posicao_w) OR UPDATE(data_hora_ultima_posicao_raw) OR UPDATE(data_hora_ultima_posicao) OR UPDATE(data_hora_ultima_posicao_nano) OR UPDATE(data_hora_ultima_posicao_offset) OR UPDATE(data_hora_ultima_posicao_sentinel) OR UPDATE(latitude_ultima_posicao_p) OR UPDATE(latitude_ultima_posicao_w) OR UPDATE(latitude_ultima_posicao_raw) OR UPDATE(latitude_ultima_posicao) OR UPDATE(longitude_ultima_posicao_p) OR UPDATE(longitude_ultima_posicao_w) OR UPDATE(longitude_ultima_posicao_raw) OR UPDATE(longitude_ultima_posicao) OR UPDATE(referencia_ultima_posicao_p) OR UPDATE(referencia_ultima_posicao_w) OR UPDATE(referencia_ultima_posicao_raw) OR UPDATE(referencia_ultima_posicao)))
 OR EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.observation_id=d.observation_id WHERE i.observation_id IS NULL)
 THROW 53503,N'ANA_OBSERVATION_IMMUTABLE',1;
 IF NOT EXISTS(SELECT 1 FROM deleted) AND EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_raster_capture c ON c.capture_id=i.capture_id WHERE c.state<>'CAPTURING')
 THROW 53504,N'ANA_CAPTURE_SEALED',1;
END;
GO


CREATE TABLE stg.analytic_raster_structure (
 observation_id BIGINT NOT NULL PRIMARY KEY REFERENCES stg.analytic_raster_trip(observation_id),
 route_presence VARCHAR(6) NOT NULL,stops_presence VARCHAR(6) NOT NULL,
 CONSTRAINT CK_analytic_structure CHECK(route_presence IN('ABSENT','NULL','VALUE') AND stops_presence IN('ABSENT','NULL','VALUE'))
);
GO
CREATE TRIGGER ctl.trg_analytic_run_immutable ON ctl.analytic_lab_run AFTER UPDATE,DELETE
AS BEGIN IF EXISTS(SELECT 1 FROM deleted) THROW 53505,N'ANA_RUN_IMMUTABLE',1;END;
GO
CREATE TRIGGER core.trg_analytic_raster_trip_history_immutable ON core.analytic_raster_trip_history AFTER UPDATE,DELETE AS BEGIN IF EXISTS(SELECT 1 FROM deleted) THROW 53506,N'RAS_HISTORY_IMMUTABLE',1;END;
GO
CREATE TRIGGER core.trg_analytic_raster_stop_history_immutable ON core.analytic_raster_stop_history AFTER UPDATE,DELETE AS BEGIN IF EXISTS(SELECT 1 FROM deleted) THROW 53506,N'RAS_HISTORY_IMMUTABLE',1;END;
GO
