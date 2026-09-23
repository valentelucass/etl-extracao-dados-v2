using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.SqlServer.TransactSql.ScriptDom;

// Offline evaluation of the adopted observer's AST predicates, not execution by SQL Server.
public sealed class RuntimeObserverContract : TSqlFragmentVisitor
{
    private BooleanExpression loop;
    private BooleanExpression resourceJoin;
    private int loops;
    private int joins;
    private int waits;
    private double delayMilliseconds;

    public override void ExplicitVisit(WaitForStatement node)
    {
        waits++;
        var delay = node.Parameter as StringLiteral;
        if (node.WaitForOption != WaitForOption.Delay || delay == null)
            throw new InvalidOperationException("B61_OBSERVER_DELAY");
        delayMilliseconds = TimeSpan.Parse(delay.Value, System.Globalization.CultureInfo.InvariantCulture).TotalMilliseconds;
        base.ExplicitVisit(node);
    }

    public override void ExplicitVisit(WhileStatement node)
    {
        loops++;
        loop = node.Predicate;
        base.ExplicitVisit(node);
    }

    public override void ExplicitVisit(QualifiedJoin node)
    {
        var right = node.SecondTableReference as NamedTableReference;
        if (right != null && right.Alias != null && right.Alias.Value == "b"
            && right.SchemaObject.BaseIdentifier.Value == "dm_tran_locks")
        {
            joins++;
            resourceJoin = node.SearchCondition;
        }
        base.ExplicitVisit(node);
    }

    private static object Scalar(ScalarExpression expression, IDictionary<string, object> values)
    {
        var variable = expression as VariableReference;
        if (variable != null) return values[variable.Name];
        var number = expression as IntegerLiteral;
        if (number != null) return int.Parse(number.Value, System.Globalization.CultureInfo.InvariantCulture);
        var column = expression as ColumnReferenceExpression;
        if (column != null) return values[string.Join(".", column.MultiPartIdentifier.Identifiers.Select(i => i.Value))];
        throw new InvalidOperationException("B61_OBSERVER_UNSUPPORTED_SCALAR");
    }

    private static object[] Row(QueryExpression expression, IDictionary<string, object> values)
    {
        var query = expression as QuerySpecification;
        if (query == null || query.FromClause != null || query.WhereClause != null)
            throw new InvalidOperationException("B61_OBSERVER_UNSUPPORTED_ROW");
        return query.SelectElements.Cast<SelectScalarExpression>().Select(e => Scalar(e.Expression, values)).ToArray();
    }

    private static bool Evaluate(BooleanExpression expression, IDictionary<string, object> values)
    {
        var parens = expression as BooleanParenthesisExpression;
        if (parens != null) return Evaluate(parens.Expression, values);
        var binary = expression as BooleanBinaryExpression;
        if (binary != null)
        {
            if (binary.BinaryExpressionType == BooleanBinaryExpressionType.And)
                return Evaluate(binary.FirstExpression, values) && Evaluate(binary.SecondExpression, values);
            if (binary.BinaryExpressionType == BooleanBinaryExpressionType.Or)
                return Evaluate(binary.FirstExpression, values) || Evaluate(binary.SecondExpression, values);
        }
        var compare = expression as BooleanComparisonExpression;
        if (compare != null)
        {
            object left = Scalar(compare.FirstExpression, values), right = Scalar(compare.SecondExpression, values);
            // SQL WHERE does not admit UNKNOWN from scalar equality with NULL.
            if (left == null || right == null) return false;
            if (compare.ComparisonType == BooleanComparisonType.Equals) return object.Equals(left, right);
            if (compare.ComparisonType == BooleanComparisonType.LessThan) return Convert.ToInt32(left) < Convert.ToInt32(right);
            if (compare.ComparisonType == BooleanComparisonType.LessThanOrEqualTo) return Convert.ToInt32(left) <= Convert.ToInt32(right);
        }
        var exists = expression as ExistsPredicate;
        if (exists != null)
        {
            var query = exists.Subquery.QueryExpression as BinaryQueryExpression;
            if (query == null || query.BinaryQueryExpressionType != BinaryQueryExpressionType.Intersect)
                throw new InvalidOperationException("B61_OBSERVER_NULL_SET_OPERATOR");
            return Row(query.FirstQueryExpression, values).SequenceEqual(Row(query.SecondQueryExpression, values));
        }
        throw new InvalidOperationException("B61_OBSERVER_UNSUPPORTED_PREDICATE");
    }

    public static void Check(string sql)
    {
        IList<ParseError> errors;
        TSqlFragment fragment;
        using (var reader = new StringReader(sql)) fragment = new TSql160Parser(true).Parse(reader, out errors);
        if (errors.Count != 0) throw new InvalidOperationException("B61_OBSERVER_PARSE");
        var visitor = new RuntimeObserverContract();
        fragment.Accept(visitor);
        if (visitor.loops != 1 || visitor.joins != 1) throw new InvalidOperationException("B61_OBSERVER_SHAPE");
        if (visitor.waits != 1 || visitor.delayMilliseconds != 250) throw new InvalidOperationException("B61_OBSERVER_DELAY");
        foreach (int tries in new[] { 0, 1, 2, 20, 27, 28 })
        foreach (int found in new[] { 0, 1, 2 })
        {
            var values = new Dictionary<string, object> { { "@tries", tries }, { "@found", found } };
            if (Evaluate(visitor.loop, values) != (tries < 28 && found < 2))
                throw new InvalidOperationException("B61_OBSERVER_EARLY_EXIT_OR_BOUND");
        }
        var columns = new[] { "resource_type", "resource_subtype", "resource_database_id",
            "resource_description", "resource_associated_entity_id", "resource_lock_partition" };
        var baseline = new object[] { "APPLICATION", null, 7, "SYNTHETIC_RESOURCE", 0, null };
        var resources = new Dictionary<string, object>();
        for (int i = 0; i < columns.Length; i++)
        {
            resources.Add("b." + columns[i], baseline[i]);
            resources.Add("w." + columns[i], baseline[i]);
        }
        if (!Evaluate(visitor.resourceJoin, resources)) throw new InvalidOperationException("B61_OBSERVER_NULL_EQUALITY");
        for (int i = 0; i < columns.Length; i++)
        {
            resources["w." + columns[i]] = "SYNTHETIC_DIFFERENT";
            if (Evaluate(visitor.resourceJoin, resources)) throw new InvalidOperationException("B61_OBSERVER_RESOURCE_MISMATCH");
            resources["w." + columns[i]] = baseline[i];
        }
    }
}
