$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory=Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'
if(Test-Path $directory){throw 'FIXTURE_ALREADY_EXISTS_PRESERVE'}
[void][IO.Directory]::CreateDirectory($directory)
$encoding=[Text.UTF8Encoding]::new($false)
foreach($template in @('6908','6389')){
 foreach($operation in @('info','data')){
  $text=[IO.File]::ReadAllText((Join-Path $root ('src/test/resources/runtime-laboratory-v2/'+$template+'-'+$operation+'.json')))
  if($operation -ceq 'data'){$text=$text.Replace('5406908','5506908').Replace('5406389','5506389').Replace('54001','55001').Replace('54002','55002').Replace('54009','55009').Replace('54011','55011').Replace('54012','55012')}
  [IO.File]::WriteAllText((Join-Path $directory ($template+'-'+$operation+'.json')),$text,$encoding)
 }
}
$schemas=@{
 '6399'=@{integers=@('sequence_code','mft_pfs_pck_sequence_code','mft_mfs_number');filter='manifests.service_date';strings=@('created_at','departured_at','closed_at','finished_at','status','mdfe_status','mft_mfs_key','km','total_cost','manifest_freights_total','total_taxed_weight','mft_vie_weight_capacity','manifest_items_count','finalized_manifest_items_count','mft_ape_name','mft_man_name','mft_vie_license_plate','mft_vie_vee_name','mft_vie_onr_name','mft_mdr_iil_name','mft_crn_psn_nickname','mft_cat_cot_number','contract_type','mft_mdr_contract_type','calculation_type','cargo_type','mft_uer_name','mft_aoe_rer_name','mft_aoe_comments','mft_cat_cot_status','mft_iks_id','mft_s_n_sequence_code','mft_tl1_license_plate','mft_tl2_license_plate','operational_comments','closing_comments','mft_s_n_svs_sge_pyr_nickname','mft_s_n_svs_sge_sse_name')};
 '6906'=@{integers=@('sequence_code');filter='quotes.requested_at';strings=@('requested_at','qoe_qes_fit_nse_issued_at','qoe_qes_fit_fhe_cte_issued_at','qoe_qes_total','qoe_crn_psn_nickname','qoe_uer_name','qoe_qes_ony_sae_code','qoe_qes_diy_sae_code')};
 '8656'=@{integers=@('corporation_sequence_number');filter='freights.service_at';strings=@('type','service_at','invoices_volumes','taxed_weight','invoices_value','total','service_type','fit_crn_psn_nickname','fit_dpn_delivery_prediction_at','fit_dyn_name','fit_dyn_drt_nickname','fit_fsn_name','fit_fln_status','fit_fln_cln_nickname','fit_o_n_name','fit_o_n_drt_nickname')}
}
foreach($key in $schemas.Keys){
 $s=$schemas[$key];$fields=@($s.integers|ForEach-Object{[ordered]@{name=$_;type='integer'}})+@($s.strings|ForEach-Object{[ordered]@{name=$_;type='string'}})
 $info=[ordered]@{fields=$fields;filters=@([ordered]@{name=$s.filter;type='date'})}
 [IO.File]::WriteAllText((Join-Path $directory ($key+'-info.json')),($info|ConvertTo-Json -Depth 6),$encoding)
}
$data=@{
 '6399'=@(
  [ordered]@{sequence_code=550639901;created_at='2024-01-01T12:00:00Z';status='closed';mdfe_status='authorized';km='0';mft_pfs_pck_sequence_code=55001;mft_mfs_key=('1'*44);mft_mfs_number=1;operational_comments=$null},
  [ordered]@{sequence_code=550639901;created_at='2024-01-01T12:00:00Z';status='closed';mdfe_status='authorized';km=$null;mft_pfs_pck_sequence_code=55002;mft_mfs_key=('2'*44);mft_mfs_number=2;operational_comments=$null});
 '6906'=@(
  [ordered]@{sequence_code=550690601;requested_at='2024-01-01';qoe_qes_total='10.00';qoe_qes_ony_sae_code='SP';qoe_qes_diy_sae_code='RJ';qoe_uer_name='SYNTHETIC A'},
  [ordered]@{sequence_code=550690602;requested_at='2024-01-01';qoe_qes_total='20.00';qoe_qes_ony_sae_code='RJ';qoe_qes_diy_sae_code='SP';qoe_uer_name=$null});
 '8656'=@(
  [ordered]@{corporation_sequence_number=550865601;service_at='2024-01-01';invoices_volumes='0';fit_fln_status='finished'},
  [ordered]@{corporation_sequence_number=550865602;service_at='2024-01-01';invoices_volumes=$null;fit_fln_status='unmapped-synthetic'})}
foreach($key in $data.Keys){[IO.File]::WriteAllText((Join-Path $directory ($key+'-data.json')),([ordered]@{data=$data[$key]}|ConvertTo-Json -Depth 6),$encoding)}
foreach($workload in @('manifestos','cotacoes','localizacao_cargas')){
 $policy=Get-Content (Join-Path $root 'config/laboratory/bloco54-temporal-coletas.json') -Raw|ConvertFrom-Json -AsHashtable
 $policy.version='bloco55-'+$workload+'-civil-v1';$policy.purpose='SYNTHETIC_BLOCO55_BACKFILL';$policy.workload=$workload
 $policy.invocation=[guid]::NewGuid().ToString();$policy.lookbackSeconds='0';$policy.firstUnpublished='2032-02-28';$policy.observedAt='2032-03-01T12:00:00Z';$policy.maximumBacklog='2'
 [IO.File]::WriteAllText((Join-Path $root ('config/laboratory/bloco55-temporal-'+$workload+'.json')),($policy|ConvertTo-Json),$encoding)
}
'B55_STATIC_SYNTHETIC_FIXTURES_AND_THREE_POLICIES_PREPARED'
