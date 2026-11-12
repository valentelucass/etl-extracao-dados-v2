CREATE TABLE ref.dashboard_frete_regras_documento (
    rule_type varchar(8) NOT NULL CHECK (rule_type IN ('BRANCH','CUBAGE')),
    document_hash binary(32) NOT NULL,
    CONSTRAINT pk_dashboard_frete_regras_documento PRIMARY KEY (rule_type,document_hash)
);
