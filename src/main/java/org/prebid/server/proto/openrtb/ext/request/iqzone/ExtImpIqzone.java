package org.prebid.server.proto.openrtb.ext.request.iqzone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpIqzone {

    @JsonProperty("placementId")
    String placementId;
}
