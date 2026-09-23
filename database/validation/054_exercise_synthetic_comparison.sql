-- CMP-01/03: two independent literal bags and a separately written aggregate oracle.
-- Synthetic only. No business IDs or row payloads leave SQL. Six fixture reservations required.
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52501,N'LOCAL_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
DECLARE @left TABLE(scenario VARCHAR(20),synthetic_key INT,amount DECIMAL(19,4),status VARCHAR(20),business_date DATE,complete BIT);
DECLARE @right TABLE(scenario VARCHAR(20),synthetic_key INT,amount DECIMAL(19,4),status VARCHAR(20),business_date DATE,complete BIT);
INSERT @left VALUES
 ('EQUAL',1,10.0000,'OPEN','2024-01-01',1),
 ('MONEY_STATUS',1,10.0000,'OPEN','2024-01-01',1),
 ('DUPLICATE',1,10.0000,'OPEN','2024-01-01',1),('DUPLICATE',1,10.0000,'OPEN','2024-01-01',1),
 ('ABSENT',1,10.0000,'OPEN','2024-01-01',1),
 ('BOUNDARY',1,10.0000,'OPEN','2023-12-31',1),
 ('INCOMPLETE',1,10.0000,'OPEN','2024-01-01',0);
INSERT @right VALUES
 ('EQUAL',1,10.0000,'OPEN','2024-01-01',1),
 ('MONEY_STATUS',1,12.0000,'CLOSED','2024-01-01',1),
 ('DUPLICATE',1,10.0000,'OPEN','2024-01-01',1),
 ('BOUNDARY',1,10.0000,'OPEN','2024-01-01',1),
 ('INCOMPLETE',1,10.0000,'OPEN','2024-01-01',1);
DECLARE @actual TABLE(scenario VARCHAR(20),missing_left INT,missing_right INT,duplicates INT,money_differences INT,status_differences INT,incomplete INT);
;WITH l AS(SELECT scenario,synthetic_key,COUNT(*) n,SUM(amount) amount,MIN(status) status,MIN(CONVERT(INT,complete)) complete
 FROM @left WHERE business_date>='2024-01-01' AND business_date<'2024-01-02' GROUP BY scenario,synthetic_key),
 r AS(SELECT scenario,synthetic_key,COUNT(*) n,SUM(amount) amount,MIN(status) status,MIN(CONVERT(INT,complete)) complete
 FROM @right WHERE business_date>='2024-01-01' AND business_date<'2024-01-02' GROUP BY scenario,synthetic_key)
INSERT @actual
 SELECT COALESCE(l.scenario,r.scenario),SUM(CASE WHEN l.n IS NULL THEN 1 ELSE 0 END),
 SUM(CASE WHEN r.n IS NULL THEN 1 ELSE 0 END),SUM(COALESCE(l.n-1,0)+COALESCE(r.n-1,0)),
 SUM(CASE WHEN l.n=1 AND r.n=1 AND l.complete=1 AND r.complete=1 AND l.amount<>r.amount THEN 1 ELSE 0 END),
 SUM(CASE WHEN l.n=1 AND r.n=1 AND l.complete=1 AND r.complete=1 AND l.status<>r.status THEN 1 ELSE 0 END),
 SUM(CASE WHEN l.complete=0 OR r.complete=0 THEN 1 ELSE 0 END)
 FROM l FULL JOIN r ON l.scenario=r.scenario AND l.synthetic_key=r.synthetic_key GROUP BY COALESCE(l.scenario,r.scenario);
DECLARE @oracle TABLE(scenario VARCHAR(20),missing_left INT,missing_right INT,duplicates INT,money_differences INT,status_differences INT,incomplete INT);
INSERT @oracle VALUES('EQUAL',0,0,0,0,0,0),('MONEY_STATUS',0,0,0,1,1,0),('DUPLICATE',0,0,1,0,0,0),
 ('ABSENT',0,1,0,0,0,0),('BOUNDARY',1,0,0,0,0,0),('INCOMPLETE',0,0,0,0,0,1);
IF EXISTS(SELECT * FROM @actual EXCEPT SELECT * FROM @oracle) OR EXISTS(SELECT * FROM @oracle EXCEPT SELECT * FROM @actual)
 THROW 52501,N'SYNTHETIC_COMPARISON_ORACLE_DIVERGED',1;
SELECT scenario,missing_left,missing_right,duplicates,money_differences,status_differences,incomplete FROM @actual ORDER BY scenario;
ROLLBACK TRANSACTION;
PRINT N'CMP_SYNTHETIC_SIX_CASES_PASS_REAL_PARITY_NOT_TESTED';
