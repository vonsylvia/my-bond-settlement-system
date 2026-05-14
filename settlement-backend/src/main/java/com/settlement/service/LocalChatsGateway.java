package com.settlement.service;

import com.settlement.dao.CashAccountDao;
import com.settlement.dao.CashMovementDao;
import com.settlement.entity.CashAccount;
import com.settlement.entity.CashMovement;
import com.settlement.entity.MovementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Local implementation of the CHATS gateway that operates against
 * the internal CASH_ACCOUNT / CASH_MOVEMENT tables. Suitable for
 * development, testing, and as a reference for production CHATS integration.
 */
@Component
public class LocalChatsGateway implements ChatsGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalChatsGateway.class);

    private final CashAccountDao cashAccountDao;
    private final CashMovementDao cashMovementDao;

    public LocalChatsGateway(CashAccountDao cashAccountDao, CashMovementDao cashMovementDao) {
        this.cashAccountDao = cashAccountDao;
        this.cashMovementDao = cashMovementDao;
    }

    @Override
    @Transactional
    public boolean reserveFunds(String accountId, String currency, BigDecimal amount, String tradeRef) {
        Optional<CashAccount> opt = cashAccountDao.findByAccountAndCurrencyForUpdate(accountId, currency);
        if (opt.isEmpty()) {
            log.warn("Cash account not found: account={}, currency={}", accountId, currency);
            return false;
        }

        CashAccount account = opt.get();
        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient funds for reservation: account={}, currency={}, balance={}, requested={}",
                    accountId, currency, account.getBalance(), amount);
            return false;
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        cashAccountDao.save(account);

        cashMovementDao.save(new CashMovement(
                accountId, currency, MovementType.DEBIT, amount, newBalance, tradeRef));

        log.info("Funds reserved: account={}, currency={}, amount={}, tradeRef={}", accountId, currency, amount, tradeRef);
        return true;
    }

    @Override
    @Transactional
    public void releaseFunds(String accountId, String currency, BigDecimal amount, String tradeRef) {
        Optional<CashAccount> opt = cashAccountDao.findByAccountAndCurrencyForUpdate(accountId, currency);
        if (opt.isEmpty()) {
            log.warn("Cannot release funds — cash account not found: account={}, currency={}", accountId, currency);
            return;
        }

        CashAccount account = opt.get();
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        cashAccountDao.save(account);

        cashMovementDao.save(new CashMovement(
                accountId, currency, MovementType.CREDIT, amount, newBalance, tradeRef));

        log.info("Funds released: account={}, currency={}, amount={}, tradeRef={}", accountId, currency, amount, tradeRef);
    }

    @Override
    @Transactional
    public boolean transferFunds(String fromAccount, String toAccount,
                                 String currency, BigDecimal amount, String tradeRef) {
        Optional<CashAccount> fromOpt = cashAccountDao.findByAccountAndCurrencyForUpdate(fromAccount, currency);
        if (fromOpt.isEmpty() || fromOpt.get().getBalance().compareTo(amount) < 0) {
            log.warn("Transfer failed — insufficient funds: from={}, currency={}, amount={}", fromAccount, currency, amount);
            return false;
        }

        CashAccount from = fromOpt.get();
        BigDecimal fromNewBalance = from.getBalance().subtract(amount);
        from.setBalance(fromNewBalance);
        cashAccountDao.save(from);
        cashMovementDao.save(new CashMovement(fromAccount, currency, MovementType.DEBIT, amount, fromNewBalance, tradeRef));

        CashAccount to = cashAccountDao.findByAccountAndCurrencyForUpdate(toAccount, currency)
                .orElseGet(() -> {
                    CashAccount newAccount = new CashAccount(toAccount, currency);
                    return cashAccountDao.save(newAccount);
                });
        BigDecimal toNewBalance = to.getBalance().add(amount);
        to.setBalance(toNewBalance);
        cashAccountDao.save(to);
        cashMovementDao.save(new CashMovement(toAccount, currency, MovementType.CREDIT, amount, toNewBalance, tradeRef));

        log.info("Funds transferred: from={} to={}, currency={}, amount={}, tradeRef={}",
                fromAccount, toAccount, currency, amount, tradeRef);
        return true;
    }

    @Override
    public boolean hasSufficientFunds(String accountId, String currency, BigDecimal amount) {
        return cashAccountDao.findByAccountAndCurrency(accountId, currency)
                .map(a -> a.getBalance().compareTo(amount) >= 0)
                .orElse(false);
    }
}
