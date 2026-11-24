-- O executor é localizado pelo número do manifesto. As consultas de retirada
-- verificam exclusivamente a ocorrência 136 da minuta.
CREATE INDEX IX_dashboard_rede_manifesto
    ON core.dashboard_manifestos_leitura(sequence_code)
    INCLUDE (filial, proprietario_nome, proprietario_documento)
    WITH (DATA_COMPRESSION=PAGE, MAXDOP=1);

CREATE INDEX IX_dashboard_rede_retirada
    ON core.dashboard_performance_ocorrencias(minuta)
    WHERE codigo = 136
    WITH (DATA_COMPRESSION=PAGE, MAXDOP=1);
