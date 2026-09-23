-- ADR0050 ANA-31: exact terminal windows and latest valid observation, preserving business snapshots.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_raster_terminal_window (
 capture_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_raster_capture(capture_id),
 ordinal INT NOT NULL,start_date DATE NOT NULL,end_exclusive DATE NOT NULL,
 expected_roots BIGINT NOT NULL,expected_stops BIGINT NOT NULL,receipt VARCHAR(64) NOT NULL,
 source_instance VARCHAR(40) NOT NULL,tenant_scope VARCHAR(40) NOT NULL,contract_version VARCHAR(40) NOT NULL,
 CONSTRAINT PK_analytic_raster_terminal PRIMARY KEY(capture_id,ordinal),
 CONSTRAINT UQ_analytic_raster_terminal_window UNIQUE(capture_id,start_date,end_exclusive),
 CONSTRAINT CK_analytic_raster_terminal CHECK(ordinal BETWEEN 1 AND 10000 AND start_date<end_exclusive
 AND expected_roots BETWEEN 0 AND 499 AND expected_stops BETWEEN 0 AND 100000
 AND receipt LIKE 'synthetic-%' AND DATALENGTH(receipt) BETWEEN 11 AND 64
 AND receipt COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^a-zA-Z0-9-]%'
 AND source_instance='SYNTHETIC_ANALYTIC_LAB' AND tenant_scope='SYNTHETIC_ANALYTIC_TENANT'
 AND contract_version='synthetic-analytic-v1')
);
GO
CREATE TRIGGER ctl.tr_analytic_raster_terminal_immutable ON ctl.analytic_raster_terminal_window
INSTEAD OF UPDATE,DELETE AS BEGIN THROW 53801,N'RAS_TERMINAL_IMMUTABLE',1; END;
GO
CREATE TRIGGER ctl.tr_analytic_raster_terminal_scope ON ctl.analytic_raster_terminal_window
AFTER INSERT AS BEGIN
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_raster_capture c ON c.capture_id=i.capture_id
 WHERE c.state<>'CAPTURING' OR i.start_date<c.start_date OR i.end_exclusive>c.end_exclusive
 OR i.source_instance<>c.source_instance OR i.tenant_scope<>c.tenant_scope OR i.contract_version<>c.contract_version)
 THROW 53802,N'RAS_TERMINAL_CAPTURE_SCOPE',1;
