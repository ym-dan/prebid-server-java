package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;

public class BidderInfoCreator {

    private BidderInfoCreator() {

    }

    public static BidderInfo create(BidderConfigurationProperties configurationProperties) {
        return create(configurationProperties, null);
    }

    public static BidderInfo create(BidderConfigurationProperties configurationProperties, String aliasOf) {
        final MetaInfo metaInfo = configurationProperties.getMetaInfo();
        return BidderInfo.create(
                configurationProperties.getEnabled(),
                configurationProperties.getEndpoint(),
                aliasOf,
                metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(),
                metaInfo.getSiteMediaTypes(),
                metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(),
                configurationProperties.getPbsEnforcesCcpa(),
                configurationProperties.getModifyingVastXmlAllowed());
    }
}
