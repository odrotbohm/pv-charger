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

import lombok.Value;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * @author Oliver Drotbohm
 */
@Value
@ConfigurationProperties("pv")
class PvProperties {

	private static final String ENDPOINT_URI = "https://%s/dyn/getDashValues.json";

	int pollingFrequency;
	URI endpoint;

	/**
	 * Creates a new {@link PvProperties} for the given polling frequency and inverter ip.
	 *
	 * @param pollingFrequency defaults to 30 seconds if 0 is given.
	 * @param ip must not be {@literal null} or empty.
	 */
	PvProperties(int pollingFrequency, String ip) {

		Assert.hasText(ip, "IP must not be null or empty!");

		this.pollingFrequency = pollingFrequency == 0 ? 30 : pollingFrequency;
		this.endpoint = URI.create(ENDPOINT_URI.formatted(ip));
	}
}
