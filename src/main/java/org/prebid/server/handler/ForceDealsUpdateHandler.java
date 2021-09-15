package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.deals.DeliveryStatsService;
import org.prebid.server.deals.PlannerService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

public class ForceDealsUpdateHandler implements Handler<RoutingContext> {

    private static final String ACTION_NAME_PARAM = "action_name";

    private final DeliveryStatsService deliveryStatsService;
    private final PlannerService plannerService;
    private final String endpoint;

    public ForceDealsUpdateHandler(DeliveryStatsService deliveryStatsService,
                                   PlannerService plannerService,
                                   String endpoint) {
        this.deliveryStatsService = Objects.requireNonNull(deliveryStatsService);
        this.plannerService = Objects.requireNonNull(plannerService);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Action dealsAction;
        try {
            dealsAction = dealsActionFrom(routingContext);
        } catch (IllegalArgumentException e) {
            respondWithError(routingContext, HttpResponseStatus.BAD_REQUEST, e);
            return;
        }

        try {
            switch (dealsAction) {
                case UPDATE_LINE_ITEMS:
                    plannerService.updateLineItemMetaData();
                    break;
                case SEND_REPORT:
                    deliveryStatsService.sendDeliveryProgressReports();
                    break;
                default:
                    throw new IllegalStateException("Unexpected action value");
            }
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                            .end());
        } catch (PreBidException e) {
            respondWithError(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private Action dealsActionFrom(RoutingContext routingContext) {
        final String givenActionValue = routingContext.request().getParam(ACTION_NAME_PARAM);
        if (StringUtils.isEmpty(givenActionValue)) {
            throw new IllegalArgumentException(
                    String.format("Parameter '%s' is required and can't be empty", ACTION_NAME_PARAM));
        }

        final String[] possibleActions = Arrays.stream(Action.values()).map(Action::name).toArray(String[]::new);
        if (Arrays.stream(possibleActions).noneMatch(Predicate.isEqual(givenActionValue.toUpperCase()))) {
            throw new IllegalArgumentException(
                    String.format("Given '%s' parameter value is not among possible actions '%s'",
                            ACTION_NAME_PARAM, Arrays.toString(possibleActions)));
        }

        return Action.valueOf(givenActionValue.toUpperCase());
    }

    private void respondWithError(RoutingContext routingContext, HttpResponseStatus statusCode, Exception exception) {
        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .setStatusCode(statusCode.code())
                        .end(exception.getMessage()));
    }

    enum Action {
        UPDATE_LINE_ITEMS, SEND_REPORT
    }
}
