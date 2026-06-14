package com.foundflow.matching;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.DiagnosticResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class MatchingServiceApplicationTests {

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@TestConfiguration
	static class GenaiClientStub {
		@Bean
		GenaiClient genaiClient() {
			GenaiClient client = mock(GenaiClient.class);
			DiagnosticResponse r = new DiagnosticResponse();
			r.setProvider(DiagnosticResponse.ProviderEnum.LOCAL);
			r.setChatOk(true);
			r.setEmbedOk(true);
			r.setChatLatencyMs(0);
			r.setEmbedLatencyMs(0);
			r.setChatModel("x");
			r.setEmbedModel("y");
			r.setEmbedDimensionsConfigured(768);
			r.setEmbedDimensionsActual(768);
			when(client.diagnostic()).thenReturn(r);
			return client;
		}
	}

	@Test
	void contextLoads() {
	}

}
