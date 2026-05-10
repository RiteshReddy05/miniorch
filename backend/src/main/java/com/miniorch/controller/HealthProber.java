package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.persistence.Replica;

public interface HealthProber {

    ProbeType supportedType();

    ProbeOutcome probe(Replica replica, ProbeConfig config);
}
