package com.fiap.pharmacypopular.function;

import com.fiap.pharmacypopular.aplication.BatchRunResult;
import com.fiap.pharmacypopular.config.AppConfig;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

public class StockIngestionFunction {
    @FunctionName("pharmacy-stock-ingestion-batch")
    public void run(
        @TimerTrigger(name = "timerInfo", schedule = "%CRON_TIME%") String timerInfo,
        final ExecutionContext context
    ) {
        BatchRunResult result = AppConfig.useCase().execute();
        context.getLogger().info("Run finished: eligible=" + result.eligible()
                + ", processed=" + result.processed()
                + ", failed=" + result.failed()
                + ", duplicates=" + result.duplicates());
    }
}
