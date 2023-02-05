/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.odrotbohm.homeautomation.pvcharger.device;

import de.odrotbohm.homeautomation.pvcharger.pv.PowerState;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * An event listener for {@link PowerState} events.
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@Component
class PowerStateListener {

	private final ChargingDevice device;

	private ChargeState chargeState;

	/**
	 * Creates a new {@link PowerStateListener} using the given {@link ChargingDevice} and {@link ChargeSettings}.
	 *
	 * @param device must not be {@literal null}.
	 * @param settings must not be {@literal null}.
	 */
	public PowerStateListener(ChargingDevice device, ChargeSettings settings) {

		device.heartbeat();

		this.device = device;
		this.chargeState = ChargeState.forCurrent(device.getChargePower(), settings);

		log.info("Initial charge state: {}", chargeState);
	}

	@Async
	@EventListener
	void on(PowerState state) {

		this.chargeState = chargeState.transitionFor(state, device);

		log.info("Charge state: {}", chargeState);
	}
}
