package com.settlement.strategy;

import com.settlement.entity.MessageStandard;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the correct {@link SwiftMessageStrategy} based on the
 * requested {@link MessageStandard}. New standards only require
 * adding a new Strategy implementation — no factory changes needed.
 */
@Component
public class SwiftMessageStrategyFactory {

    private final Map<MessageStandard, SwiftMessageStrategy> strategies;

    public SwiftMessageStrategyFactory(List<SwiftMessageStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(SwiftMessageStrategy::getStandard, Function.identity()));
    }

    public SwiftMessageStrategy getStrategy(MessageStandard standard) {
        SwiftMessageStrategy strategy = strategies.get(standard);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for standard: " + standard);
        }
        return strategy;
    }

    /**
     * Detects the message standard from raw payload content.
     * MX messages are XML; MT messages use FIN tag syntax.
     */
    public SwiftMessageStrategy detectStrategy(String rawPayload) {
        if (rawPayload != null && rawPayload.stripLeading().startsWith("<")) {
            return getStrategy(MessageStandard.MX);
        }
        return getStrategy(MessageStandard.MT);
    }
}
