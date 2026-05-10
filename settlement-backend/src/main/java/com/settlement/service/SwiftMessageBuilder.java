package com.settlement.service;

import com.prowidesoftware.swift.model.mt.mt5xx.MT541;
import com.prowidesoftware.swift.model.field.*;
import com.settlement.entity.SettlementInstruction;
import com.settlement.swift.SwiftConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Builds SWIFT MT541 (Receive Against Payment) messages using Prowide Core.
 * MT541 is used for receiving securities instructions.
 */
@Component
public class SwiftMessageBuilder {

    private static final Logger log = LoggerFactory.getLogger(SwiftMessageBuilder.class);
    private static final DateTimeFormatter SWIFT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String buildMT541(SettlementInstruction instruction) {
        MT541 mt = new MT541();

        mt.setSender("OWNRBICXXX");
        mt.setReceiver(padBic(instruction.getBicCode()));

        // Sequence A - General Information
        mt.addField(new Field20C()
                .setQualifier(SwiftConst.SEME)
                .setReference(instruction.getTradeRef()));
        mt.addField(new Field23G(SwiftConst.NEWM));

        // Sequence B - Trade Details
        String settlementDateStr = instruction.getSettlementDate().format(SWIFT_DATE_FORMAT);
        mt.addField(new Field98A()
                .setQualifier(SwiftConst.SETT)
                .setDate(settlementDateStr));
        mt.addField(new Field35B("ISIN " + instruction.getIsin()));
        mt.addField(new Field36B()
                .setQualifier(SwiftConst.SETT)
                .setQuantityTypeCode(SwiftConst.UNIT)
                .setQuantity(instruction.getQuantity().toPlainString()));

        // Sequence E - Settlement Parties
        mt.addField(new Field95P()
                .setQualifier(SwiftConst.DEAG)
                .setIdentifierCode(padBic(instruction.getBicCode())));
        mt.addField(new Field97A()
                .setQualifier(SwiftConst.SAFE)
                .setAccountNumber(instruction.getAccountId()));

        String message = mt.message();
        log.debug("Built MT541 for tradeRef={}: length={}", instruction.getTradeRef(), message.length());
        return message;
    }

    private String padBic(String bic) {
        if (bic.length() == 8) {
            return bic + "XXX";
        }
        return bic;
    }
}
