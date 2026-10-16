package br.com.esl.etl.v2.plataforma.fonte.devlocal;

/** Safe embedded synthetic sample and independently declared assertions for packaged DEV LOCAL. */
final class DevLocalSamples {
    private DevLocalSamples() {}

    static final String SAMPLE =
            """
            {
              "synthetic": true,
              "datasetVersion": "DEV_LOCAL_V1",
              "filter": {
                "startInclusive": "2026-10-01T00:00:00Z",
                "endExclusive": "2026-10-02T00:00:00Z",
                "zone": "America/Sao_Paulo"
              },
              "entities": [
                {
                  "entity": "Users",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "id": 1101,
                          "name": "Synthetic Alice"
                        },
                        {
                          "id": "1101",
                          "name": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "id": 1102
                        },
                        {
                          "id": false,
                          "name": "Synthetic Invalid"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Manifestos",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "sequence_code": 2101,
                          "finished_at": "2026-10-01T10:00:00Z",
                          "mft_pfs_pck_sequence_code": 3101
                        },
                        {
                          "sequence_code": 2102,
                          "finished_at": "2026-10-01T11:00:00Z",
                          "contract_type": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "sequence_code": 2103,
                          "finished_at": "2026-10-02T00:00:00Z",
                          "contract_type": "synthetic"
                        },
                        {
                          "sequence_code": "2101",
                          "finished_at": "2026-10-01T10:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Coletas",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "id": 3101,
                          "status_updated_at": "2026-10-01T10:00:00Z",
                          "status": "pending"
                        },
                        {
                          "id": 3102,
                          "sequence_code": null,
                          "status_updated_at": "2026-10-01T11:00:00Z"
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "id": 3103,
                          "sequence_code": 301,
                          "status_updated_at": "2026-10-02T00:00:00Z"
                        },
                        {
                          "id": true,
                          "status_updated_at": "2026-10-01T10:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Fretes",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "id": 4101,
                          "cte_created_at": "2026-10-01T10:00:00Z",
                          "total": 150.25,
                          "fit_p_m_pck_sequence_code": 3101
                        },
                        {
                          "id": 4102,
                          "cte_created_at": "2026-10-01T11:00:00Z",
                          "reference_number": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "id": 4103,
                          "cte_created_at": "2026-10-02T00:00:00Z",
                          "reference_number": "SYN-FRETE"
                        },
                        {
                          "id": "4101",
                          "cte_created_at": "2026-10-01T10:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Cotacoes",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "sequence_code": 5101,
                          "requested_at": "2026-10-01T10:00:00Z",
                          "qoe_qes_total": 150.25
                        },
                        {
                          "sequence_code": 5102,
                          "requested_at": "2026-10-01T11:00:00Z",
                          "qoe_crn_psn_nickname": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "sequence_code": 5103,
                          "requested_at": "2026-10-02T00:00:00Z",
                          "qoe_crn_psn_nickname": "Synthetic Org"
                        },
                        {
                          "sequence_code": false,
                          "requested_at": "2026-10-01T10:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Localizacao",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "corporation_sequence_number": 6101,
                          "service_at": "2026-10-01T10:00:00Z",
                          "invoices_volumes": 2,
                          "total": 50.25,
                          "fit_fln_status": "finished"
                        },
                        {
                          "corporation_sequence_number": 6102,
                          "service_at": "2026-10-01T11:00:00Z",
                          "fit_dyn_name": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "corporation_sequence_number": 6103,
                          "service_at": "2026-10-02T00:00:00Z",
                          "fit_dyn_name": "Synthetic City"
                        },
                        {
                          "corporation_sequence_number": "6101",
                          "service_at": "2026-10-01T10:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "CAP",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "ant_ils_sequence_code": 7101,
                          "value": 100.25,
                          "paid": false
                        },
                        {
                          "ant_ils_sequence_code": 7102,
                          "comments": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "ant_ils_sequence_code": 7103,
                          "comments": "Synthetic payable"
                        },
                        {
                          "ant_ils_sequence_code": 7104,
                          "value": "invalid-decimal"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Faturas",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "id": 8101,
                          "total": 150.25
                        },
                        {
                          "id": 8102,
                          "comments": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "id": 8103,
                          "comments": "Synthetic invoice"
                        },
                        {
                          "id": 8104,
                          "total": "invalid-decimal"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Inventario",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "sequence_code": 9101,
                          "cnr_c_s_fit_invoices_value": 25.75
                        },
                        {
                          "sequence_code": 9102,
                          "cnr_c_s_fit_dyn_name": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "sequence_code": 9103,
                          "cnr_c_s_fit_dyn_name": "Synthetic depot"
                        },
                        {
                          "sequence_code": 9104,
                          "cnr_c_s_fit_real_weight": "invalid-decimal"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Sinistros",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "sequence_code": 10101,
                          "insurance_claim_total": 550.25
                        },
                        {
                          "sequence_code": 10102,
                          "opening_at_date": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "sequence_code": 10103,
                          "opening_at_date": "2026-10-01"
                        },
                        {
                          "sequence_code": 10104,
                          "insurance_claim_total": "invalid-decimal"
                        }
                      ]
                    }
                  ]
                },
                {
                  "entity": "Raster",
                  "pages": [
                    {
                      "number": 1,
                      "terminal": false,
                      "rows": [
                        {
                          "CodSolicitacao": "11001",
                          "ColetasEntregas": [
                            {
                              "Ordem": "1",
                              "Tipo": "Coleta",
                              "Latitude": -23.5,
                              "Longitude": -46.6
                            }
                          ]
                        },
                        {
                          "CodSolicitacao": "11002",
                          "Rota": null,
                          "ColetasEntregas": null
                        }
                      ]
                    },
                    {
                      "number": 2,
                      "terminal": true,
                      "rows": [
                        {
                          "CodSolicitacao": "11003",
                          "Rota": {},
                          "ColetasEntregas": []
                        },
                        {
                          "CodSolicitacao": false,
                          "ColetasEntregas": []
                        }
                      ]
                    }
                  ]
                }
              ],
              "relationBindings": [
                {
                  "relation": "MC",
                  "originKey": "INTEGER:2101",
                  "originComponent": "INTEGER:3101",
                  "targetKey": "INTEGER:3101",
                  "targetComponent": "ROOT",
                  "evidence": "synthetic-mc-fixture-v1",
                  "revision": 1,
                  "cardinality": "ONE_TO_ONE",
                  "date": "2026-10-01"
                },
                {
                  "relation": "CF",
                  "originKey": "INTEGER:3101",
                  "originComponent": "INTEGER:4101",
                  "targetKey": "INTEGER:4101",
                  "targetComponent": "INTEGER:3101",
                  "evidence": "synthetic-cf-fixture-v1",
                  "revision": 1,
                  "cardinality": "ONE_TO_ONE",
                  "date": "2026-10-01"
                }
              ]
            }
            """;
    static final String EXPECTED =
            """
            {
              "synthetic": true,
              "entities": [
                {
                  "entity": "Users",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 3,
                  "quarantined": 1,
                  "filtered": 0,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "INTEGER:1101"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/1/sourceKey",
                      "value": "STRING:1101"
                    }
                  ]
                },
                {
                  "entity": "Manifestos",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 2,
                  "quarantined": 1,
                  "filtered": 1,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "INTEGER:2101"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/freshness",
                      "value": "2026-10-01T10:00:00Z"
                    },
                    {
                      "path": "/rows/2/filterDecision",
                      "value": "OUTSIDE_WINDOW"
                    },
                    {
                      "path": "/rows/0/relationKey",
                      "value": "INTEGER:3101"
                    }
                  ]
                },
                {
                  "entity": "Coletas",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 2,
                  "quarantined": 1,
                  "filtered": 1,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "INTEGER:3101"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/freshness",
                      "value": "2026-10-01T10:00:00Z"
                    },
                    {
                      "path": "/rows/2/filterDecision",
                      "value": "OUTSIDE_WINDOW"
                    }
                  ]
                },
                {
                  "entity": "Fretes",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 2,
                  "quarantined": 1,
                  "filtered": 1,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "INTEGER:4101"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/freshness",
                      "value": "2026-10-01T10:00:00Z"
                    },
                    {
                      "path": "/rows/2/filterDecision",
                      "value": "OUTSIDE_WINDOW"
                    }
                  ]
                },
                {
                  "entity": "Cotacoes",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 2,
                  "quarantined": 1,
                  "filtered": 1,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "INTEGER:5101"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/freshness",
                      "value": "2026-10-01T10:00:00Z"
                    },
                    {
                      "path": "/rows/2/filterDecision",
                      "value": "OUTSIDE_WINDOW"
                    },
                    {
                      "path": "/rows/0/total",
                      "value": "150.25"
                    }
                  ]
                },
                {
                  "entity": "Localizacao",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 2,
                  "quarantined": 1,
                  "filtered": 1,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "INTEGER:6101"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/freshness",
                      "value": "2026-10-01T10:00:00Z"
                    },
                    {
                      "path": "/rows/2/filterDecision",
                      "value": "OUTSIDE_WINDOW"
                    },
                    {
                      "path": "/rows/0/total",
                      "value": "50.25"
                    },
                    {
                      "path": "/rows/0/volumes",
                      "value": 2
                    }
                  ]
                },
                {
                  "entity": "CAP",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 3,
                  "quarantined": 1,
                  "filtered": 0,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "SYNTHETIC:CAP:1"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/values/value/typed",
                      "value": "100.25000000"
                    },
                    {
                      "path": "/rows/0/values/paid/typed",
                      "value": "false"
                    }
                  ]
                },
                {
                  "entity": "Faturas",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 3,
                  "quarantined": 1,
                  "filtered": 0,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "SYNTHETIC:Faturas:1"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/values/total/typed",
                      "value": "150.25000000"
                    }
                  ]
                },
                {
                  "entity": "Inventario",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 3,
                  "quarantined": 1,
                  "filtered": 0,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "SYNTHETIC:Inventario:1"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/values/cnrCSFitInvoicesValue/typed",
                      "value": "25.75000000"
                    }
                  ]
                },
                {
                  "entity": "Sinistros",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 3,
                  "quarantined": 1,
                  "filtered": 0,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "SYNTHETIC:Sinistros:1"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/values/insuranceClaimTotal/typed",
                      "value": "550.25000000"
                    }
                  ]
                },
                {
                  "entity": "Raster",
                  "inputRows": 4,
                  "pages": 2,
                  "accepted": 3,
                  "quarantined": 1,
                  "filtered": 0,
                  "checks": [
                    {
                      "path": "/rows/0/presence",
                      "value": "ABSENT"
                    },
                    {
                      "path": "/rows/1/presence",
                      "value": "NULL"
                    },
                    {
                      "path": "/rows/2/presence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/sourceKey",
                      "value": "SYNTHETIC:Raster:1"
                    },
                    {
                      "path": "/rows/0/eligible",
                      "value": true
                    },
                    {
                      "path": "/rows/3/valid",
                      "value": false
                    },
                    {
                      "path": "/rows/0/stopPresence",
                      "value": "VALUE"
                    },
                    {
                      "path": "/rows/0/stops/0/latitude/typed",
                      "value": "-23.50000000"
                    }
                  ]
                }
              ],
              "freshnessChecks": [
                {
                  "path": "/insert",
                  "value": "INSERT"
                },
                {
                  "path": "/replay",
                  "value": "REPLAY_NO_OP"
                },
                {
                  "path": "/stale",
                  "value": "STALE_NO_OP"
                },
                {
                  "path": "/update",
                  "value": "UPDATE"
                },
                {
                  "path": "/conflict",
                  "value": "EQUAL_FRESHNESS_CONFLICT"
                }
              ],
              "relationBindingChecks": [
                {
                  "path": "/relationBindings",
                  "value": [
                    {
                      "relation": "MC",
                      "originKey": "INTEGER:2101",
                      "originComponent": "INTEGER:3101",
                      "targetKey": "INTEGER:3101",
                      "targetComponent": "ROOT",
                      "evidence": "synthetic-mc-fixture-v1",
                      "revision": 1,
                      "cardinality": "ONE_TO_ONE",
                      "date": "2026-10-01"
                    },
                    {
                      "relation": "CF",
                      "originKey": "INTEGER:3101",
                      "originComponent": "INTEGER:4101",
                      "targetKey": "INTEGER:4101",
                      "targetComponent": "INTEGER:3101",
                      "evidence": "synthetic-cf-fixture-v1",
                      "revision": 1,
                      "cardinality": "ONE_TO_ONE",
                      "date": "2026-10-01"
                    }
                  ]
                },
                {
                  "path": "/relationBindingObservations",
                  "value": [
                    {
                      "relation": "MC",
                      "evidence": "synthetic-mc-fixture-v1",
                      "identityScope": "SYNTHETIC_LOCAL_ONLY",
                      "originEntity": "Manifestos",
                      "originKey": "INTEGER:2101",
                      "originObservedRows": 1,
                      "originEligibleRows": 1,
                      "targetEntity": "Coletas",
                      "targetKey": "INTEGER:3101",
                      "targetObservedRows": 1,
                      "targetEligibleRows": 1,
                      "componentAuthority": "DECLARED_SYNTHETIC_BINDING",
                      "canonicalIdentityProven": false,
                      "status": "OBSERVED_SYNTHETIC_ROOTS"
                    },
                    {
                      "relation": "CF",
                      "evidence": "synthetic-cf-fixture-v1",
                      "identityScope": "SYNTHETIC_LOCAL_ONLY",
                      "originEntity": "Coletas",
                      "originKey": "INTEGER:3101",
                      "originObservedRows": 1,
                      "originEligibleRows": 1,
                      "targetEntity": "Fretes",
                      "targetKey": "INTEGER:4101",
                      "targetObservedRows": 1,
                      "targetEligibleRows": 1,
                      "componentAuthority": "DECLARED_SYNTHETIC_BINDING",
                      "canonicalIdentityProven": false,
                      "status": "OBSERVED_SYNTHETIC_ROOTS"
                    }
                  ]
                }
              ]
            }
            """;
}
