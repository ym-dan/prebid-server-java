package org.prebid.server.bidder.improvedigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExt;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExtImprovedigital;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.improvedigital.ExtImpImprovedigital;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ImprovedigitalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ImprovedigitalBidder improvedigitalBidder;

    @Before
    public void setUp() {
        improvedigitalBidder = new ImprovedigitalBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImprovedigitalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("123")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpImprovedigital.of(1234))))
                                .build(),
                        Imp.builder()
                                .id("456")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpImprovedigital.of(1234))))
                                .build()
                ))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = improvedigitalBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123", "456");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = improvedigitalBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize instance");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnMissingPlacementId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = improvedigitalBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No placementId provided"));
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoErrorsOnTwoErrorEvents() {
        // given
        final Imp imp = Imp.builder().ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode()))).build();
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp, imp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = improvedigitalBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatBidsCountIsMoreThanOne() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(asList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build()
                )).build()));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Unexpected SeatBid! Must be only one but have: 2"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBannerOrVideoNotPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unknown impression type for ID: \"123\""));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidIfBidExtImprovedigitalIsNull() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(null);

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .ext(mapper.valueToTree(bidExt)).build(),
                        banner,
                        "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().xNative(Native.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpNotFoundForId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("456"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Failed to find impression for ID: \"456\""));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldNotPopulateDealIdWhenLineItemIsMissing() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .buyingType("classic")
                        .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldPopulateDealIdForCampaign() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .lineItemId(2222222)
                        .buyingType("classic")
                        .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .dealid("2222222")
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldPopulateDealIdForDeal() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .lineItemId(2222222)
                        .buyingType("deal")
                        .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .dealid("2222222")
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldNotPopulateDealIdForRtb() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .lineItemId(2222222)
                        .buyingType("rtb")
                        .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
