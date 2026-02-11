package com.fiap.pharmacypopular.function;

import java.time.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

public class StockIngestionFunction {
    @FunctionName("pharmacy-stock-ingestion-batch")
    public void run(
        @TimerTrigger(name = "timerInfo", schedule = "%CRON_TIME%") String timerInfo,
        final ExecutionContext context
    ) {
        context.getLogger().info("Timer trigger function executed at: " + LocalDateTime.now());
    }
}
