/*
 * Copyright 2022 the original author or authors.
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
package de.odrotbohm.homeautomation.pvcharger.device.tesla;

import java.io.IOException;
import java.util.function.Function;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.client.RestOperations;

/**
 * @author Oliver Drotbohm
 */
@Component
class TeslaAuthentication implements ClientHttpRequestInterceptor {

	private final TokenResolver resolver;
	private final RestOperations operations;

	private String token;

	/**
	 * @param resolver
	 * @param operations
	 * @param token
	 */
	public TeslaAuthentication(TokenResolver resolver, RestTemplateBuilder templateBuilder) {

		this.resolver = resolver;
		this.operations = templateBuilder.additionalInterceptors(this).build();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.http.client.ClientHttpRequestInterceptor#intercept(org.springframework.http.HttpRequest, byte[], org.springframework.http.client.ClientHttpRequestExecution)
	 */
	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {

		if (token != null) {
			request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}

		return execution.execute(request, body);
	}

	public <T> T withAuthentication(Function<RestOperations, T> supplier) {

		if (token == null) {
			this.token = resolver.obtainToken();
		}

		try {

			return supplier.apply(operations);

		} catch (Unauthorized o_O) {

			this.token = null;
			return withAuthentication(supplier);
		}
	}

}
