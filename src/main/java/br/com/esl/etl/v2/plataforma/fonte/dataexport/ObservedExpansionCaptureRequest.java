package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Request value/encoding only: no URL, credentials, transport execution or retry. */
public record ObservedExpansionCaptureRequest(ObservedExpansionCapturePlan plan, int page) {
    public ObservedExpansionCaptureRequest {
        Objects.requireNonNull(plan);
        if (page < 1 || page > plan.limits().maximumPages()) {
            throw new IllegalArgumentException("OBS_CAPTURE_PAGE");
        }
    }

    /** A fresh tree on every call; callers cannot mutate the frozen request. */
    public ObjectNode jsonBody() {
        final ObjectNode body = JsonNodeFactory.instance.objectNode();
        final ObjectNode search = body.putObject("search");
        final ObjectNode root = search.putObject(plan.template().filterRoot());
        final String window = plan.businessWindow().formatForSource(plan.sourceZone());
        root.put(plan.template().filterField(), window);
        if (plan.template() == ObservedExpansionCaptureTemplate.CAP) {
            root.put("created_at", window);
        }
        body.put("page", Integer.toString(page));
        body.put("per", Integer.toString(plan.per()));
        if (plan.orderBy().present()) {
            body.put("order_by", plan.orderBy().expressions().get(0));
        }
        return body;
    }

    @Override
    public String toString() {
        return "ObservedExpansionCaptureRequest[id="
                + plan.captureExecutionId()
                + ",template="
                + plan.template().templateId()
                + ",page="
                + page
                + "]";
    }
}
