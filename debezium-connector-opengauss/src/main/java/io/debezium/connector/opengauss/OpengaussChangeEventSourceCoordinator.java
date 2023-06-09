/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.opengauss;

import java.sql.SQLException;

import org.apache.kafka.connect.source.SourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.DebeziumException;
import io.debezium.config.CommonConnectorConfig;
import io.debezium.connector.opengauss.spi.SlotState;
import io.debezium.connector.opengauss.spi.Snapshotter;
import io.debezium.pipeline.ChangeEventSourceCoordinator;
import io.debezium.pipeline.ErrorHandler;
import io.debezium.pipeline.EventDispatcher;
import io.debezium.pipeline.metrics.spi.ChangeEventSourceMetricsFactory;
import io.debezium.pipeline.source.spi.ChangeEventSource;
import io.debezium.pipeline.source.spi.ChangeEventSource.ChangeEventSourceContext;
import io.debezium.pipeline.source.spi.SnapshotChangeEventSource;
import io.debezium.pipeline.spi.Offsets;
import io.debezium.schema.DatabaseSchema;

/**
 * Coordinates one or more {@link ChangeEventSource}s and executes them in order. Extends the base
 * {@link ChangeEventSourceCoordinator} to support a pre-snapshot catch up streaming phase.
 */
public class OpengaussChangeEventSourceCoordinator extends ChangeEventSourceCoordinator<OpengaussPartition, OpengaussOffsetContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpengaussChangeEventSourceCoordinator.class);

    private final Snapshotter snapshotter;
    private final SlotState slotInfo;

    public OpengaussChangeEventSourceCoordinator(Offsets<OpengaussPartition, OpengaussOffsetContext> previousOffsets,
                                                 ErrorHandler errorHandler,
                                                 Class<? extends SourceConnector> connectorType,
                                                 CommonConnectorConfig connectorConfig,
                                                 OpengaussChangeEventSourceFactory changeEventSourceFactory,
                                                 ChangeEventSourceMetricsFactory changeEventSourceMetricsFactory,
                                                 EventDispatcher<?> eventDispatcher, DatabaseSchema<?> schema,
                                                 Snapshotter snapshotter, SlotState slotInfo) {
        super(previousOffsets, errorHandler, connectorType, connectorConfig, changeEventSourceFactory,
                changeEventSourceMetricsFactory, eventDispatcher, schema);
        this.snapshotter = snapshotter;
        this.slotInfo = slotInfo;
    }

    @Override
    protected CatchUpStreamingResult executeCatchUpStreaming(ChangeEventSourceContext context,
                                                             SnapshotChangeEventSource<OpengaussPartition, OpengaussOffsetContext> snapshotSource,
                                                             OpengaussPartition partition,
                                                             OpengaussOffsetContext previousOffset)
            throws InterruptedException {
        if (previousOffset != null && !snapshotter.shouldStreamEventsStartingFromSnapshot() && slotInfo != null) {
            try {
                setSnapshotStartLsn((OpengaussSnapshotChangeEventSource) snapshotSource,
                        previousOffset);
            }
            catch (SQLException e) {
                throw new DebeziumException("Failed to determine catch-up streaming stopping LSN");
            }
            LOGGER.info("Previous connector state exists and will stream events until {} then perform snapshot",
                    previousOffset.getStreamingStoppingLsn());
            streamEvents(context, partition, previousOffset);
            return new CatchUpStreamingResult(true);
        }

        return new CatchUpStreamingResult(false);
    }

    private void setSnapshotStartLsn(OpengaussSnapshotChangeEventSource snapshotSource,
                                     OpengaussOffsetContext offsetContext)
            throws SQLException {
        snapshotSource.createSnapshotConnection();
        snapshotSource.setSnapshotTransactionIsolationLevel();
        snapshotSource.updateOffsetForPreSnapshotCatchUpStreaming(offsetContext);
    }

}
