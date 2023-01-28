/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.opengauss;

import io.debezium.connector.opengauss.snapshot.AlwaysSnapshotter;

public class CustomStartFromStreamingTestSnapshot extends AlwaysSnapshotter {
    @Override
    public boolean shouldStreamEventsStartingFromSnapshot() {
        return false;
    }
}
