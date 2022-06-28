/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.oracle.logminer;

import org.apache.kafka.connect.data.Struct;

import io.debezium.DebeziumException;
import io.debezium.connector.oracle.BaseChangeRecordEmitter;
import io.debezium.connector.oracle.logminer.events.EventType;
import io.debezium.data.Envelope.Operation;
import io.debezium.pipeline.spi.OffsetContext;
import io.debezium.pipeline.spi.Partition;
import io.debezium.relational.Table;
import io.debezium.relational.TableSchema;
import io.debezium.schema.DataCollectionSchema;
import io.debezium.util.Clock;

/**
 * Emits change records based on an event read from Oracle LogMiner.
 */
public class LogMinerChangeRecordEmitter extends BaseChangeRecordEmitter<Object> {

    private final Operation operation;
    private final Object[] oldValues;
    private final Object[] newValues;

    public LogMinerChangeRecordEmitter(Partition partition, OffsetContext offset, Operation operation, Object[] oldValues,
                                       Object[] newValues, Table table, Clock clock) {
        super(partition, offset, table, clock);
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.operation = operation;
    }

    public LogMinerChangeRecordEmitter(Partition partition, OffsetContext offset, EventType eventType, Object[] oldValues,
                                       Object[] newValues, Table table, Clock clock) {
        this(partition, offset, getOperation(eventType), oldValues, newValues, table, clock);
    }

    private static Operation getOperation(EventType eventType) {
        switch (eventType) {
            case INSERT:
                return Operation.CREATE;
            case UPDATE:
            case SELECT_LOB_LOCATOR:
                return Operation.UPDATE;
            case DELETE:
                return Operation.DELETE;
            default:
                throw new DebeziumException("Unsupported operation type: " + eventType);
        }
    }

    @Override
    protected Operation getOperation() {
        return operation;
    }

    @Override
    protected Object[] getOldColumnValues() {
        return oldValues;
    }

    @Override
    protected Object[] getNewColumnValues() {
        return newValues;
    }

    @Override
    public void emitChangeRecords(DataCollectionSchema schema, Receiver receiver) throws InterruptedException {
        TableSchema tableSchema = (TableSchema) schema;
        Operation operation = getOperation();
        if (operation == Operation.TRUNCATE_CASCADE) {
            emitTruncateCascadeRecord(receiver, tableSchema);
        }
        else {
            super.emitChangeRecords(schema, receiver);
        }
    }

    protected void emitTruncateCascadeRecord(Receiver receiver, TableSchema tableSchema) throws InterruptedException {
        Struct envelope = tableSchema.getEnvelopeSchema().truncateCascade(getOffset().getSourceInfo(), getClock().currentTimeAsInstant());
        receiver.changeRecord(getPartition(), tableSchema, Operation.TRUNCATE_CASCADE, null, envelope, getOffset(), null);
    }
}
