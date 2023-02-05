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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * A {@link TokenResolver} that scrapes the Teslafi website for the token they use.
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@Component
class TeslafiTokenResolver implements TokenResolver {

	private static final String TOKEN_XPATH = "//*[@id=\"contentNoSidebar\"]/div[3]/div[1]/span";

	private final String username, password;

	private Map<String, String> cookies;

	public TeslafiTokenResolver(
			@Value("${auth.teslafi.username}") String username,
			@Value("${auth.teslafi.password}") String password) {

		this.username = username;
		this.password = password;
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.device.tesla.TokenResolver#obtainToken()
	 */
	@Override
	@SneakyThrows
	public String obtainToken() {

		if (cookies == null) {
			autheticateWithTeslafi();
		}

		log.info("Obtaining Tesla login token from Teslafi…");

		var response = Jsoup.connect("https://teslafi.com/tokenNew.php")
				.referrer("https://teslafi.com/index.php")
				.userAgent("Mozilla/5.0")
				.cookies(cookies)
				.execute();

		// Reset auth and re-attempt
		if (response.statusCode() == 302) {

			log.info("Received redirect to " + response.header("Location"));

			this.cookies = null;
			return obtainToken();
		}

		var token = Jsoup.parse(response.body())
				.selectXpath(TOKEN_XPATH)
				.text();

		if (token.isBlank()) {

			log.info(
					"Could not obtain token from Teslafi token page (" + TOKEN_XPATH + ")! Response was:\n" + response.body());

			this.cookies = null;
			return obtainToken();
		}

		log.info("Obtained token ending in …{}.", token.substring(token.length() - 15));

		return token;
	}

	@SneakyThrows
	private void autheticateWithTeslafi() {

		log.info("Authenticating with Teslafi…");

		// Access login page
		var loginForm = Jsoup.connect("https://teslafi.com/userlogin.php").execute();

		// Extract server token
		var loginToken = Jsoup.parse(loginForm.body())
				.select("input[name=\"token\"]")
				.val();

		this.cookies = loginForm.cookies();

		// Submit login
		var payload = Map.of("username", username,
				"password", password,
				"token", loginToken,
				"submit", "Login");

		Response response = Jsoup.connect("https://teslafi.com/userlogin.php")
				.cookies(cookies)
				.header("Origin", "https://teslafi.com")
				.referrer("https://teslafi.com/userlogin.php")
				.method(Method.POST)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.userAgent("Mozilla/5.0")
				.data(payload)
				.followRedirects(false)
				.execute();

		Assert.isTrue(response.statusCode() == 302,
				() -> String.format("Expected redirect after login but got status %s!", response.statusCode()));

		log.info("Received redirect to " + response.header("Location"));
	}
}