END;
GO
CREATE PROCEDURE ctl.usp_seal_analytic_raster @capture_id UNIQUEIDENTIFIER,@roots BIGINT,@stops BIGINT,@calls INT
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT OFF;
 DECLARE @run UNIQUEIDENTIFIER,@start DATE,@end DATE,@maximum INT;
 SELECT @run=c.run_id,@start=c.start_date,@end=c.end_exclusive,@maximum=r.maximum_pages
 FROM ctl.analytic_raster_capture c JOIN ctl.analytic_lab_run r ON r.run_id=c.run_id
 WHERE c.capture_id=@capture_id AND c.state='CAPTURING';
 IF @run IS NULL THROW 53803,N'RAS_SEAL_STATE',1;
 EXEC ctl.usp_analytic_lab_lock @run,'RASTER';
 SELECT *,LAG(end_exclusive) OVER(ORDER BY ordinal) previous_end INTO #terminal
 FROM ctl.analytic_raster_terminal_window WHERE capture_id=@capture_id;
 DECLARE @leaves INT=(SELECT COUNT(*) FROM #terminal);
 IF @leaves=0 OR @calls<>2*@leaves-1 OR @calls>@maximum
 OR (SELECT MIN(ordinal) FROM #terminal)<>1 OR (SELECT MAX(ordinal) FROM #terminal)<>@leaves
 OR (SELECT start_date FROM #terminal WHERE ordinal=1)<>@start
 OR (SELECT end_exclusive FROM #terminal WHERE ordinal=@leaves)<>@end
 OR EXISTS(SELECT 1 FROM #terminal WHERE ordinal>1 AND start_date<>previous_end)
 OR (SELECT SUM(expected_roots) FROM #terminal)<>@roots
 OR (SELECT SUM(expected_stops) FROM #terminal)<>@stops
 OR (SELECT COUNT_BIG(*) FROM stg.analytic_raster_trip WHERE capture_id=@capture_id)<>@roots
 OR (SELECT COUNT_BIG(*) FROM stg.analytic_raster_stop WHERE capture_id=@capture_id)<>@stops
 THROW 53804,N'RAS_TERMINAL_WINDOWS_UNPROVEN',1;
 UPDATE ctl.analytic_raster_capture SET expected_roots=@roots,expected_stops=@stops,pages=@calls,
 receipt=CASE WHEN @leaves=1 THEN (SELECT receipt FROM #terminal WHERE ordinal=1)
 ELSE 'synthetic-window-ledger-v1' END,state='COMPLETE' WHERE capture_id=@capture_id;
END;
GO
ALTER TABLE core.analytic_raster_trip ADD last_observation_id BIGINT NULL
 REFERENCES stg.analytic_raster_trip(observation_id);
ALTER TABLE core.analytic_raster_stop ADD last_observation_id BIGINT NULL
 REFERENCES stg.analytic_raster_stop(observation_id);
GO
CREATE OR ALTER PROCEDURE core.usp_apply_analytic_raster @run_id UNIQUEIDENTIFIER,@capture_id UNIQUEIDENTIFIER
AS BEGIN SET NOCOUNT ON;SET XACT_ABORT OFF;
 EXEC ctl.usp_analytic_lab_lock @run_id,'RASTER';
 DECLARE @state VARCHAR(16),@now DATETIME2(7),@roots BIGINT,@stops BIGINT;
 SELECT @state=c.state,@now=c.extracted_at,@roots=c.expected_roots,@stops=c.expected_stops
 FROM ctl.analytic_raster_capture c JOIN ctl.analytic_lab_run r ON r.run_id=c.run_id
 WHERE c.run_id=@run_id AND c.capture_id=@capture_id AND c.source_instance=r.source_instance
 AND c.tenant_scope=r.tenant_scope AND c.contract_version=r.contract_version
 AND c.start_date>=r.window_start AND c.end_exclusive<=r.window_end_exclusive
 AND c.pages BETWEEN 1 AND r.maximum_pages AND c.expected_roots+c.expected_stops<=r.maximum_rows
 AND c.receipt LIKE 'synthetic-%';
 IF @state IS NULL OR @state='CAPTURING' THROW 53510,N'RAS_CAPTURE_INCOMPLETE',1;
 IF @state IN('APPLIED','DEGRADED') BEGIN
 SELECT observed_roots,observed_stops,applied,noops,stale,duplicates,quarantine,unbound,state FROM ctl.analytic_raster_capture WHERE capture_id=@capture_id;RETURN;END;
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_raster_terminal_window WHERE capture_id=@capture_id)
 THROW 53804,N'RAS_TERMINAL_WINDOWS_UNPROVEN',1;
 IF @roots<>(SELECT COUNT_BIG(*) FROM stg.analytic_raster_trip WHERE capture_id=@capture_id)
 OR @stops<>(SELECT COUNT_BIG(*) FROM stg.analytic_raster_stop WHERE capture_id=@capture_id)
 THROW 53511,N'RAS_CAPTURE_RECONCILIATION',1;
UPDATE stg.analytic_raster_trip SET disposition=CASE WHEN valid=0 THEN 'QUARANTINE'
 WHEN trip_key IS NULL  OR revision IS NULL OR active IS NULL OR reactivate IS NULL OR evidence IS NULL THEN 'UNBOUND' ELSE 'PENDING' END WHERE capture_id=@capture_id;
 SELECT s.*,DENSE_RANK() OVER(PARTITION BY trip_key ORDER BY revision DESC) freshness_rank
 INTO #trip_rank FROM stg.analytic_raster_trip s WHERE capture_id=@capture_id AND disposition='PENDING';
 UPDATE s SET disposition='STALE' FROM stg.analytic_raster_trip s JOIN #trip_rank r ON r.observation_id=s.observation_id WHERE r.freshness_rank>1;
 UPDATE s SET disposition='QUARANTINE' FROM stg.analytic_raster_trip s JOIN #trip_rank r ON r.observation_id=s.observation_id
 WHERE r.freshness_rank=1 AND EXISTS(SELECT 1 FROM #trip_rank x WHERE x.trip_key=r.trip_key AND x.freshness_rank=1 AND x.comparison_bytes<>r.comparison_bytes);
 SELECT trip_key,MIN(observation_id) observation_id INTO #trip_winner FROM stg.analytic_raster_trip
 WHERE capture_id=@capture_id AND disposition='PENDING' GROUP BY trip_key;
 UPDATE s SET disposition='DUPLICATE' FROM stg.analytic_raster_trip s JOIN #trip_winner w ON s.trip_key=w.trip_key
 WHERE s.capture_id=@capture_id AND s.disposition='PENDING' AND s.observation_id<>w.observation_id;
 
 UPDATE s SET disposition=CASE WHEN c.observation_id IS NULL THEN 'INSERTED' WHEN s.revision<c.revision THEN 'STALE'
 WHEN s.revision=c.revision THEN CASE WHEN s.comparison_bytes=c.comparison_bytes THEN 'NOOP' ELSE 'QUARANTINE' END
 WHEN c.active=0 AND s.active=1 AND s.reactivate=0 THEN 'QUARANTINE'
 WHEN s.comparison_bytes=c.comparison_bytes THEN 'NOOP' ELSE 'UPDATED' END
 FROM stg.analytic_raster_trip s LEFT JOIN core.analytic_raster_trip c ON c.run_id=@run_id AND c.trip_key=s.trip_key
 WHERE s.capture_id=@capture_id AND s.disposition='PENDING';
 INSERT core.analytic_raster_trip_history(run_id,trip_key,prior_observation,observation_id,action,recorded_at)
 SELECT @run_id,s.trip_key,c.observation_id,s.observation_id,s.disposition,@now
 FROM stg.analytic_raster_trip s LEFT JOIN core.analytic_raster_trip c ON c.run_id=@run_id AND c.trip_key=s.trip_key
 WHERE s.capture_id=@capture_id AND s.disposition IN('INSERTED','UPDATED');
 INSERT core.analytic_raster_trip(run_id,trip_key,revision,active,observation_id,comparison_bytes,extracted_at,cod_solicitacao_p,cod_solicitacao_w,cod_solicitacao_raw,cod_solicitacao,sequencial_p,sequencial_w,sequencial_raw,sequencial,cod_filial_p,cod_filial_w,cod_filial_raw,cod_filial,status_viagem_p,status_viagem_w,status_viagem_raw,status_viagem,placa_veiculo_p,placa_veiculo_w,placa_veiculo_raw,placa_veiculo,placa_carreta1_p,placa_carreta1_w,placa_carreta1_raw,placa_carreta1,placa_carreta2_p,placa_carreta2_w,placa_carreta2_raw,placa_carreta2,placa_carreta3_p,placa_carreta3_w,placa_carreta3_raw,placa_carreta3,cpf_motorista1_p,cpf_motorista1_w,cpf_motorista1_raw,cpf_motorista1,cpf_motorista2_p,cpf_motorista2_w,cpf_motorista2_raw,cpf_motorista2,cnpj_cliente_orig_p,cnpj_cliente_orig_w,cnpj_cliente_orig_raw,cnpj_cliente_orig,cnpj_cliente_dest_p,cnpj_cliente_dest_w,cnpj_cliente_dest_raw,cnpj_cliente_dest,cod_ibge_cidade_orig_p,cod_ibge_cidade_orig_w,cod_ibge_cidade_orig_raw,cod_ibge_cidade_orig,cod_ibge_cidade_dest_p,cod_ibge_cidade_dest_w,cod_ibge_cidade_dest_raw,cod_ibge_cidade_dest,data_hora_prev_ini_p,data_hora_prev_ini_w,data_hora_prev_ini_raw,data_hora_prev_ini,data_hora_prev_ini_nano,data_hora_prev_ini_offset,data_hora_prev_ini_sentinel,data_hora_prev_fim_p,data_hora_prev_fim_w,data_hora_prev_fim_raw,data_hora_prev_fim,data_hora_prev_fim_nano,data_hora_prev_fim_offset,data_hora_prev_fim_sentinel,data_hora_real_ini_p,data_hora_real_ini_w,data_hora_real_ini_raw,data_hora_real_ini,data_hora_real_ini_nano,data_hora_real_ini_offset,data_hora_real_ini_sentinel,data_hora_real_fim_p,data_hora_real_fim_w,data_hora_real_fim_raw,data_hora_real_fim,data_hora_real_fim_nano,data_hora_real_fim_offset,data_hora_real_fim_sentinel,data_hora_identificou_fim_viagem_p,data_hora_identificou_fim_viagem_w,data_hora_identificou_fim_viagem_raw,data_hora_identificou_fim_viagem,data_hora_identificou_fim_viagem_nano,data_hora_identificou_fim_viagem_offset,data_hora_identificou_fim_viagem_sentinel,tempo_total_viagem_p,tempo_total_viagem_w,tempo_total_viagem_raw,tempo_total_viagem,dentro_prazo_p,dentro_prazo_w,dentro_prazo_raw,dentro_prazo,percentual_atraso_p,percentual_atraso_w,percentual_atraso_raw,percentual_atraso,rodou_fora_horario_p,rodou_fora_horario_w,rodou_fora_horario_raw,rodou_fora_horario,velocidade_media_p,velocidade_media_w,velocidade_media_raw,velocidade_media,eventos_velocidade_p,eventos_velocidade_w,eventos_velocidade_raw,eventos_velocidade,desvios_de_rota_p,desvios_de_rota_w,desvios_de_rota_raw,desvios_de_rota,link_timeline_p,link_timeline_w,link_timeline_raw,link_timeline,route_cod_rota_p,route_cod_rota_w,route_cod_rota_raw,route_cod_rota,route_descricao_p,route_descricao_w,route_descricao_raw,route_descricao)
 SELECT @run_id,s.trip_key,s.revision,s.active,s.observation_id,s.comparison_bytes,@now,s.cod_solicitacao_p,s.cod_solicitacao_w,s.cod_solicitacao_raw,s.cod_solicitacao,s.sequencial_p,s.sequencial_w,s.sequencial_raw,s.sequencial,s.cod_filial_p,s.cod_filial_w,s.cod_filial_raw,s.cod_filial,s.status_viagem_p,s.status_viagem_w,s.status_viagem_raw,s.status_viagem,s.placa_veiculo_p,s.placa_veiculo_w,s.placa_veiculo_raw,s.placa_veiculo,s.placa_carreta1_p,s.placa_carreta1_w,s.placa_carreta1_raw,s.placa_carreta1,s.placa_carreta2_p,s.placa_carreta2_w,s.placa_carreta2_raw,s.placa_carreta2,s.placa_carreta3_p,s.placa_carreta3_w,s.placa_carreta3_raw,s.placa_carreta3,s.cpf_motorista1_p,s.cpf_motorista1_w,s.cpf_motorista1_raw,s.cpf_motorista1,s.cpf_motorista2_p,s.cpf_motorista2_w,s.cpf_motorista2_raw,s.cpf_motorista2,s.cnpj_cliente_orig_p,s.cnpj_cliente_orig_w,s.cnpj_cliente_orig_raw,s.cnpj_cliente_orig,s.cnpj_cliente_dest_p,s.cnpj_cliente_dest_w,s.cnpj_cliente_dest_raw,s.cnpj_cliente_dest,s.cod_ibge_cidade_orig_p,s.cod_ibge_cidade_orig_w,s.cod_ibge_cidade_orig_raw,s.cod_ibge_cidade_orig,s.cod_ibge_cidade_dest_p,s.cod_ibge_cidade_dest_w,s.cod_ibge_cidade_dest_raw,s.cod_ibge_cidade_dest,s.data_hora_prev_ini_p,s.data_hora_prev_ini_w,s.data_hora_prev_ini_raw,s.data_hora_prev_ini,s.data_hora_prev_ini_nano,s.data_hora_prev_ini_offset,s.data_hora_prev_ini_sentinel,s.data_hora_prev_fim_p,s.data_hora_prev_fim_w,s.data_hora_prev_fim_raw,s.data_hora_prev_fim,s.data_hora_prev_fim_nano,s.data_hora_prev_fim_offset,s.data_hora_prev_fim_sentinel,s.data_hora_real_ini_p,s.data_hora_real_ini_w,s.data_hora_real_ini_raw,s.data_hora_real_ini,s.data_hora_real_ini_nano,s.data_hora_real_ini_offset,s.data_hora_real_ini_sentinel,s.data_hora_real_fim_p,s.data_hora_real_fim_w,s.data_hora_real_fim_raw,s.data_hora_real_fim,s.data_hora_real_fim_nano,s.data_hora_real_fim_offset,s.data_hora_real_fim_sentinel,s.data_hora_identificou_fim_viagem_p,s.data_hora_identificou_fim_viagem_w,s.data_hora_identificou_fim_viagem_raw,s.data_hora_identificou_fim_viagem,s.data_hora_identificou_fim_viagem_nano,s.data_hora_identificou_fim_viagem_offset,s.data_hora_identificou_fim_viagem_sentinel,s.tempo_total_viagem_p,s.tempo_total_viagem_w,s.tempo_total_viagem_raw,s.tempo_total_viagem,s.dentro_prazo_p,s.dentro_prazo_w,s.dentro_prazo_raw,s.dentro_prazo,s.percentual_atraso_p,s.percentual_atraso_w,s.percentual_atraso_raw,s.percentual_atraso,s.rodou_fora_horario_p,s.rodou_fora_horario_w,s.rodou_fora_horario_raw,s.rodou_fora_horario,s.velocidade_media_p,s.velocidade_media_w,s.velocidade_media_raw,s.velocidade_media,s.eventos_velocidade_p,s.eventos_velocidade_w,s.eventos_velocidade_raw,s.eventos_velocidade,s.desvios_de_rota_p,s.desvios_de_rota_w,s.desvios_de_rota_raw,s.desvios_de_rota,s.link_timeline_p,s.link_timeline_w,s.link_timeline_raw,s.link_timeline,s.route_cod_rota_p,s.route_cod_rota_w,s.route_cod_rota_raw,s.route_cod_rota,s.route_descricao_p,s.route_descricao_w,s.route_descricao_raw,s.route_descricao
 FROM stg.analytic_raster_trip s WHERE s.capture_id=@capture_id AND s.disposition='INSERTED';
 UPDATE c SET revision=s.revision,active=s.active,observation_id=s.observation_id,comparison_bytes=s.comparison_bytes,extracted_at=@now,
 cod_solicitacao_p=CASE WHEN s.cod_solicitacao_p='ABSENT' THEN c.cod_solicitacao_p ELSE s.cod_solicitacao_p END,
 cod_solicitacao_w=CASE WHEN s.cod_solicitacao_p='ABSENT' THEN c.cod_solicitacao_w ELSE s.cod_solicitacao_w END,
 cod_solicitacao_raw=CASE WHEN s.cod_solicitacao_p='ABSENT' THEN c.cod_solicitacao_raw ELSE s.cod_solicitacao_raw END,
 cod_solicitacao=CASE WHEN s.cod_solicitacao_p='ABSENT' THEN c.cod_solicitacao ELSE s.cod_solicitacao END,
 sequencial_p=CASE WHEN s.sequencial_p='ABSENT' THEN c.sequencial_p ELSE s.sequencial_p END,
 sequencial_w=CASE WHEN s.sequencial_p='ABSENT' THEN c.sequencial_w ELSE s.sequencial_w END,
 sequencial_raw=CASE WHEN s.sequencial_p='ABSENT' THEN c.sequencial_raw ELSE s.sequencial_raw END,
 sequencial=CASE WHEN s.sequencial_p='ABSENT' THEN c.sequencial ELSE s.sequencial END,
 cod_filial_p=CASE WHEN s.cod_filial_p='ABSENT' THEN c.cod_filial_p ELSE s.cod_filial_p END,
 cod_filial_w=CASE WHEN s.cod_filial_p='ABSENT' THEN c.cod_filial_w ELSE s.cod_filial_w END,
 cod_filial_raw=CASE WHEN s.cod_filial_p='ABSENT' THEN c.cod_filial_raw ELSE s.cod_filial_raw END,
 cod_filial=CASE WHEN s.cod_filial_p='ABSENT' THEN c.cod_filial ELSE s.cod_filial END,
 status_viagem_p=CASE WHEN s.status_viagem_p='ABSENT' THEN c.status_viagem_p ELSE s.status_viagem_p END,
 status_viagem_w=CASE WHEN s.status_viagem_p='ABSENT' THEN c.status_viagem_w ELSE s.status_viagem_w END,
 status_viagem_raw=CASE WHEN s.status_viagem_p='ABSENT' THEN c.status_viagem_raw ELSE s.status_viagem_raw END,
 status_viagem=CASE WHEN s.status_viagem_p='ABSENT' THEN c.status_viagem ELSE s.status_viagem END,
 placa_veiculo_p=CASE WHEN s.placa_veiculo_p='ABSENT' THEN c.placa_veiculo_p ELSE s.placa_veiculo_p END,
 placa_veiculo_w=CASE WHEN s.placa_veiculo_p='ABSENT' THEN c.placa_veiculo_w ELSE s.placa_veiculo_w END,
 placa_veiculo_raw=CASE WHEN s.placa_veiculo_p='ABSENT' THEN c.placa_veiculo_raw ELSE s.placa_veiculo_raw END,
 placa_veiculo=CASE WHEN s.placa_veiculo_p='ABSENT' THEN c.placa_veiculo ELSE s.placa_veiculo END,
 placa_carreta1_p=CASE WHEN s.placa_carreta1_p='ABSENT' THEN c.placa_carreta1_p ELSE s.placa_carreta1_p END,
 placa_carreta1_w=CASE WHEN s.placa_carreta1_p='ABSENT' THEN c.placa_carreta1_w ELSE s.placa_carreta1_w END,
 placa_carreta1_raw=CASE WHEN s.placa_carreta1_p='ABSENT' THEN c.placa_carreta1_raw ELSE s.placa_carreta1_raw END,
 placa_carreta1=CASE WHEN s.placa_carreta1_p='ABSENT' THEN c.placa_carreta1 ELSE s.placa_carreta1 END,
 placa_carreta2_p=CASE WHEN s.placa_carreta2_p='ABSENT' THEN c.placa_carreta2_p ELSE s.placa_carreta2_p END,
 placa_carreta2_w=CASE WHEN s.placa_carreta2_p='ABSENT' THEN c.placa_carreta2_w ELSE s.placa_carreta2_w END,
 placa_carreta2_raw=CASE WHEN s.placa_carreta2_p='ABSENT' THEN c.placa_carreta2_raw ELSE s.placa_carreta2_raw END,
 placa_carreta2=CASE WHEN s.placa_carreta2_p='ABSENT' THEN c.placa_carreta2 ELSE s.placa_carreta2 END,
 placa_carreta3_p=CASE WHEN s.placa_carreta3_p='ABSENT' THEN c.placa_carreta3_p ELSE s.placa_carreta3_p END,
 placa_carreta3_w=CASE WHEN s.placa_carreta3_p='ABSENT' THEN c.placa_carreta3_w ELSE s.placa_carreta3_w END,
 placa_carreta3_raw=CASE WHEN s.placa_carreta3_p='ABSENT' THEN c.placa_carreta3_raw ELSE s.placa_carreta3_raw END,
 placa_carreta3=CASE WHEN s.placa_carreta3_p='ABSENT' THEN c.placa_carreta3 ELSE s.placa_carreta3 END,
 cpf_motorista1_p=CASE WHEN s.cpf_motorista1_p='ABSENT' THEN c.cpf_motorista1_p ELSE s.cpf_motorista1_p END,
 cpf_motorista1_w=CASE WHEN s.cpf_motorista1_p='ABSENT' THEN c.cpf_motorista1_w ELSE s.cpf_motorista1_w END,
 cpf_motorista1_raw=CASE WHEN s.cpf_motorista1_p='ABSENT' THEN c.cpf_motorista1_raw ELSE s.cpf_motorista1_raw END,
 cpf_motorista1=CASE WHEN s.cpf_motorista1_p='ABSENT' THEN c.cpf_motorista1 ELSE s.cpf_motorista1 END,
 cpf_motorista2_p=CASE WHEN s.cpf_motorista2_p='ABSENT' THEN c.cpf_motorista2_p ELSE s.cpf_motorista2_p END,
 cpf_motorista2_w=CASE WHEN s.cpf_motorista2_p='ABSENT' THEN c.cpf_motorista2_w ELSE s.cpf_motorista2_w END,
 cpf_motorista2_raw=CASE WHEN s.cpf_motorista2_p='ABSENT' THEN c.cpf_motorista2_raw ELSE s.cpf_motorista2_raw END,
 cpf_motorista2=CASE WHEN s.cpf_motorista2_p='ABSENT' THEN c.cpf_motorista2 ELSE s.cpf_motorista2 END,
 cnpj_cliente_orig_p=CASE WHEN s.cnpj_cliente_orig_p='ABSENT' THEN c.cnpj_cliente_orig_p ELSE s.cnpj_cliente_orig_p END,
 cnpj_cliente_orig_w=CASE WHEN s.cnpj_cliente_orig_p='ABSENT' THEN c.cnpj_cliente_orig_w ELSE s.cnpj_cliente_orig_w END,
 cnpj_cliente_orig_raw=CASE WHEN s.cnpj_cliente_orig_p='ABSENT' THEN c.cnpj_cliente_orig_raw ELSE s.cnpj_cliente_orig_raw END,
 cnpj_cliente_orig=CASE WHEN s.cnpj_cliente_orig_p='ABSENT' THEN c.cnpj_cliente_orig ELSE s.cnpj_cliente_orig END,
 cnpj_cliente_dest_p=CASE WHEN s.cnpj_cliente_dest_p='ABSENT' THEN c.cnpj_cliente_dest_p ELSE s.cnpj_cliente_dest_p END,
 cnpj_cliente_dest_w=CASE WHEN s.cnpj_cliente_dest_p='ABSENT' THEN c.cnpj_cliente_dest_w ELSE s.cnpj_cliente_dest_w END,
 cnpj_cliente_dest_raw=CASE WHEN s.cnpj_cliente_dest_p='ABSENT' THEN c.cnpj_cliente_dest_raw ELSE s.cnpj_cliente_dest_raw END,
 cnpj_cliente_dest=CASE WHEN s.cnpj_cliente_dest_p='ABSENT' THEN c.cnpj_cliente_dest ELSE s.cnpj_cliente_dest END,
 cod_ibge_cidade_orig_p=CASE WHEN s.cod_ibge_cidade_orig_p='ABSENT' THEN c.cod_ibge_cidade_orig_p ELSE s.cod_ibge_cidade_orig_p END,
 cod_ibge_cidade_orig_w=CASE WHEN s.cod_ibge_cidade_orig_p='ABSENT' THEN c.cod_ibge_cidade_orig_w ELSE s.cod_ibge_cidade_orig_w END,
 cod_ibge_cidade_orig_raw=CASE WHEN s.cod_ibge_cidade_orig_p='ABSENT' THEN c.cod_ibge_cidade_orig_raw ELSE s.cod_ibge_cidade_orig_raw END,
 cod_ibge_cidade_orig=CASE WHEN s.cod_ibge_cidade_orig_p='ABSENT' THEN c.cod_ibge_cidade_orig ELSE s.cod_ibge_cidade_orig END,
 cod_ibge_cidade_dest_p=CASE WHEN s.cod_ibge_cidade_dest_p='ABSENT' THEN c.cod_ibge_cidade_dest_p ELSE s.cod_ibge_cidade_dest_p END,
 cod_ibge_cidade_dest_w=CASE WHEN s.cod_ibge_cidade_dest_p='ABSENT' THEN c.cod_ibge_cidade_dest_w ELSE s.cod_ibge_cidade_dest_w END,
 cod_ibge_cidade_dest_raw=CASE WHEN s.cod_ibge_cidade_dest_p='ABSENT' THEN c.cod_ibge_cidade_dest_raw ELSE s.cod_ibge_cidade_dest_raw END,
 cod_ibge_cidade_dest=CASE WHEN s.cod_ibge_cidade_dest_p='ABSENT' THEN c.cod_ibge_cidade_dest ELSE s.cod_ibge_cidade_dest END,
 data_hora_prev_ini_p=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini_p ELSE s.data_hora_prev_ini_p END,
 data_hora_prev_ini_w=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini_w ELSE s.data_hora_prev_ini_w END,
 data_hora_prev_ini_raw=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini_raw ELSE s.data_hora_prev_ini_raw END,
 data_hora_prev_ini=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini ELSE s.data_hora_prev_ini END,
 data_hora_prev_ini_nano=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini_nano ELSE s.data_hora_prev_ini_nano END,
 data_hora_prev_ini_offset=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini_offset ELSE s.data_hora_prev_ini_offset END,
 data_hora_prev_ini_sentinel=CASE WHEN s.data_hora_prev_ini_p='ABSENT' THEN c.data_hora_prev_ini_sentinel ELSE s.data_hora_prev_ini_sentinel END,
 data_hora_prev_fim_p=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim_p ELSE s.data_hora_prev_fim_p END,
 data_hora_prev_fim_w=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim_w ELSE s.data_hora_prev_fim_w END,
 data_hora_prev_fim_raw=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim_raw ELSE s.data_hora_prev_fim_raw END,
 data_hora_prev_fim=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim ELSE s.data_hora_prev_fim END,
 data_hora_prev_fim_nano=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim_nano ELSE s.data_hora_prev_fim_nano END,
 data_hora_prev_fim_offset=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim_offset ELSE s.data_hora_prev_fim_offset END,
 data_hora_prev_fim_sentinel=CASE WHEN s.data_hora_prev_fim_p='ABSENT' THEN c.data_hora_prev_fim_sentinel ELSE s.data_hora_prev_fim_sentinel END,
 data_hora_real_ini_p=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini_p ELSE s.data_hora_real_ini_p END,
 data_hora_real_ini_w=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini_w ELSE s.data_hora_real_ini_w END,
 data_hora_real_ini_raw=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini_raw ELSE s.data_hora_real_ini_raw END,
 data_hora_real_ini=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini ELSE s.data_hora_real_ini END,
 data_hora_real_ini_nano=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini_nano ELSE s.data_hora_real_ini_nano END,
 data_hora_real_ini_offset=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini_offset ELSE s.data_hora_real_ini_offset END,
 data_hora_real_ini_sentinel=CASE WHEN s.data_hora_real_ini_p='ABSENT' THEN c.data_hora_real_ini_sentinel ELSE s.data_hora_real_ini_sentinel END,
 data_hora_real_fim_p=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim_p ELSE s.data_hora_real_fim_p END,
 data_hora_real_fim_w=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim_w ELSE s.data_hora_real_fim_w END,
 data_hora_real_fim_raw=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim_raw ELSE s.data_hora_real_fim_raw END,
 data_hora_real_fim=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim ELSE s.data_hora_real_fim END,
 data_hora_real_fim_nano=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim_nano ELSE s.data_hora_real_fim_nano END,
 data_hora_real_fim_offset=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim_offset ELSE s.data_hora_real_fim_offset END,
 data_hora_real_fim_sentinel=CASE WHEN s.data_hora_real_fim_p='ABSENT' THEN c.data_hora_real_fim_sentinel ELSE s.data_hora_real_fim_sentinel END,
 data_hora_identificou_fim_viagem_p=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem_p ELSE s.data_hora_identificou_fim_viagem_p END,
 data_hora_identificou_fim_viagem_w=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem_w ELSE s.data_hora_identificou_fim_viagem_w END,
 data_hora_identificou_fim_viagem_raw=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem_raw ELSE s.data_hora_identificou_fim_viagem_raw END,
 data_hora_identificou_fim_viagem=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem ELSE s.data_hora_identificou_fim_viagem END,
 data_hora_identificou_fim_viagem_nano=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem_nano ELSE s.data_hora_identificou_fim_viagem_nano END,
 data_hora_identificou_fim_viagem_offset=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem_offset ELSE s.data_hora_identificou_fim_viagem_offset END,
 data_hora_identificou_fim_viagem_sentinel=CASE WHEN s.data_hora_identificou_fim_viagem_p='ABSENT' THEN c.data_hora_identificou_fim_viagem_sentinel ELSE s.data_hora_identificou_fim_viagem_sentinel END,
 tempo_total_viagem_p=CASE WHEN s.tempo_total_viagem_p='ABSENT' THEN c.tempo_total_viagem_p ELSE s.tempo_total_viagem_p END,
 tempo_total_viagem_w=CASE WHEN s.tempo_total_viagem_p='ABSENT' THEN c.tempo_total_viagem_w ELSE s.tempo_total_viagem_w END,
 tempo_total_viagem_raw=CASE WHEN s.tempo_total_viagem_p='ABSENT' THEN c.tempo_total_viagem_raw ELSE s.tempo_total_viagem_raw END,
 tempo_total_viagem=CASE WHEN s.tempo_total_viagem_p='ABSENT' THEN c.tempo_total_viagem ELSE s.tempo_total_viagem END,
 dentro_prazo_p=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo_p ELSE s.dentro_prazo_p END,
 dentro_prazo_w=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo_w ELSE s.dentro_prazo_w END,
 dentro_prazo_raw=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo_raw ELSE s.dentro_prazo_raw END,
 dentro_prazo=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo ELSE s.dentro_prazo END,
 percentual_atraso_p=CASE WHEN s.percentual_atraso_p='ABSENT' THEN c.percentual_atraso_p ELSE s.percentual_atraso_p END,
 percentual_atraso_w=CASE WHEN s.percentual_atraso_p='ABSENT' THEN c.percentual_atraso_w ELSE s.percentual_atraso_w END,
 percentual_atraso_raw=CASE WHEN s.percentual_atraso_p='ABSENT' THEN c.percentual_atraso_raw ELSE s.percentual_atraso_raw END,
 percentual_atraso=CASE WHEN s.percentual_atraso_p='ABSENT' THEN c.percentual_atraso ELSE s.percentual_atraso END,
 rodou_fora_horario_p=CASE WHEN s.rodou_fora_horario_p='ABSENT' THEN c.rodou_fora_horario_p ELSE s.rodou_fora_horario_p END,
 rodou_fora_horario_w=CASE WHEN s.rodou_fora_horario_p='ABSENT' THEN c.rodou_fora_horario_w ELSE s.rodou_fora_horario_w END,
 rodou_fora_horario_raw=CASE WHEN s.rodou_fora_horario_p='ABSENT' THEN c.rodou_fora_horario_raw ELSE s.rodou_fora_horario_raw END,
 rodou_fora_horario=CASE WHEN s.rodou_fora_horario_p='ABSENT' THEN c.rodou_fora_horario ELSE s.rodou_fora_horario END,
 velocidade_media_p=CASE WHEN s.velocidade_media_p='ABSENT' THEN c.velocidade_media_p ELSE s.velocidade_media_p END,
 velocidade_media_w=CASE WHEN s.velocidade_media_p='ABSENT' THEN c.velocidade_media_w ELSE s.velocidade_media_w END,
 velocidade_media_raw=CASE WHEN s.velocidade_media_p='ABSENT' THEN c.velocidade_media_raw ELSE s.velocidade_media_raw END,
 velocidade_media=CASE WHEN s.velocidade_media_p='ABSENT' THEN c.velocidade_media ELSE s.velocidade_media END,
 eventos_velocidade_p=CASE WHEN s.eventos_velocidade_p='ABSENT' THEN c.eventos_velocidade_p ELSE s.eventos_velocidade_p END,
 eventos_velocidade_w=CASE WHEN s.eventos_velocidade_p='ABSENT' THEN c.eventos_velocidade_w ELSE s.eventos_velocidade_w END,
 eventos_velocidade_raw=CASE WHEN s.eventos_velocidade_p='ABSENT' THEN c.eventos_velocidade_raw ELSE s.eventos_velocidade_raw END,
 eventos_velocidade=CASE WHEN s.eventos_velocidade_p='ABSENT' THEN c.eventos_velocidade ELSE s.eventos_velocidade END,
 desvios_de_rota_p=CASE WHEN s.desvios_de_rota_p='ABSENT' THEN c.desvios_de_rota_p ELSE s.desvios_de_rota_p END,
 desvios_de_rota_w=CASE WHEN s.desvios_de_rota_p='ABSENT' THEN c.desvios_de_rota_w ELSE s.desvios_de_rota_w END,
 desvios_de_rota_raw=CASE WHEN s.desvios_de_rota_p='ABSENT' THEN c.desvios_de_rota_raw ELSE s.desvios_de_rota_raw END,
 desvios_de_rota=CASE WHEN s.desvios_de_rota_p='ABSENT' THEN c.desvios_de_rota ELSE s.desvios_de_rota END,
 link_timeline_p=CASE WHEN s.link_timeline_p='ABSENT' THEN c.link_timeline_p ELSE s.link_timeline_p END,
 link_timeline_w=CASE WHEN s.link_timeline_p='ABSENT' THEN c.link_timeline_w ELSE s.link_timeline_w END,
 link_timeline_raw=CASE WHEN s.link_timeline_p='ABSENT' THEN c.link_timeline_raw ELSE s.link_timeline_raw END,
 link_timeline=CASE WHEN s.link_timeline_p='ABSENT' THEN c.link_timeline ELSE s.link_timeline END,
 route_cod_rota_p=CASE WHEN s.route_cod_rota_p='ABSENT' THEN c.route_cod_rota_p ELSE s.route_cod_rota_p END,
 route_cod_rota_w=CASE WHEN s.route_cod_rota_p='ABSENT' THEN c.route_cod_rota_w ELSE s.route_cod_rota_w END,
 route_cod_rota_raw=CASE WHEN s.route_cod_rota_p='ABSENT' THEN c.route_cod_rota_raw ELSE s.route_cod_rota_raw END,
 route_cod_rota=CASE WHEN s.route_cod_rota_p='ABSENT' THEN c.route_cod_rota ELSE s.route_cod_rota END,
 route_descricao_p=CASE WHEN s.route_descricao_p='ABSENT' THEN c.route_descricao_p ELSE s.route_descricao_p END,
 route_descricao_w=CASE WHEN s.route_descricao_p='ABSENT' THEN c.route_descricao_w ELSE s.route_descricao_w END,
 route_descricao_raw=CASE WHEN s.route_descricao_p='ABSENT' THEN c.route_descricao_raw ELSE s.route_descricao_raw END,
 route_descricao=CASE WHEN s.route_descricao_p='ABSENT' THEN c.route_descricao ELSE s.route_descricao END
 FROM core.analytic_raster_trip c JOIN stg.analytic_raster_trip s ON c.trip_key=s.trip_key
 WHERE c.run_id=@run_id AND s.capture_id=@capture_id AND s.disposition='UPDATED';
 UPDATE c SET revision=s.revision FROM core.analytic_raster_trip c JOIN stg.analytic_raster_trip s ON c.trip_key=s.trip_key
 WHERE c.run_id=@run_id AND s.capture_id=@capture_id AND s.disposition='NOOP' AND s.revision>c.revision;
UPDATE stg.analytic_raster_stop SET disposition=CASE WHEN valid=0 THEN 'QUARANTINE'
 WHEN trip_key IS NULL OR stop_key IS NULL OR revision IS NULL OR active IS NULL OR reactivate IS NULL OR evidence IS NULL THEN 'UNBOUND' ELSE 'PENDING' END WHERE capture_id=@capture_id;
 SELECT s.*,DENSE_RANK() OVER(PARTITION BY trip_key,stop_key ORDER BY revision DESC) freshness_rank
 INTO #stop_rank FROM stg.analytic_raster_stop s WHERE capture_id=@capture_id AND disposition='PENDING';
 UPDATE s SET disposition='STALE' FROM stg.analytic_raster_stop s JOIN #stop_rank r ON r.observation_id=s.observation_id WHERE r.freshness_rank>1;
 UPDATE s SET disposition='QUARANTINE' FROM stg.analytic_raster_stop s JOIN #stop_rank r ON r.observation_id=s.observation_id
 WHERE r.freshness_rank=1 AND EXISTS(SELECT 1 FROM #stop_rank x WHERE x.trip_key=r.trip_key AND x.stop_key=r.stop_key AND x.freshness_rank=1 AND x.comparison_bytes<>r.comparison_bytes);
 SELECT trip_key,stop_key,MIN(observation_id) observation_id INTO #stop_winner FROM stg.analytic_raster_stop
 WHERE capture_id=@capture_id AND disposition='PENDING' GROUP BY trip_key,stop_key;
 UPDATE s SET disposition='DUPLICATE' FROM stg.analytic_raster_stop s JOIN #stop_winner w ON s.trip_key=w.trip_key AND s.stop_key=w.stop_key
 WHERE s.capture_id=@capture_id AND s.disposition='PENDING' AND s.observation_id<>w.observation_id;
 UPDATE s SET disposition='UNBOUND' FROM stg.analytic_raster_stop s
 WHERE s.capture_id=@capture_id AND s.disposition='PENDING' AND NOT EXISTS(SELECT 1 FROM core.analytic_raster_trip t WHERE t.run_id=@run_id AND t.trip_key=s.trip_key AND t.active=1);
 UPDATE s SET disposition=CASE WHEN c.observation_id IS NULL THEN 'INSERTED' WHEN s.revision<c.revision THEN 'STALE'
 WHEN s.revision=c.revision THEN CASE WHEN s.comparison_bytes=c.comparison_bytes THEN 'NOOP' ELSE 'QUARANTINE' END
 WHEN c.active=0 AND s.active=1 AND s.reactivate=0 THEN 'QUARANTINE'
 WHEN s.comparison_bytes=c.comparison_bytes THEN 'NOOP' ELSE 'UPDATED' END
 FROM stg.analytic_raster_stop s LEFT JOIN core.analytic_raster_stop c ON c.run_id=@run_id AND c.trip_key=s.trip_key AND c.stop_key=s.stop_key
 WHERE s.capture_id=@capture_id AND s.disposition='PENDING';
 INSERT core.analytic_raster_stop_history(run_id,trip_key,stop_key,prior_observation,observation_id,action,recorded_at)
 SELECT @run_id,s.trip_key,s.stop_key,c.observation_id,s.observation_id,s.disposition,@now
 FROM stg.analytic_raster_stop s LEFT JOIN core.analytic_raster_stop c ON c.run_id=@run_id AND c.trip_key=s.trip_key AND c.stop_key=s.stop_key
 WHERE s.capture_id=@capture_id AND s.disposition IN('INSERTED','UPDATED');
 INSERT core.analytic_raster_stop(run_id,trip_key,stop_key,revision,active,observation_id,comparison_bytes,extracted_at,ordem_p,ordem_w,ordem_raw,ordem,tipo_p,tipo_w,tipo_raw,tipo,cod_ibge_cidade_p,cod_ibge_cidade_w,cod_ibge_cidade_raw,cod_ibge_cidade,cnpj_cliente_p,cnpj_cliente_w,cnpj_cliente_raw,cnpj_cliente,codigo_cliente_p,codigo_cliente_w,codigo_cliente_raw,codigo_cliente,data_hora_prev_chegada_p,data_hora_prev_chegada_w,data_hora_prev_chegada_raw,data_hora_prev_chegada,data_hora_prev_chegada_nano,data_hora_prev_chegada_offset,data_hora_prev_chegada_sentinel,data_hora_prev_saida_p,data_hora_prev_saida_w,data_hora_prev_saida_raw,data_hora_prev_saida,data_hora_prev_saida_nano,data_hora_prev_saida_offset,data_hora_prev_saida_sentinel,data_hora_real_chegada_p,data_hora_real_chegada_w,data_hora_real_chegada_raw,data_hora_real_chegada,data_hora_real_chegada_nano,data_hora_real_chegada_offset,data_hora_real_chegada_sentinel,data_hora_real_saida_p,data_hora_real_saida_w,data_hora_real_saida_raw,data_hora_real_saida,data_hora_real_saida_nano,data_hora_real_saida_offset,data_hora_real_saida_sentinel,latitude_p,latitude_w,latitude_raw,latitude,longitude_p,longitude_w,longitude_raw,longitude,dentro_prazo_p,dentro_prazo_w,dentro_prazo_raw,dentro_prazo,diferenca_tempo_p,diferenca_tempo_w,diferenca_tempo_raw,diferenca_tempo,km_percorrido_entrega_p,km_percorrido_entrega_w,km_percorrido_entrega_raw,km_percorrido_entrega,km_restante_entrega_p,km_restante_entrega_w,km_restante_entrega_raw,km_restante_entrega,chegou_na_entrega_p,chegou_na_entrega_w,chegou_na_entrega_raw,chegou_na_entrega,data_hora_ultima_posicao_p,data_hora_ultima_posicao_w,data_hora_ultima_posicao_raw,data_hora_ultima_posicao,data_hora_ultima_posicao_nano,data_hora_ultima_posicao_offset,data_hora_ultima_posicao_sentinel,latitude_ultima_posicao_p,latitude_ultima_posicao_w,latitude_ultima_posicao_raw,latitude_ultima_posicao,longitude_ultima_posicao_p,longitude_ultima_posicao_w,longitude_ultima_posicao_raw,longitude_ultima_posicao,referencia_ultima_posicao_p,referencia_ultima_posicao_w,referencia_ultima_posicao_raw,referencia_ultima_posicao)
 SELECT @run_id,s.trip_key,s.stop_key,s.revision,s.active,s.observation_id,s.comparison_bytes,@now,s.ordem_p,s.ordem_w,s.ordem_raw,s.ordem,s.tipo_p,s.tipo_w,s.tipo_raw,s.tipo,s.cod_ibge_cidade_p,s.cod_ibge_cidade_w,s.cod_ibge_cidade_raw,s.cod_ibge_cidade,s.cnpj_cliente_p,s.cnpj_cliente_w,s.cnpj_cliente_raw,s.cnpj_cliente,s.codigo_cliente_p,s.codigo_cliente_w,s.codigo_cliente_raw,s.codigo_cliente,s.data_hora_prev_chegada_p,s.data_hora_prev_chegada_w,s.data_hora_prev_chegada_raw,s.data_hora_prev_chegada,s.data_hora_prev_chegada_nano,s.data_hora_prev_chegada_offset,s.data_hora_prev_chegada_sentinel,s.data_hora_prev_saida_p,s.data_hora_prev_saida_w,s.data_hora_prev_saida_raw,s.data_hora_prev_saida,s.data_hora_prev_saida_nano,s.data_hora_prev_saida_offset,s.data_hora_prev_saida_sentinel,s.data_hora_real_chegada_p,s.data_hora_real_chegada_w,s.data_hora_real_chegada_raw,s.data_hora_real_chegada,s.data_hora_real_chegada_nano,s.data_hora_real_chegada_offset,s.data_hora_real_chegada_sentinel,s.data_hora_real_saida_p,s.data_hora_real_saida_w,s.data_hora_real_saida_raw,s.data_hora_real_saida,s.data_hora_real_saida_nano,s.data_hora_real_saida_offset,s.data_hora_real_saida_sentinel,s.latitude_p,s.latitude_w,s.latitude_raw,s.latitude,s.longitude_p,s.longitude_w,s.longitude_raw,s.longitude,s.dentro_prazo_p,s.dentro_prazo_w,s.dentro_prazo_raw,s.dentro_prazo,s.diferenca_tempo_p,s.diferenca_tempo_w,s.diferenca_tempo_raw,s.diferenca_tempo,s.km_percorrido_entrega_p,s.km_percorrido_entrega_w,s.km_percorrido_entrega_raw,s.km_percorrido_entrega,s.km_restante_entrega_p,s.km_restante_entrega_w,s.km_restante_entrega_raw,s.km_restante_entrega,s.chegou_na_entrega_p,s.chegou_na_entrega_w,s.chegou_na_entrega_raw,s.chegou_na_entrega,s.data_hora_ultima_posicao_p,s.data_hora_ultima_posicao_w,s.data_hora_ultima_posicao_raw,s.data_hora_ultima_posicao,s.data_hora_ultima_posicao_nano,s.data_hora_ultima_posicao_offset,s.data_hora_ultima_posicao_sentinel,s.latitude_ultima_posicao_p,s.latitude_ultima_posicao_w,s.latitude_ultima_posicao_raw,s.latitude_ultima_posicao,s.longitude_ultima_posicao_p,s.longitude_ultima_posicao_w,s.longitude_ultima_posicao_raw,s.longitude_ultima_posicao,s.referencia_ultima_posicao_p,s.referencia_ultima_posicao_w,s.referencia_ultima_posicao_raw,s.referencia_ultima_posicao
 FROM stg.analytic_raster_stop s WHERE s.capture_id=@capture_id AND s.disposition='INSERTED';
 UPDATE c SET revision=s.revision,active=s.active,observation_id=s.observation_id,comparison_bytes=s.comparison_bytes,extracted_at=@now,
 ordem_p=CASE WHEN s.ordem_p='ABSENT' THEN c.ordem_p ELSE s.ordem_p END,
 ordem_w=CASE WHEN s.ordem_p='ABSENT' THEN c.ordem_w ELSE s.ordem_w END,
 ordem_raw=CASE WHEN s.ordem_p='ABSENT' THEN c.ordem_raw ELSE s.ordem_raw END,
 ordem=CASE WHEN s.ordem_p='ABSENT' THEN c.ordem ELSE s.ordem END,
 tipo_p=CASE WHEN s.tipo_p='ABSENT' THEN c.tipo_p ELSE s.tipo_p END,
 tipo_w=CASE WHEN s.tipo_p='ABSENT' THEN c.tipo_w ELSE s.tipo_w END,
 tipo_raw=CASE WHEN s.tipo_p='ABSENT' THEN c.tipo_raw ELSE s.tipo_raw END,
 tipo=CASE WHEN s.tipo_p='ABSENT' THEN c.tipo ELSE s.tipo END,
 cod_ibge_cidade_p=CASE WHEN s.cod_ibge_cidade_p='ABSENT' THEN c.cod_ibge_cidade_p ELSE s.cod_ibge_cidade_p END,
 cod_ibge_cidade_w=CASE WHEN s.cod_ibge_cidade_p='ABSENT' THEN c.cod_ibge_cidade_w ELSE s.cod_ibge_cidade_w END,
 cod_ibge_cidade_raw=CASE WHEN s.cod_ibge_cidade_p='ABSENT' THEN c.cod_ibge_cidade_raw ELSE s.cod_ibge_cidade_raw END,
 cod_ibge_cidade=CASE WHEN s.cod_ibge_cidade_p='ABSENT' THEN c.cod_ibge_cidade ELSE s.cod_ibge_cidade END,
 cnpj_cliente_p=CASE WHEN s.cnpj_cliente_p='ABSENT' THEN c.cnpj_cliente_p ELSE s.cnpj_cliente_p END,
 cnpj_cliente_w=CASE WHEN s.cnpj_cliente_p='ABSENT' THEN c.cnpj_cliente_w ELSE s.cnpj_cliente_w END,
 cnpj_cliente_raw=CASE WHEN s.cnpj_cliente_p='ABSENT' THEN c.cnpj_cliente_raw ELSE s.cnpj_cliente_raw END,
 cnpj_cliente=CASE WHEN s.cnpj_cliente_p='ABSENT' THEN c.cnpj_cliente ELSE s.cnpj_cliente END,
 codigo_cliente_p=CASE WHEN s.codigo_cliente_p='ABSENT' THEN c.codigo_cliente_p ELSE s.codigo_cliente_p END,
 codigo_cliente_w=CASE WHEN s.codigo_cliente_p='ABSENT' THEN c.codigo_cliente_w ELSE s.codigo_cliente_w END,
 codigo_cliente_raw=CASE WHEN s.codigo_cliente_p='ABSENT' THEN c.codigo_cliente_raw ELSE s.codigo_cliente_raw END,
 codigo_cliente=CASE WHEN s.codigo_cliente_p='ABSENT' THEN c.codigo_cliente ELSE s.codigo_cliente END,
 data_hora_prev_chegada_p=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada_p ELSE s.data_hora_prev_chegada_p END,
 data_hora_prev_chegada_w=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada_w ELSE s.data_hora_prev_chegada_w END,
 data_hora_prev_chegada_raw=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada_raw ELSE s.data_hora_prev_chegada_raw END,
 data_hora_prev_chegada=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada ELSE s.data_hora_prev_chegada END,
 data_hora_prev_chegada_nano=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada_nano ELSE s.data_hora_prev_chegada_nano END,
 data_hora_prev_chegada_offset=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada_offset ELSE s.data_hora_prev_chegada_offset END,
 data_hora_prev_chegada_sentinel=CASE WHEN s.data_hora_prev_chegada_p='ABSENT' THEN c.data_hora_prev_chegada_sentinel ELSE s.data_hora_prev_chegada_sentinel END,
 data_hora_prev_saida_p=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida_p ELSE s.data_hora_prev_saida_p END,
 data_hora_prev_saida_w=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida_w ELSE s.data_hora_prev_saida_w END,
 data_hora_prev_saida_raw=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida_raw ELSE s.data_hora_prev_saida_raw END,
 data_hora_prev_saida=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida ELSE s.data_hora_prev_saida END,
 data_hora_prev_saida_nano=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida_nano ELSE s.data_hora_prev_saida_nano END,
 data_hora_prev_saida_offset=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida_offset ELSE s.data_hora_prev_saida_offset END,
 data_hora_prev_saida_sentinel=CASE WHEN s.data_hora_prev_saida_p='ABSENT' THEN c.data_hora_prev_saida_sentinel ELSE s.data_hora_prev_saida_sentinel END,
 data_hora_real_chegada_p=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada_p ELSE s.data_hora_real_chegada_p END,
 data_hora_real_chegada_w=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada_w ELSE s.data_hora_real_chegada_w END,
 data_hora_real_chegada_raw=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada_raw ELSE s.data_hora_real_chegada_raw END,
 data_hora_real_chegada=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada ELSE s.data_hora_real_chegada END,
 data_hora_real_chegada_nano=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada_nano ELSE s.data_hora_real_chegada_nano END,
 data_hora_real_chegada_offset=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada_offset ELSE s.data_hora_real_chegada_offset END,
 data_hora_real_chegada_sentinel=CASE WHEN s.data_hora_real_chegada_p='ABSENT' THEN c.data_hora_real_chegada_sentinel ELSE s.data_hora_real_chegada_sentinel END,
 data_hora_real_saida_p=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida_p ELSE s.data_hora_real_saida_p END,
 data_hora_real_saida_w=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida_w ELSE s.data_hora_real_saida_w END,
 data_hora_real_saida_raw=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida_raw ELSE s.data_hora_real_saida_raw END,
 data_hora_real_saida=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida ELSE s.data_hora_real_saida END,
 data_hora_real_saida_nano=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida_nano ELSE s.data_hora_real_saida_nano END,
 data_hora_real_saida_offset=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida_offset ELSE s.data_hora_real_saida_offset END,
 data_hora_real_saida_sentinel=CASE WHEN s.data_hora_real_saida_p='ABSENT' THEN c.data_hora_real_saida_sentinel ELSE s.data_hora_real_saida_sentinel END,
 latitude_p=CASE WHEN s.latitude_p='ABSENT' THEN c.latitude_p ELSE s.latitude_p END,
 latitude_w=CASE WHEN s.latitude_p='ABSENT' THEN c.latitude_w ELSE s.latitude_w END,
 latitude_raw=CASE WHEN s.latitude_p='ABSENT' THEN c.latitude_raw ELSE s.latitude_raw END,
 latitude=CASE WHEN s.latitude_p='ABSENT' THEN c.latitude ELSE s.latitude END,
 longitude_p=CASE WHEN s.longitude_p='ABSENT' THEN c.longitude_p ELSE s.longitude_p END,
 longitude_w=CASE WHEN s.longitude_p='ABSENT' THEN c.longitude_w ELSE s.longitude_w END,
 longitude_raw=CASE WHEN s.longitude_p='ABSENT' THEN c.longitude_raw ELSE s.longitude_raw END,
 longitude=CASE WHEN s.longitude_p='ABSENT' THEN c.longitude ELSE s.longitude END,
 dentro_prazo_p=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo_p ELSE s.dentro_prazo_p END,
 dentro_prazo_w=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo_w ELSE s.dentro_prazo_w END,
 dentro_prazo_raw=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo_raw ELSE s.dentro_prazo_raw END,
 dentro_prazo=CASE WHEN s.dentro_prazo_p='ABSENT' THEN c.dentro_prazo ELSE s.dentro_prazo END,
 diferenca_tempo_p=CASE WHEN s.diferenca_tempo_p='ABSENT' THEN c.diferenca_tempo_p ELSE s.diferenca_tempo_p END,
 diferenca_tempo_w=CASE WHEN s.diferenca_tempo_p='ABSENT' THEN c.diferenca_tempo_w ELSE s.diferenca_tempo_w END,
 diferenca_tempo_raw=CASE WHEN s.diferenca_tempo_p='ABSENT' THEN c.diferenca_tempo_raw ELSE s.diferenca_tempo_raw END,
 diferenca_tempo=CASE WHEN s.diferenca_tempo_p='ABSENT' THEN c.diferenca_tempo ELSE s.diferenca_tempo END,
 km_percorrido_entrega_p=CASE WHEN s.km_percorrido_entrega_p='ABSENT' THEN c.km_percorrido_entrega_p ELSE s.km_percorrido_entrega_p END,
 km_percorrido_entrega_w=CASE WHEN s.km_percorrido_entrega_p='ABSENT' THEN c.km_percorrido_entrega_w ELSE s.km_percorrido_entrega_w END,
 km_percorrido_entrega_raw=CASE WHEN s.km_percorrido_entrega_p='ABSENT' THEN c.km_percorrido_entrega_raw ELSE s.km_percorrido_entrega_raw END,
 km_percorrido_entrega=CASE WHEN s.km_percorrido_entrega_p='ABSENT' THEN c.km_percorrido_entrega ELSE s.km_percorrido_entrega END,
 km_restante_entrega_p=CASE WHEN s.km_restante_entrega_p='ABSENT' THEN c.km_restante_entrega_p ELSE s.km_restante_entrega_p END,
 km_restante_entrega_w=CASE WHEN s.km_restante_entrega_p='ABSENT' THEN c.km_restante_entrega_w ELSE s.km_restante_entrega_w END,
 km_restante_entrega_raw=CASE WHEN s.km_restante_entrega_p='ABSENT' THEN c.km_restante_entrega_raw ELSE s.km_restante_entrega_raw END,
 km_restante_entrega=CASE WHEN s.km_restante_entrega_p='ABSENT' THEN c.km_restante_entrega ELSE s.km_restante_entrega END,
 chegou_na_entrega_p=CASE WHEN s.chegou_na_entrega_p='ABSENT' THEN c.chegou_na_entrega_p ELSE s.chegou_na_entrega_p END,
 chegou_na_entrega_w=CASE WHEN s.chegou_na_entrega_p='ABSENT' THEN c.chegou_na_entrega_w ELSE s.chegou_na_entrega_w END,
 chegou_na_entrega_raw=CASE WHEN s.chegou_na_entrega_p='ABSENT' THEN c.chegou_na_entrega_raw ELSE s.chegou_na_entrega_raw END,
 chegou_na_entrega=CASE WHEN s.chegou_na_entrega_p='ABSENT' THEN c.chegou_na_entrega ELSE s.chegou_na_entrega END,
 data_hora_ultima_posicao_p=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao_p ELSE s.data_hora_ultima_posicao_p END,
 data_hora_ultima_posicao_w=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao_w ELSE s.data_hora_ultima_posicao_w END,
 data_hora_ultima_posicao_raw=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao_raw ELSE s.data_hora_ultima_posicao_raw END,
 data_hora_ultima_posicao=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao ELSE s.data_hora_ultima_posicao END,
 data_hora_ultima_posicao_nano=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao_nano ELSE s.data_hora_ultima_posicao_nano END,
 data_hora_ultima_posicao_offset=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao_offset ELSE s.data_hora_ultima_posicao_offset END,
 data_hora_ultima_posicao_sentinel=CASE WHEN s.data_hora_ultima_posicao_p='ABSENT' THEN c.data_hora_ultima_posicao_sentinel ELSE s.data_hora_ultima_posicao_sentinel END,
 latitude_ultima_posicao_p=CASE WHEN s.latitude_ultima_posicao_p='ABSENT' THEN c.latitude_ultima_posicao_p ELSE s.latitude_ultima_posicao_p END,
 latitude_ultima_posicao_w=CASE WHEN s.latitude_ultima_posicao_p='ABSENT' THEN c.latitude_ultima_posicao_w ELSE s.latitude_ultima_posicao_w END,
 latitude_ultima_posicao_raw=CASE WHEN s.latitude_ultima_posicao_p='ABSENT' THEN c.latitude_ultima_posicao_raw ELSE s.latitude_ultima_posicao_raw END,
 latitude_ultima_posicao=CASE WHEN s.latitude_ultima_posicao_p='ABSENT' THEN c.latitude_ultima_posicao ELSE s.latitude_ultima_posicao END,
 longitude_ultima_posicao_p=CASE WHEN s.longitude_ultima_posicao_p='ABSENT' THEN c.longitude_ultima_posicao_p ELSE s.longitude_ultima_posicao_p END,
 longitude_ultima_posicao_w=CASE WHEN s.longitude_ultima_posicao_p='ABSENT' THEN c.longitude_ultima_posicao_w ELSE s.longitude_ultima_posicao_w END,
 longitude_ultima_posicao_raw=CASE WHEN s.longitude_ultima_posicao_p='ABSENT' THEN c.longitude_ultima_posicao_raw ELSE s.longitude_ultima_posicao_raw END,
 longitude_ultima_posicao=CASE WHEN s.longitude_ultima_posicao_p='ABSENT' THEN c.longitude_ultima_posicao ELSE s.longitude_ultima_posicao END,
 referencia_ultima_posicao_p=CASE WHEN s.referencia_ultima_posicao_p='ABSENT' THEN c.referencia_ultima_posicao_p ELSE s.referencia_ultima_posicao_p END,
 referencia_ultima_posicao_w=CASE WHEN s.referencia_ultima_posicao_p='ABSENT' THEN c.referencia_ultima_posicao_w ELSE s.referencia_ultima_posicao_w END,
 referencia_ultima_posicao_raw=CASE WHEN s.referencia_ultima_posicao_p='ABSENT' THEN c.referencia_ultima_posicao_raw ELSE s.referencia_ultima_posicao_raw END,
 referencia_ultima_posicao=CASE WHEN s.referencia_ultima_posicao_p='ABSENT' THEN c.referencia_ultima_posicao ELSE s.referencia_ultima_posicao END
 FROM core.analytic_raster_stop c JOIN stg.analytic_raster_stop s ON c.trip_key=s.trip_key AND c.stop_key=s.stop_key
 WHERE c.run_id=@run_id AND s.capture_id=@capture_id AND s.disposition='UPDATED';
 UPDATE c SET revision=s.revision FROM core.analytic_raster_stop c JOIN stg.analytic_raster_stop s ON c.trip_key=s.trip_key AND c.stop_key=s.stop_key
 WHERE c.run_id=@run_id AND s.capture_id=@capture_id AND s.disposition='NOOP' AND s.revision>c.revision;
 UPDATE c SET extracted_at=@now,last_observation_id=s.observation_id
 FROM core.analytic_raster_trip c JOIN stg.analytic_raster_trip s ON c.trip_key=s.trip_key
 WHERE c.run_id=@run_id AND s.capture_id=@capture_id
 AND s.disposition IN('INSERTED','UPDATED','NOOP','STALE')
 AND (@now>c.extracted_at OR (@now=c.extracted_at AND s.observation_id>COALESCE(c.last_observation_id,0)))
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_raster_trip later WHERE later.capture_id=s.capture_id
 AND later.trip_key=s.trip_key 
 AND later.disposition IN('INSERTED','UPDATED','NOOP','STALE') AND later.observation_id>s.observation_id);
 UPDATE c SET extracted_at=@now,last_observation_id=s.observation_id
 FROM core.analytic_raster_stop c JOIN stg.analytic_raster_stop s ON c.trip_key=s.trip_key AND c.stop_key=s.stop_key
 WHERE c.run_id=@run_id AND s.capture_id=@capture_id
 AND s.disposition IN('INSERTED','UPDATED','NOOP','STALE')
 AND (@now>c.extracted_at OR (@now=c.extracted_at AND s.observation_id>COALESCE(c.last_observation_id,0)))
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_raster_stop later WHERE later.capture_id=s.capture_id
 AND later.trip_key=s.trip_key AND later.stop_key=s.stop_key
 AND later.disposition IN('INSERTED','UPDATED','NOOP','STALE') AND later.observation_id>s.observation_id);
SELECT disposition INTO #all_dispositions FROM stg.analytic_raster_trip WHERE capture_id=@capture_id
 UNION ALL SELECT disposition FROM stg.analytic_raster_stop WHERE capture_id=@capture_id;
 IF EXISTS(SELECT 1 FROM #all_dispositions WHERE disposition NOT IN('INSERTED','UPDATED','NOOP','STALE','DUPLICATE','QUARANTINE','UNBOUND')) THROW 53512,N'RAS_DISPOSITION_UNCLOSED',1;
 UPDATE ctl.analytic_raster_capture SET observed_roots=@roots,observed_stops=@stops,
 applied=(SELECT COUNT_BIG(*) FROM #all_dispositions WHERE disposition IN('INSERTED','UPDATED')),
 noops=(SELECT COUNT_BIG(*) FROM #all_dispositions WHERE disposition='NOOP'),
 stale=(SELECT COUNT_BIG(*) FROM #all_dispositions WHERE disposition='STALE'),
 duplicates=(SELECT COUNT_BIG(*) FROM #all_dispositions WHERE disposition='DUPLICATE'),
 quarantine=(SELECT COUNT_BIG(*) FROM #all_dispositions WHERE disposition='QUARANTINE'),
 unbound=(SELECT COUNT_BIG(*) FROM #all_dispositions WHERE disposition='UNBOUND'),
 state=CASE WHEN EXISTS(SELECT 1 FROM #all_dispositions WHERE disposition IN('QUARANTINE','UNBOUND')) THEN 'DEGRADED' ELSE 'APPLIED' END
 WHERE capture_id=@capture_id;
 SELECT observed_roots,observed_stops,applied,noops,stale,duplicates,quarantine,unbound,state FROM ctl.analytic_raster_capture WHERE capture_id=@capture_id;
END;
GO


CREATE OR ALTER VIEW pub.analytic_lab_sql_13 AS
WITH base AS (
 SELECT v.run_id,v.trip_key,p.stop_key,v.observation_id trip_observation,p.observation_id stop_observation,
 v.cod_solicitacao,v.placa_veiculo,v.status_viagem,v.cnpj_cliente_orig,v.cnpj_cliente_dest,
 v.cod_ibge_cidade_orig,v.cod_ibge_cidade_dest,v.tempo_total_viagem,
 v.dentro_prazo dentro_prazo_raster,v.percentual_atraso percentual_atraso_raster,
 v.route_cod_rota cod_rota,v.route_descricao rota_descricao,v.link_timeline,
 p.ordem,p.tipo,p.cod_ibge_cidade parada_cidade,p.cnpj_cliente parada_cnpj,
 core.ufn_analytic_raster_time(v.data_hora_prev_ini,v.data_hora_prev_ini_nano,v.data_hora_prev_ini_offset) data_hora_prev_ini,
 core.ufn_analytic_raster_time(v.data_hora_prev_fim,v.data_hora_prev_fim_nano,v.data_hora_prev_fim_offset) data_hora_prev_fim,
 core.ufn_analytic_raster_time(p.data_hora_prev_chegada,p.data_hora_prev_chegada_nano,p.data_hora_prev_chegada_offset) data_hora_prev_chegada,
 core.ufn_analytic_raster_time(v.data_hora_real_ini,v.data_hora_real_ini_nano,v.data_hora_real_ini_offset) data_hora_real_ini,
 core.ufn_analytic_raster_time(v.data_hora_real_fim,v.data_hora_real_fim_nano,v.data_hora_real_fim_offset) data_hora_real_fim,
 core.ufn_analytic_raster_time(p.data_hora_real_chegada,p.data_hora_real_chegada_nano,p.data_hora_real_chegada_offset) data_hora_real_chegada,
 core.ufn_analytic_raster_time(p.data_hora_real_saida,p.data_hora_real_saida_nano,p.data_hora_real_saida_offset) data_hora_real_saida,
 v.extracted_at trip_extracted,p.extracted_at stop_extracted,
 COALESCE(v.last_observation_id,v.observation_id) trip_last_observation,
 COALESCE(p.last_observation_id,p.observation_id) stop_last_observation
 FROM core.analytic_raster_trip v
 LEFT JOIN core.analytic_raster_stop p ON p.run_id=v.run_id AND p.trip_key=v.trip_key AND p.active=1
 WHERE v.active=1
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_raster_trip q JOIN ctl.analytic_raster_capture c ON c.capture_id=q.capture_id
 WHERE c.run_id=v.run_id AND q.trip_key=v.trip_key AND q.revision>=v.revision AND q.disposition='QUARANTINE')
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_raster_stop q JOIN ctl.analytic_raster_capture c ON c.capture_id=q.capture_id
 WHERE c.run_id=p.run_id AND q.trip_key=p.trip_key AND q.stop_key=p.stop_key AND q.revision>=p.revision AND q.disposition='QUARANTINE')
),normalized AS (
 SELECT *,NULLIF(LTRIM(RTRIM(REPLACE(REPLACE(rota_descricao,N'/BRASIL',N''),N' ATE ',N'|'))),N'') route_text
 FROM base
),route_parts AS (
 SELECT *,CASE WHEN CHARINDEX(N'|',route_text)>0 AND CHARINDEX(N'|',route_text,CHARINDEX(N'|',route_text)+1)=0
 THEN NULLIF(LTRIM(RTRIM(LEFT(route_text,CHARINDEX(N'|',route_text)-1))),N'') END route_origin,
 CASE WHEN CHARINDEX(N'|',route_text)>0 AND CHARINDEX(N'|',route_text,CHARINDEX(N'|',route_text)+1)=0
 THEN NULLIF(LTRIM(RTRIM(SUBSTRING(route_text,CHARINDEX(N'|',route_text)+1,1024))),N'') END route_destination
 FROM normalized
),resolved AS (
 SELECT *,COALESCE(route_origin,NULLIF(cnpj_cliente_orig,N''),cod_ibge_cidade_orig) origem_sm,
 COALESCE(parada_cidade,route_destination,NULLIF(parada_cnpj,N''),NULLIF(cnpj_cliente_dest,N''),cod_ibge_cidade_dest) destino_sm,
 CASE WHEN route_origin IS NOT NULL THEN 'ROUTE_V1' WHEN NULLIF(cnpj_cliente_orig,N'') IS NOT NULL THEN 'TRIP_CNPJ' ELSE 'TRIP_CITY' END origin_provenance,
 CASE WHEN parada_cidade IS NOT NULL THEN 'STOP_CITY' WHEN route_destination IS NOT NULL THEN 'ROUTE_V1'
 WHEN NULLIF(parada_cnpj,N'') IS NOT NULL THEN 'STOP_CNPJ' WHEN NULLIF(cnpj_cliente_dest,N'') IS NOT NULL THEN 'TRIP_CNPJ' ELSE 'TRIP_CITY' END destination_provenance,
 DATEDIFF(MINUTE,data_hora_prev_ini,COALESCE(data_hora_prev_chegada,data_hora_prev_fim)) fallback_minutes,
 CASE WHEN stop_extracted>trip_extracted THEN stop_extracted ELSE trip_extracted END data_extracao_raster,
 CASE WHEN stop_extracted>trip_extracted THEN 'STOP' ELSE 'TRIP' END extraction_provenance
 FROM route_parts
),duration AS (
 SELECT *,CASE WHEN tempo_total_viagem BETWEEN 0 AND 43200 THEN tempo_total_viagem
 WHEN fallback_minutes BETWEEN 0 AND 43200 THEN fallback_minutes END transit_minutes,
 CASE WHEN tempo_total_viagem BETWEEN 0 AND 43200 THEN 'DIRECT'
 WHEN fallback_minutes BETWEEN 0 AND 43200 THEN 'PREDICTED_DIFFERENCE' ELSE 'INVALID_OR_ABSENT' END duration_provenance,
 CONVERT(BIT,CASE WHEN tempo_total_viagem IS NOT NULL AND tempo_total_viagem NOT BETWEEN 0 AND 43200 THEN 1 ELSE 0 END) invalid_direct_duration
 FROM resolved
),presentation AS (
 SELECT *,CONCAT(origem_sm,N' x ',destino_sm) origem_destino,
 CASE WHEN CHARINDEX(N'/',origem_sm)>0 THEN LTRIM(RTRIM(LEFT(origem_sm,CHARINDEX(N'/',origem_sm)-1)))
 WHEN CHARINDEX(N'-',origem_sm)>0 THEN LTRIM(RTRIM(LEFT(origem_sm,CHARINDEX(N'-',origem_sm)-1))) ELSE origem_sm END origem_nome,
 CASE WHEN CHARINDEX(N'/',destino_sm)>0 THEN LTRIM(RTRIM(LEFT(destino_sm,CHARINDEX(N'/',destino_sm)-1)))
 WHEN CHARINDEX(N'-',destino_sm)>0 THEN LTRIM(RTRIM(LEFT(destino_sm,CHARINDEX(N'-',destino_sm)-1))) ELSE destino_sm END destino_nome,
 CASE WHEN ordem IS NOT NULL THEN CONCAT(ordem,N'º') END ordem_parada_label,
 CONVERT(CHAR(5),CAST(data_hora_prev_ini AS TIME),108) horario_corte_texto,
 CONVERT(CHAR(5),CAST(COALESCE(data_hora_prev_chegada,data_hora_prev_fim) AS TIME),108) previsao_chegada_destino,
 CASE WHEN transit_minutes IS NOT NULL THEN CONCAT(
 CASE WHEN transit_minutes/60<100 THEN RIGHT('00'+CONVERT(VARCHAR(2),transit_minutes/60),2) ELSE CONVERT(VARCHAR(10),transit_minutes/60) END,
 ':',RIGHT('00'+CONVERT(VARCHAR(2),transit_minutes%60),2)) END transit_time_texto
 FROM duration
)
SELECT cod_solicitacao,placa_veiculo,status_viagem,
 origem_sm [ORIGEM - SM],destino_sm [DESTINO - SM],origem_destino [Origem x Destino],origem_nome [ORIGEM],
 ordem_parada_label [ORDEM],destino_nome [DESTINO],horario_corte_texto [HORÁRIO CORTE],
 previsao_chegada_destino [PREV. CHEGADA (destino)],transit_time_texto [TRANSIT TIME],
 origem_sm,destino_sm,origem_destino,origem_nome,ordem_parada_label,destino_nome,horario_corte_texto,
 previsao_chegada_destino,transit_time_texto,ordem ordem_parada,tipo tipo_parada,
 data_hora_prev_ini data_hora_prev_ini_raster,data_hora_prev_fim data_hora_prev_fim_raster,
 data_hora_prev_chegada data_hora_prev_chegada_parada,data_hora_real_ini,data_hora_real_fim,
 data_hora_real_chegada,data_hora_real_saida,dentro_prazo_raster,percentual_atraso_raster,
 cod_rota,rota_descricao,link_timeline,data_extracao_raster [Data de extracao],data_extracao_raster,
 run_id,trip_key,stop_key,trip_observation,stop_observation,transit_minutes,duration_provenance,
 origin_provenance,destination_provenance,extraction_provenance,invalid_direct_duration,
 cnpj_cliente_orig,COALESCE(NULLIF(parada_cnpj,N''),NULLIF(cnpj_cliente_dest,N'')) destination_cnpj,
 CONVERT(VARCHAR(32),'RASTER_ROUTE_V1') route_policy,trip_last_observation,stop_last_observation
FROM presentation;
GO
