package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/** Bounded JDBC batches with explicit typed columns; no universe retained in Java. */
public final class JdbcRasterBatch implements AutoCloseable {
    private final PreparedStatement trips;
    private final PreparedStatement stops;
    private final PreparedStatement structures;
    private final UUID capture;
    private int pending;
    private long tripCount;
    private long stopCount;
    private int maximumPending;
    private int executedBatches;

    public JdbcRasterBatch(final Connection connection, final UUID capture) throws SQLException {
        this.capture = java.util.Objects.requireNonNull(capture);
        trips = connection.prepareStatement(TRIP_SQL);
        PreparedStatement child = null;
        PreparedStatement structure = null;
        try {
            child = connection.prepareStatement(STOP_SQL);
            structure =
                    connection.prepareStatement(
                            "INSERT stg.analytic_raster_structure(observation_id,route_presence,stops_presence)"
                                    + " SELECT observation_id,?,? FROM stg.analytic_raster_trip WHERE capture_id=? AND occur"
                                    + "rence=?");
            trips.setQueryTimeout(15);
            child.setQueryTimeout(15);
            structure.setQueryTimeout(15);
        } catch (final SQLException failure) {
            for (final var prepared : new PreparedStatement[] {trips, child, structure}) {
                if (prepared != null) {
                    try {
                        prepared.close();
                    } catch (final SQLException closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            throw failure;
        }
        stops = child;
        structures = structure;
    }

    private static final String TRIP_SQL =
            "INSERT stg.analytic_raster_trip ([capture_id],[occurrence],[trip_key],[revision],[act"
                    + "ive],[react"
                    + "ivate],[evidence],[valid],[comparison_bytes],[cod_solicitacao_p],[cod_solicitacao_w],"
                    + "[cod_solici"
                    + "tacao_raw],[cod_solicitacao],[sequencial_p],[sequencial_w],[sequencial_raw],[sequenci"
                    + "al],[cod_fi"
                    + "lial_p],[cod_filial_w],[cod_filial_raw],[cod_filial],[status_viagem_p],[status_viagem"
                    + "_w],[status"
                    + "_viagem_raw],[status_viagem],[placa_veiculo_p],[placa_veiculo_w],[placa_veiculo_raw],"
                    + "[placa_veic"
                    + "ulo],[placa_carreta1_p],[placa_carreta1_w],[placa_carreta1_raw],[placa_carreta1],[pla"
                    + "ca_carreta2"
                    + "_p],[placa_carreta2_w],[placa_carreta2_raw],[placa_carreta2],[placa_carreta3_p],[plac"
                    + "a_carreta3_"
                    + "w],[placa_carreta3_raw],[placa_carreta3],[cpf_motorista1_p],[cpf_motorista1_w],[cpf_m"
                    + "otorista1_r"
                    + "aw],[cpf_motorista1],[cpf_motorista2_p],[cpf_motorista2_w],[cpf_motorista2_raw],[cpf_"
                    + "motorista2]"
                    + ",[cnpj_cliente_orig_p],[cnpj_cliente_orig_w],[cnpj_cliente_orig_raw],[cnpj_cliente_or"
                    + "ig],[cnpj_c"
                    + "liente_dest_p],[cnpj_cliente_dest_w],[cnpj_cliente_dest_raw],[cnpj_cliente_dest],[cod"
                    + "_ibge_cidad"
                    + "e_orig_p],[cod_ibge_cidade_orig_w],[cod_ibge_cidade_orig_raw],[cod_ibge_cidade_orig],"
                    + "[cod_ibge_c"
                    + "idade_dest_p],[cod_ibge_cidade_dest_w],[cod_ibge_cidade_dest_raw],[cod_ibge_cidade_de"
                    + "st],[data_h"
                    + "ora_prev_ini_p],[data_hora_prev_ini_w],[data_hora_prev_ini_raw],[data_hora_prev_ini],"
                    + "[data_hora_"
                    + "prev_ini_nano],[data_hora_prev_ini_offset],[data_hora_prev_ini_sentinel],[data_hora_p"
                    + "rev_fim_p],"
                    + "[data_hora_prev_fim_w],[data_hora_prev_fim_raw],[data_hora_prev_fim],[data_hora_prev_"
                    + "fim_nano],["
                    + "data_hora_prev_fim_offset],[data_hora_prev_fim_sentinel],[data_hora_real_ini_p],[data"
                    + "_hora_real_"
                    + "ini_w],[data_hora_real_ini_raw],[data_hora_real_ini],[data_hora_real_ini_nano],[data_"
                    + "hora_real_i"
                    + "ni_offset],[data_hora_real_ini_sentinel],[data_hora_real_fim_p],[data_hora_real_fim_w"
                    + "],[data_hor"
                    + "a_real_fim_raw],[data_hora_real_fim],[data_hora_real_fim_nano],[data_hora_real_fim_of"
                    + "fset],[data"
                    + "_hora_real_fim_sentinel],[data_hora_identificou_fim_viagem_p],[data_hora_identificou_"
                    + "fim_viagem_"
                    + "w],[data_hora_identificou_fim_viagem_raw],[data_hora_identificou_fim_viagem],[data_ho"
                    + "ra_identifi"
                    + "cou_fim_viagem_nano],[data_hora_identificou_fim_viagem_offset],[data_hora_identificou"
                    + "_fim_viagem"
                    + "_sentinel],[tempo_total_viagem_p],[tempo_total_viagem_w],[tempo_total_viagem_raw],[te"
                    + "mpo_total_v"
                    + "iagem],[dentro_prazo_p],[dentro_prazo_w],[dentro_prazo_raw],[dentro_prazo],[percentua"
                    + "l_atraso_p]"
                    + ",[percentual_atraso_w],[percentual_atraso_raw],[percentual_atraso],[rodou_fora_horari"
                    + "o_p],[rodou"
                    + "_fora_horario_w],[rodou_fora_horario_raw],[rodou_fora_horario],[velocidade_media_p],["
                    + "velocidade_"
                    + "media_w],[velocidade_media_raw],[velocidade_media],[eventos_velocidade_p],[eventos_ve"
                    + "locidade_w]"
                    + ",[eventos_velocidade_raw],[eventos_velocidade],[desvios_de_rota_p],[desvios_de_rota_w"
                    + "],[desvios_"
                    + "de_rota_raw],[desvios_de_rota],[link_timeline_p],[link_timeline_w],[link_timeline_raw"
                    + "],[link_tim"
                    + "eline],[route_cod_rota_p],[route_cod_rota_w],[route_cod_rota_raw],[route_cod_rota],[r"
                    + "oute_descri"
                    + "cao_p],[route_descricao_w],[route_descricao_raw],[route_descricao]) VALUES (?,?,?,?,?"
                    + ",?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public void trip(final RasterTripObservation value, final RasterBinding binding)
            throws SQLException {
        if (binding != null && binding.stopKey() != null) {
            throw new IllegalArgumentException("RAS_BINDING_KIND");
        }
        final var bytes = new AnalyticFieldComparison();
        bytes.flag(binding == null ? null : binding.active());
        int index = 1;
        trips.setString(index++, capture.toString());
        trips.setLong(index++, ++tripCount);
        trips.setString(index++, binding == null ? null : binding.tripKey());

        trips.setObject(index++, binding == null ? null : binding.revision());
        trips.setObject(index++, binding == null ? null : binding.active());
        trips.setObject(index++, binding == null ? null : binding.reactivate());
        trips.setString(index++, binding == null ? null : binding.evidence());
        trips.setBoolean(index++, value.valid());
        final int comparisonIndex = index++;
        index = RasterJdbcValue.text(trips, index, value.trip().codSolicitacao());
        bytes.value(value.trip().codSolicitacao());
        index = RasterJdbcValue.text(trips, index, value.trip().sequencial());
        bytes.value(value.trip().sequencial());
        index = RasterJdbcValue.text(trips, index, value.trip().codFilial());
        bytes.value(value.trip().codFilial());
        index = RasterJdbcValue.text(trips, index, value.trip().statusViagem());
        bytes.value(value.trip().statusViagem());
        index = RasterJdbcValue.text(trips, index, value.trip().placaVeiculo());
        bytes.value(value.trip().placaVeiculo());
        index = RasterJdbcValue.text(trips, index, value.trip().placaCarreta1());
        bytes.value(value.trip().placaCarreta1());
        index = RasterJdbcValue.text(trips, index, value.trip().placaCarreta2());
        bytes.value(value.trip().placaCarreta2());
        index = RasterJdbcValue.text(trips, index, value.trip().placaCarreta3());
        bytes.value(value.trip().placaCarreta3());
        index = RasterJdbcValue.text(trips, index, value.trip().cpfMotorista1());
        bytes.value(value.trip().cpfMotorista1());
        index = RasterJdbcValue.text(trips, index, value.trip().cpfMotorista2());
        bytes.value(value.trip().cpfMotorista2());
        index = RasterJdbcValue.text(trips, index, value.trip().cnpjClienteOrig());
        bytes.value(value.trip().cnpjClienteOrig());
        index = RasterJdbcValue.text(trips, index, value.trip().cnpjClienteDest());
        bytes.value(value.trip().cnpjClienteDest());
        index = RasterJdbcValue.text(trips, index, value.trip().codIbgeCidadeOrig());
        bytes.value(value.trip().codIbgeCidadeOrig());
        index = RasterJdbcValue.text(trips, index, value.trip().codIbgeCidadeDest());
        bytes.value(value.trip().codIbgeCidadeDest());
        index = RasterJdbcValue.time(trips, index, value.trip().dataHoraPrevIni());
        bytes.value(value.trip().dataHoraPrevIni());
        index = RasterJdbcValue.time(trips, index, value.trip().dataHoraPrevFim());
        bytes.value(value.trip().dataHoraPrevFim());
        index = RasterJdbcValue.time(trips, index, value.trip().dataHoraRealIni());
        bytes.value(value.trip().dataHoraRealIni());
        index = RasterJdbcValue.time(trips, index, value.trip().dataHoraRealFim());
        bytes.value(value.trip().dataHoraRealFim());
        index = RasterJdbcValue.time(trips, index, value.trip().dataHoraIdentificouFimViagem());
        bytes.value(value.trip().dataHoraIdentificouFimViagem());
        index = RasterJdbcValue.count(trips, index, value.trip().tempoTotalViagem());
        bytes.value(value.trip().tempoTotalViagem());
        index = RasterJdbcValue.text(trips, index, value.trip().dentroPrazo());
        bytes.value(value.trip().dentroPrazo());
        index = RasterJdbcValue.decimal(trips, index, value.trip().percentualAtraso());
        bytes.value(value.trip().percentualAtraso());
        index = RasterJdbcValue.text(trips, index, value.trip().rodouForaHorario());
        bytes.value(value.trip().rodouForaHorario());
        index = RasterJdbcValue.decimal(trips, index, value.trip().velocidadeMedia());
        bytes.value(value.trip().velocidadeMedia());
        index = RasterJdbcValue.count(trips, index, value.trip().eventosVelocidade());
        bytes.value(value.trip().eventosVelocidade());
        index = RasterJdbcValue.count(trips, index, value.trip().desviosDeRota());
        bytes.value(value.trip().desviosDeRota());
        index = RasterJdbcValue.text(trips, index, value.trip().linkTimeline());
        bytes.value(value.trip().linkTimeline());
        index = RasterJdbcValue.text(trips, index, value.route().codRota());
        bytes.value(value.route().codRota());
        index = RasterJdbcValue.text(trips, index, value.route().descricao());
        bytes.value(value.route().descricao());
        bytes.text(value.routePresence().name());
        bytes.text(value.stopsPresence().name());
        trips.setBytes(comparisonIndex, bytes.bytesLimited(256 * 1024));
        trips.addBatch();
        structures.setString(1, value.routePresence().name());
        structures.setString(2, value.stopsPresence().name());
        structures.setString(3, capture.toString());
        structures.setLong(4, tripCount);
        structures.addBatch();
        pending++;
        maximumPending = Math.max(maximumPending, pending);
        if (pending == 16) {
            flush();
        }
    }

    private static final String STOP_SQL =
            "INSERT stg.analytic_raster_stop ([capture_id],[occurrence],[trip_key],[stop_key],[rev"
                    + "ision],[act"
                    + "ive],[reactivate],[evidence],[valid],[comparison_bytes],[ordem_p],[ordem_w],[ordem_ra"
                    + "w],[ordem],"
                    + "[tipo_p],[tipo_w],[tipo_raw],[tipo],[cod_ibge_cidade_p],[cod_ibge_cidade_w],[cod_ibge"
                    + "_cidade_raw"
                    + "],[cod_ibge_cidade],[cnpj_cliente_p],[cnpj_cliente_w],[cnpj_cliente_raw],[cnpj_client"
                    + "e],[codigo_"
                    + "cliente_p],[codigo_cliente_w],[codigo_cliente_raw],[codigo_cliente],[data_hora_prev_c"
                    + "hegada_p],["
                    + "data_hora_prev_chegada_w],[data_hora_prev_chegada_raw],[data_hora_prev_chegada],[data"
                    + "_hora_prev_"
                    + "chegada_nano],[data_hora_prev_chegada_offset],[data_hora_prev_chegada_sentinel],[data"
                    + "_hora_prev_"
                    + "saida_p],[data_hora_prev_saida_w],[data_hora_prev_saida_raw],[data_hora_prev_saida],["
                    + "data_hora_p"
                    + "rev_saida_nano],[data_hora_prev_saida_offset],[data_hora_prev_saida_sentinel],[data_h"
                    + "ora_real_ch"
                    + "egada_p],[data_hora_real_chegada_w],[data_hora_real_chegada_raw],[data_hora_real_cheg"
                    + "ada],[data_"
                    + "hora_real_chegada_nano],[data_hora_real_chegada_offset],[data_hora_real_chegada_senti"
                    + "nel],[data_"
                    + "hora_real_saida_p],[data_hora_real_saida_w],[data_hora_real_saida_raw],[data_hora_rea"
                    + "l_saida],[d"
                    + "ata_hora_real_saida_nano],[data_hora_real_saida_offset],[data_hora_real_saida_sentine"
                    + "l],[latitud"
                    + "e_p],[latitude_w],[latitude_raw],[latitude],[longitude_p],[longitude_w],[longitude_ra"
                    + "w],[longitu"
                    + "de],[dentro_prazo_p],[dentro_prazo_w],[dentro_prazo_raw],[dentro_prazo],[diferenca_te"
                    + "mpo_p],[dif"
                    + "erenca_tempo_w],[diferenca_tempo_raw],[diferenca_tempo],[km_percorrido_entrega_p],[km"
                    + "_percorrido"
                    + "_entrega_w],[km_percorrido_entrega_raw],[km_percorrido_entrega],[km_restante_entrega_"
                    + "p],[km_rest"
                    + "ante_entrega_w],[km_restante_entrega_raw],[km_restante_entrega],[chegou_na_entrega_p]"
                    + ",[chegou_na"
                    + "_entrega_w],[chegou_na_entrega_raw],[chegou_na_entrega],[data_hora_ultima_posicao_p],"
                    + "[data_hora_"
                    + "ultima_posicao_w],[data_hora_ultima_posicao_raw],[data_hora_ultima_posicao],[data_hor"
                    + "a_ultima_po"
                    + "sicao_nano],[data_hora_ultima_posicao_offset],[data_hora_ultima_posicao_sentinel],[la"
                    + "titude_ulti"
                    + "ma_posicao_p],[latitude_ultima_posicao_w],[latitude_ultima_posicao_raw],[latitude_ult"
                    + "ima_posicao"
                    + "],[longitude_ultima_posicao_p],[longitude_ultima_posicao_w],[longitude_ultima_posicao"
                    + "_raw],[long"
                    + "itude_ultima_posicao],[referencia_ultima_posicao_p],[referencia_ultima_posicao_w],[re"
                    + "ferencia_ul"
                    + "tima_posicao_raw],[referencia_ultima_posicao]) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public void stop(final RasterStop value, final RasterBinding binding) throws SQLException {
        if (binding != null && binding.stopKey() == null) {
            throw new IllegalArgumentException("RAS_BINDING_KIND");
        }
        final var bytes = new AnalyticFieldComparison();
        bytes.flag(binding == null ? null : binding.active());
        int index = 1;
        stops.setString(index++, capture.toString());
        stops.setLong(index++, ++stopCount);
        stops.setString(index++, binding == null ? null : binding.tripKey());
        stops.setString(index++, binding == null ? null : binding.stopKey());
        stops.setObject(index++, binding == null ? null : binding.revision());
        stops.setObject(index++, binding == null ? null : binding.active());
        stops.setObject(index++, binding == null ? null : binding.reactivate());
        stops.setString(index++, binding == null ? null : binding.evidence());
        stops.setBoolean(index++, value.valid());
        final int comparisonIndex = index++;
        index = RasterJdbcValue.text(stops, index, value.ordem());
        bytes.value(value.ordem());
        index = RasterJdbcValue.text(stops, index, value.tipo());
        bytes.value(value.tipo());
        index = RasterJdbcValue.text(stops, index, value.codIbgeCidade());
        bytes.value(value.codIbgeCidade());
        index = RasterJdbcValue.text(stops, index, value.cnpjCliente());
        bytes.value(value.cnpjCliente());
        index = RasterJdbcValue.text(stops, index, value.codigoCliente());
        bytes.value(value.codigoCliente());
        index = RasterJdbcValue.time(stops, index, value.dataHoraPrevChegada());
        bytes.value(value.dataHoraPrevChegada());
        index = RasterJdbcValue.time(stops, index, value.dataHoraPrevSaida());
        bytes.value(value.dataHoraPrevSaida());
        index = RasterJdbcValue.time(stops, index, value.dataHoraRealChegada());
        bytes.value(value.dataHoraRealChegada());
        index = RasterJdbcValue.time(stops, index, value.dataHoraRealSaida());
        bytes.value(value.dataHoraRealSaida());
        index = RasterJdbcValue.decimal(stops, index, value.latitude());
        bytes.value(value.latitude());
        index = RasterJdbcValue.decimal(stops, index, value.longitude());
        bytes.value(value.longitude());
        index = RasterJdbcValue.text(stops, index, value.dentroPrazo());
        bytes.value(value.dentroPrazo());
        index = RasterJdbcValue.text(stops, index, value.diferencaTempo());
        bytes.value(value.diferencaTempo());
        index = RasterJdbcValue.decimal(stops, index, value.kmPercorridoEntrega());
        bytes.value(value.kmPercorridoEntrega());
        index = RasterJdbcValue.decimal(stops, index, value.kmRestanteEntrega());
        bytes.value(value.kmRestanteEntrega());
        index = RasterJdbcValue.text(stops, index, value.chegouNaEntrega());
        bytes.value(value.chegouNaEntrega());
        index = RasterJdbcValue.time(stops, index, value.dataHoraUltimaPosicao());
        bytes.value(value.dataHoraUltimaPosicao());
        index = RasterJdbcValue.decimal(stops, index, value.latitudeUltimaPosicao());
        bytes.value(value.latitudeUltimaPosicao());
        index = RasterJdbcValue.decimal(stops, index, value.longitudeUltimaPosicao());
        bytes.value(value.longitudeUltimaPosicao());
        index = RasterJdbcValue.text(stops, index, value.referenciaUltimaPosicao());
        bytes.value(value.referenciaUltimaPosicao());
        stops.setBytes(comparisonIndex, bytes.bytesLimited(256 * 1024));
        stops.addBatch();
        pending++;
        maximumPending = Math.max(maximumPending, pending);
        if (pending == 16) {
            flush();
        }
    }

    public void flush() throws SQLException {
        if (pending > 0) {
            trips.executeBatch();
            stops.executeBatch();
            structures.executeBatch();
            trips.clearBatch();
            stops.clearBatch();
            structures.clearBatch();
            executedBatches++;
            pending = 0;
        }
    }

    public long tripCount() {
        return tripCount;
    }

    public long stopCount() {
        return stopCount;
    }

    public int maximumPending() {
        return maximumPending;
    }

    public int executedBatches() {
        return executedBatches;
    }

    @Override
    public void close() throws SQLException {
        try {
            trips.close();
        } finally {
            try {
                stops.close();
            } finally {
                structures.close();
            }
        }
    }
}
