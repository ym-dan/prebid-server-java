package org.prebid.server.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.DeliveryStatsService;
import org.prebid.server.deals.PlannerService;
import org.prebid.server.exception.PreBidException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class ForceDealsUpdateHandlerTest {

    private static final String ACTION_NAME_PARAM = "action_name";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private DeliveryStatsService deliveryStatsService;
    @Mock
    private PlannerService plannerService;

    private ForceDealsUpdateHandler handler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        handler = new ForceDealsUpdateHandler(deliveryStatsService, plannerService, "/endpoint");

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.closed()).willReturn(false);
    }

    @Test
    public void shouldReturnBadRequestWhenActionParamIsMissing() {
        // given
        given(httpRequest.getParam(any())).willReturn(null);

        // when
        handler.handle(routingContext);

        //  then
        verify(httpRequest).getParam(eq(ACTION_NAME_PARAM));

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq(String.format("Parameter '%s' is required and can't be empty", ACTION_NAME_PARAM)));
    }

    @Test
    public void shouldReturnBadRequestWhenBadActionParamIsGiven() {
        // given
        given(httpRequest.getParam(any())).willReturn("bad_param_name");

        // when
        handler.handle(routingContext);

        //  then
        verify(httpRequest).getParam(eq(ACTION_NAME_PARAM));

        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq(String.format("Given '%s' parameter value is not among possible actions "
                + "'[UPDATE_LINE_ITEMS, SEND_REPORT]'", ACTION_NAME_PARAM)));
    }

    @Test
    public void shouldCallLineItemsUpdateMethodWhenUpdateLineItemsParamIsGiven() {
        // given
        given(httpRequest.getParam(any())).willReturn(ForceDealsUpdateHandler.Action.UPDATE_LINE_ITEMS.name());

        // when
        handler.handle(routingContext);

        // then
        verify(plannerService, times(1)).updateLineItemMetaData();
        verifyZeroInteractions(deliveryStatsService);

        verify(httpResponse).setStatusCode(eq(204));
    }

    @Test
    public void shouldCallReportSendingMethodWhenSendReportParamIsGiven() {
        // given
        given(httpRequest.getParam(any())).willReturn(ForceDealsUpdateHandler.Action.SEND_REPORT.name());

        // when
        handler.handle(routingContext);

        // then
        verify(deliveryStatsService, times(1)).sendDeliveryProgressReports();
        verifyZeroInteractions(plannerService);

        verify(httpResponse).setStatusCode(eq(204));
    }

    @Test
    public void shouldReturnInternalServerExceptionWhenUpdatingLineItemActionFailed() {
        // given
        given(httpRequest.getParam(any())).willReturn(ForceDealsUpdateHandler.Action.UPDATE_LINE_ITEMS.name());
        final String exceptionMessage = "Failed to fetch data from Planner";
        doThrow(new PreBidException(exceptionMessage)).when(plannerService).updateLineItemMetaData();

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq(exceptionMessage));
    }

    @Test
    public void shouldReturnInternalServerExceptionWhenSendingReportActionFailed() {
        // given
        given(httpRequest.getParam(any())).willReturn(ForceDealsUpdateHandler.Action.SEND_REPORT.name());
        final String exceptionMessage = "Sending report failed";
        doThrow(new PreBidException(exceptionMessage)).when(deliveryStatsService).sendDeliveryProgressReports();

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq(exceptionMessage));
    }
}
