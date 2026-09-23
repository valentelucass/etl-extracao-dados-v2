-- EXP-24: all six contracted arrays share the same bounded positional storage.
-- Additive correction of V038; existing rows satisfy the expanded closed list.
ALTER TABLE stg.expansion_lab_array_item DROP CONSTRAINT CK_expansion_array_bound;
GO
ALTER TABLE stg.expansion_lab_array_item WITH CHECK ADD CONSTRAINT CK_expansion_array_bound
 CHECK(physical_position BETWEEN 0 AND 31 AND field_name IN(
 'invoices_mapping','fit_fte_invoices_order_number','cnr_c_s_fit_invoices_mapping',
 'rcfdc','rctac','rctrc'));
GO
