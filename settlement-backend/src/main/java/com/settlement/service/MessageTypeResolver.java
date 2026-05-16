package com.settlement.service;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import com.settlement.entity.MessageStandard;
import org.springframework.stereotype.Component;

/**
 * Resolves the correct SWIFT message type based on settlement direction
 * and payment type, covering the full MT540-543 / sese.023 family.
 *
 * <table>
 *   <tr><th>Direction</th><th>Payment</th><th>MT</th><th>MX</th></tr>
 *   <tr><td>RECEIVE</td><td>FREE</td><td>MT540</td><td>sese.023.001.09</td></tr>
 *   <tr><td>RECEIVE</td><td>AGAINST</td><td>MT541</td><td>sese.023.001.09</td></tr>
 *   <tr><td>DELIVER</td><td>FREE</td><td>MT542</td><td>sese.023.001.09</td></tr>
 *   <tr><td>DELIVER</td><td>AGAINST</td><td>MT543</td><td>sese.023.001.09</td></tr>
 * </table>
 */
@Component
public class MessageTypeResolver {

    public String resolveOutboundType(CanonicalSettlement settlement, MessageStandard standard) {
        if (standard == MessageStandard.MX) {
            return "sese.023.001.09";
        }

        return resolveMtType(settlement.direction(), settlement.paymentType());
    }

    public String resolveMtType(SettlementDirection direction, PaymentType paymentType) {
        if (direction == SettlementDirection.RECEIVE) {
            return (paymentType == PaymentType.FREE_OF_PAYMENT) ? "MT540" : "MT541";
        } else {
            return (paymentType == PaymentType.FREE_OF_PAYMENT) ? "MT542" : "MT543";
        }
    }

    public String resolveInboundStatusType(MessageStandard standard) {
        return (standard == MessageStandard.MX) ? "sese.024.001.10" : "MT548";
    }

    public String resolveConfirmationType(MessageStandard standard) {
        return (standard == MessageStandard.MX) ? "sese.025.001.09" : "MT544";
    }

    public String resolveHoldingsStatementType(MessageStandard standard) {
        return (standard == MessageStandard.MX) ? "semt.002.001.10" : "MT535";
    }

    public String resolveTransactionStatementType(MessageStandard standard) {
        return (standard == MessageStandard.MX) ? "semt.017.001.10" : "MT536";
    }

    public String resolveCorporateActionNotificationType(MessageStandard standard) {
        return (standard == MessageStandard.MX) ? "seev.031.001.11" : "MT564";
    }

    public String resolveCorporateActionConfirmationType(MessageStandard standard) {
        return (standard == MessageStandard.MX) ? "seev.036.001.11" : "MT566";
    }
}
