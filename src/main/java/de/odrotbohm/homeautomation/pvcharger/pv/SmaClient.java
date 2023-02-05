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
package de.odrotbohm.homeautomation.pvcharger.pv;

import de.odrotbohm.homeautomation.pvcharger.Power;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * An HTTP client connecting to a SMA inverter available at {@link PvProperties#getIp()} (local network). Extract
 * current solar power, power used from the grid and power fed into the grid into {@link PowerState}s and publishes them
 * on the internal event bus. The polling frequency can be customized via {@link PvProperties#getPollingFrequency()}.
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@Component
class SmaClient {

	private static final String DATA_TEMPLATE = "$.result.0198-B3344918.%s.1[0].val";

	private static final JsonPath CURRENT_POWER_PATH = JsonPath.compile(DATA_TEMPLATE.formatted("6100_40263F00"));
	private static final JsonPath EXTERNAL_POWER_PATH = JsonPath.compile(DATA_TEMPLATE.formatted("6100_40463700"));
	private static final JsonPath FEED_IN_POWER_PATH = JsonPath.compile(DATA_TEMPLATE.formatted("6100_40463600"));

	private final RestOperations client;
	private final ApplicationEventPublisher publisher;
	private final URI server;

	/**
	 * Creates a new {@link SmaClient} for the given {@link ApplicationEventPublisher} and {@link PvProperties}.
	 *
	 * @param publisher must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	SmaClient(ApplicationEventPublisher publisher, PvProperties properties) {

		Assert.notNull(publisher, "ApplicationEventPublisher must not be null!");
		Assert.notNull(properties, "PvProperties must not be null!");

		this.client = new RestTemplate();
		this.publisher = publisher;
		this.server = properties.getEndpoint();

		log.info("Polling SMA inverter every {} seconds.", properties.getPollingFrequency());
	}

	@Scheduled(fixedRateString = "${pv.polling-frequency:30}", timeUnit = TimeUnit.SECONDS)
	void lookupPower() {

		var response = client.getForObject(server, String.class);

		log.debug("PV data lookup returned: {}", response);

		DocumentContext context = JsonPath.parse(response);

		var solarPower = Power.ofWatt(context.read(CURRENT_POWER_PATH, Integer.class));
		var externalPower = Power.ofWatt(context.read(EXTERNAL_POWER_PATH, Integer.class));
		var feedInPower = Power.ofWatt(context.read(FEED_IN_POWER_PATH, Integer.class));

		var base = PowerState.forSolarPower(solarPower);
		var state = Power.NONE.equals(externalPower)
				? base.feedingIn(feedInPower)
				: base.consuming(externalPower);

		log.info("Current power: {}", state);

		publisher.publishEvent(state);
	}
}
