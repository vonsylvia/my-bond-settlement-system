package com.settlement.service;

import com.prowidesoftware.swift.model.mt.mt5xx.MT541;
import com.prowidesoftware.swift.model.field.*;
import com.settlement.entity.SettlementInstruction;
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

        // Sender/Receiver BIC (simplified - in production these come from config)
        mt.setSender("OWNRBICXXX");
        mt.setReceiver(padBic(instruction.getBicCode()));

        // Sequence A - General Information
        // :20C: Sender's Reference
        mt.addField(new Field20C(":SEME//" + instruction.getTradeRef()));
        // :23G: Function of the Message (NEWM = New Message)
        mt.addField(new Field23G("NEWM"));

        // Sequence B - Trade Details
        // :98A: Settlement Date
        String settlementDateStr = instruction.getSettlementDate().format(SWIFT_DATE_FORMAT);
        mt.addField(new Field98A(":SETT//" + settlementDateStr));
        // :35B: ISIN identification
        mt.addField(new Field35B("ISIN " + instruction.getIsin()));
        // :36B: Quantity of securities
        mt.addField(new Field36B(":SETT//UNIT/" + instruction.getQuantity().toPlainString()));

        // Sequence E - Settlement Parties
        // :95P: Delivering/Receiving Agent
        mt.addField(new Field95P(":DEAG//" + padBic(instruction.getBicCode())));
        // :97A: Safekeeping Account
        mt.addField(new Field97A(":SAFE//" + instruction.getAccountId()));

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
