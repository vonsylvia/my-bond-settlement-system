package com.settlement.strategy;

import com.settlement.canonical.*;
import com.settlement.entity.Direction;
import com.settlement.entity.SettlementInstruction;
import org.springframework.stereotype.Component;

/**
 * Maps between JPA business entities and format-independent Canonical models.
 * This is the only place that knows about both worlds — Strategy implementations
 * only see Canonical, and Service/DAO layers only see JPA entities.
 */
@Component
public class CanonicalMapper {

    private static final String SENDER_BIC = "OWNRBICXXX";

    public CanonicalSettlement toCanonical(SettlementInstruction instruction) {
        SettlementDirection direction = (instruction.getDirection() == Direction.BUY)
                ? SettlementDirection.RECEIVE
                : SettlementDirection.DELIVER;

        return new CanonicalSettlement(
                instruction.getTradeRef(),
                instruction.getIsin(),
                instruction.getSettlementDate(),
                instruction.getQuantity(),
                direction,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic(SENDER_BIC),
                PartyInfo.ofBic(instruction.getBicCode()),
                instruction.getAccountId(),
                null,
                null,
                null
        );
    }
}
