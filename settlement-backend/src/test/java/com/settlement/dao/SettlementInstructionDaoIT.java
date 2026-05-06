package com.settlement.dao;

import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:spring/test-applicationContext.xml")
@Transactional
class SettlementInstructionDaoIT {

    @Autowired
    private SettlementInstructionDao instructionDao;

    @Test
    void save_shouldPersistInstruction() {
        SettlementInstruction instruction = createInstruction("TR-IT-001");

        SettlementInstruction saved = instructionDao.save(instruction);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTradeRef()).isEqualTo("TR-IT-001");
    }

    @Test
    void findByTradeRef_shouldReturnInstruction() {
        instructionDao.save(createInstruction("TR-IT-002"));

        Optional<SettlementInstruction> found = instructionDao.findByTradeRef("TR-IT-002");

        assertThat(found).isPresent();
        assertThat(found.get().getIsin()).isEqualTo("US0378331005");
    }

    @Test
    void findByTradeRef_shouldReturnEmpty_whenNotExists() {
        Optional<SettlementInstruction> found = instructionDao.findByTradeRef("TR-NONEXIST");

        assertThat(found).isEmpty();
    }

    @Test
    void findByStatus_shouldFilterCorrectly() {
        instructionDao.save(createInstruction("TR-S1"));
        SettlementInstruction sent = createInstruction("TR-S2");
        sent.setStatus(InstructionStatus.SENT);
        instructionDao.save(sent);

        List<SettlementInstruction> pending = instructionDao.findByStatus(InstructionStatus.PENDING);
        List<SettlementInstruction> sentList = instructionDao.findByStatus(InstructionStatus.SENT);

        assertThat(pending).hasSize(1);
        assertThat(sentList).hasSize(1);
        assertThat(sentList.get(0).getTradeRef()).isEqualTo("TR-S2");
    }

    @Test
    void findAll_shouldPaginate() {
        for (int i = 0; i < 5; i++) {
            instructionDao.save(createInstruction("TR-PAGE-" + i));
        }

        List<SettlementInstruction> page0 = instructionDao.findAll(0, 2);
        List<SettlementInstruction> page1 = instructionDao.findAll(1, 2);

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
    }

    @Test
    void count_shouldReturnTotalCount() {
        instructionDao.save(createInstruction("TR-C1"));
        instructionDao.save(createInstruction("TR-C2"));

        long count = instructionDao.count();

        assertThat(count).isEqualTo(2);
    }

    private SettlementInstruction createInstruction(String tradeRef) {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef(tradeRef);
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setAccountId("ACC-001");
        instruction.setStatus(InstructionStatus.PENDING);
        return instruction;
    }
}
